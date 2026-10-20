"""Turn raw frame images (screenshots, Pinterest saves, JPGs) into app-ready image frames.

For each entry in frames.config.json:
  1. load the source, optionally deskew it, crop to the strip;
  2. upscale to the target width (Lanczos, or a pre-upscaled image from --upscaled-dir);
  3. chroma-key the photo windows to transparency (soft edges + colour decontamination);
  4. find the window rects (= photo slots), ordered row by row;
  5. write app/src/main/assets/frames/<id>/frame.png + thumb.png and assets/frames/frames.json;
  6. write review previews to photo-frames/_previews/.

See README §5.5. Requirements: pillow, numpy, scipy.
Usage: python tools/frames/process_frames.py [--upscaled-dir DIR] [--only ID]
"""

from __future__ import annotations

import argparse
import glob
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
CONFIG = Path(__file__).with_name("frames.config.json")
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "frames"
PREVIEWS = ROOT / "photo-frames" / "_previews"


def load_source(cfg: dict) -> Image.Image:
    matches = sorted(glob.glob(str(ROOT / cfg["source"])))
    if len(matches) != 1:
        raise SystemExit(f"{cfg['id']}: source pattern matched {len(matches)} files: {cfg['source']}")
    im = Image.open(matches[0]).convert("RGB")
    if cfg.get("rotate"):
        fill = tuple(im.getpixel((2, 2)))
        im = im.rotate(cfg["rotate"], resample=Image.BICUBIC, expand=False, fillcolor=fill)
    return im.crop(tuple(cfg["crop"]))


def upscale(im: Image.Image, cfg: dict, upscaled_dir: Path | None) -> Image.Image:
    target_w = cfg.get("width", 1200)
    if upscaled_dir and (upscaled_dir / f"{cfg['id']}.png").exists():
        im = Image.open(upscaled_dir / f"{cfg['id']}.png").convert("RGB")
    target_h = round(im.height * target_w / im.width)
    return im.resize((target_w, target_h), Image.LANCZOS)


def key_distance(rgb: np.ndarray, key) -> np.ndarray:
    return np.sqrt(((rgb - np.array(key, dtype=float)) ** 2).sum(-1))


def core_rect(comp: np.ndarray) -> tuple[int, int, int, int]:
    """Bounding box of the solid body of a component, ignoring thin leaks along outlines."""
    rows, cols = comp.sum(1), comp.sum(0)
    ys = np.where(rows >= 0.1 * rows.max())[0]
    xs = np.where(cols >= 0.1 * cols.max())[0]
    return xs[0], ys[0], xs[-1] + 1, ys[-1] + 1


def segment_windows(rgb0: np.ndarray, cfg: dict) -> tuple[np.ndarray, list[tuple[int, int, int, int]]]:
    """Find windows at native resolution, where thin outlines still separate characters from windows.

    Returns (region mask that may be keyed, window rects), both in native pixels.
    """
    t0, t1 = cfg.get("tolerance", [20, 55])
    dist = key_distance(rgb0, cfg["window_color"])
    h, w = dist.shape

    lab, _ = ndi.label(dist < t0)
    candidates = []
    for i, sl in enumerate(ndi.find_objects(lab), 1):
        touches = sl[0].start == 0 or sl[1].start == 0 or sl[0].stop == h or sl[1].stop == w
        area = int((lab[sl] == i).sum())
        if not touches and area >= 0.01 * h * w:
            candidates.append((area, i, sl))
    if not candidates:
        raise SystemExit(f"{cfg['id']}: no windows found")
    biggest = max(a for a, _, _ in candidates)
    # Windows are the big regions; same-coloured characters (white bears, clouds) are much smaller.
    windows = [(i, sl) for a, i, sl in candidates if a >= cfg.get("min_window_ratio", 0.3) * biggest]
    window_ids = {i for i, _ in windows}

    # Same-coloured shapes that aren't windows (a white cloud on a white-window frame) must stay opaque,
    # even where soft keying would otherwise creep into them through faint outlines.
    protected = np.zeros_like(dist, dtype=bool)
    for i, sl in enumerate(ndi.find_objects(lab), 1):
        if i not in window_ids and (lab[sl] == i).sum() >= 0.001 * h * w:
            protected[sl] |= lab[sl] == i
    protected = ndi.binary_dilation(protected, iterations=2)

    seeds = np.zeros_like(dist, dtype=bool)
    rects = []
    for i, sl in windows:
        comp = lab[sl] == i
        seeds[sl] |= comp
        x0, y0, x1, y1 = core_rect(comp)
        rects.append((sl[1].start + x0, sl[0].start + y0, sl[1].start + x1, sl[0].start + y1))

    lab, _ = ndi.label(dist < t1)
    region = np.isin(lab, np.unique(lab[seeds])) & (lab > 0)
    inside = np.zeros_like(region)
    for x0, y0, x1, y1 in rects:
        inside[max(0, y0 - 2):y1 + 2, max(0, x0 - 2):x1 + 2] = True
    region &= inside & ~protected
    region = ndi.binary_dilation(region, iterations=1) & inside & ~protected
    return region, rects


def key_windows(rgb: np.ndarray, region0: np.ndarray, cfg: dict) -> np.ndarray:
    """Soft key at full resolution, restricted to the native-resolution window region. 0 = photo."""
    t0, t1 = cfg.get("tolerance", [20, 55])
    h, w = rgb.shape[:2]
    region = np.asarray(Image.fromarray(region0.astype(np.uint8) * 255).resize((w, h), Image.NEAREST)) > 0
    ramp = np.clip((key_distance(rgb, cfg["window_color"]) - t0) / (t1 - t0), 0, 1)
    ramp = ramp * ramp * (3 - 2 * ramp)  # smoothstep
    alpha = np.ones((h, w))
    alpha[region] = ramp[region]

    # Remove tiny opaque specks (JPEG noise) fully enclosed by a window.
    window = alpha < 0.5
    lab, n = ndi.label(ndi.binary_fill_holes(window) & ~window)
    if n:
        sizes = ndi.sum(np.ones((h, w)), lab, index=np.arange(1, n + 1))
        small = np.isin(lab, np.where(sizes < cfg.get("speck_px", 60))[0] + 1)
        alpha[small] = 0
    return alpha


def decontaminate(rgb: np.ndarray, alpha: np.ndarray, key) -> np.ndarray:
    """Remove the key colour that bled into semi-transparent edge pixels."""
    a = alpha[..., None]
    out = np.where(a > 0.02, (rgb - (1 - a) * np.array(key, dtype=float)) / np.maximum(a, 1e-3), rgb)
    return np.clip(out, 0, 255)


def erase_text(rgb: np.ndarray, erase: list[dict]) -> np.ndarray:
    """Paint out lettering inside the given output-pixel rects.

    Pixels that differ from the rect's surrounding background are treated as text, then filled in from
    their surroundings (normalised convolution followed by harmonic smoothing), so textures nearby survive.
    """
    rgb = rgb.copy()
    for e in erase:
        x0, y0, x1, y1 = e["rect"]
        m = 12
        sy, sx = slice(max(0, y0 - m), y1 + m), slice(max(0, x0 - m), x1 + m)
        sub = rgb[sy, sx]
        inner = np.zeros(sub.shape[:2], dtype=bool)
        inner[y0 - sy.start:y1 - sy.start, x0 - sx.start:x1 - sx.start] = True
        background = np.median(sub[~inner], axis=0)
        if e.get("mode") == "all":  # plain backgrounds: repaint the whole rect
            text = inner
        else:
            text = inner & (np.sqrt(((sub - background) ** 2).sum(-1)) > e.get("tolerance", 35))
            text = ndi.binary_dilation(text, iterations=e.get("grow", 4)) & inner
        rgb[sy, sx] = harmonic_fill(sub, text)
    return rgb


def harmonic_fill(img: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Solve Laplace's equation over `mask` with the surrounding pixels as boundary values."""
    from scipy.sparse import coo_matrix
    from scipy.sparse.linalg import spsolve

    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    index = -np.ones((h, w), dtype=int)
    index[ys, xs] = np.arange(len(ys))
    rows, cols, vals = [], [], []
    b = np.zeros((len(ys), img.shape[2]))
    diag = np.zeros(len(ys))
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = ys + dy, xs + dx
        inside = (ny >= 0) & (ny < h) & (nx >= 0) & (nx < w)
        diag += inside
        unknown = inside.copy()
        unknown[inside] = mask[ny[inside], nx[inside]]
        rows.append(np.nonzero(unknown)[0]); cols.append(index[ny[unknown], nx[unknown]]); vals.append(-np.ones(unknown.sum()))
        boundary = inside & ~unknown
        b[boundary] += img[ny[boundary], nx[boundary]]
    n = len(ys)
    rows.append(np.arange(n)); cols.append(np.arange(n)); vals.append(diag)
    a = coo_matrix((np.concatenate(vals), (np.concatenate(rows), np.concatenate(cols))), shape=(n, n)).tocsc()
    out = img.copy()
    out[ys, xs] = np.column_stack([spsolve(a, b[:, c]) for c in range(img.shape[2])])
    return out


def to_slots(rects0, scale: float, size: tuple[int, int], grow: int = 6) -> list[dict]:
    """Scale native window rects to output pixels, grown so photos tuck under the artwork's edges."""
    w, h = size
    rects = [(max(0, round(x0 * scale) - grow), max(0, round(y0 * scale) - grow),
              min(w, round(x1 * scale) + grow), min(h, round(y1 * scale) + grow)) for x0, y0, x1, y1 in rects0]
    # Row-major order: rects whose tops are within 5% of the height share a row.
    rects.sort(key=lambda r: r[1])
    rows: list[list[tuple]] = []
    for r in rects:
        if rows and abs(rows[-1][0][1] - r[1]) < 0.05 * h:
            rows[-1].append(r)
        else:
            rows.append([r])
    ordered = [r for row in rows for r in sorted(row, key=lambda r: r[0])]
    return [{"x": x0, "y": y0, "w": x1 - x0, "h": y1 - y0} for x0, y0, x1, y1 in ordered]


def sample_photo(w: int, h: int, n: int) -> Image.Image:
    """A colourful stand-in photo so previews show exactly what lands in each window."""
    palettes = [((255, 179, 199), (255, 236, 179)), ((179, 220, 255), (214, 196, 255)),
                ((190, 240, 210), (255, 240, 180)), ((255, 205, 170), (250, 170, 210))]
    c0, c1 = palettes[(n - 1) % len(palettes)]
    t = np.linspace(0, 1, h)[:, None, None]
    grad = (np.array(c0) * (1 - t) + np.array(c1) * t) * np.ones((1, w, 1))
    im = Image.fromarray(grad.astype(np.uint8))
    d = ImageDraw.Draw(im)
    r = min(w, h) // 5
    d.ellipse((w // 2 - r, h // 2 - r, w // 2 + r, h // 2 + r), fill=(255, 255, 255))
    try:
        font = ImageFont.truetype("arial.ttf", r)
    except OSError:
        font = ImageFont.load_default()
    d.text((w // 2, h // 2), str(n), fill=(240, 98, 143), font=font, anchor="mm")
    return im


def composite_preview(frame: Image.Image, slots: list[dict]) -> Image.Image:
    base = Image.new("RGB", frame.size, (255, 255, 255))
    for i, s in enumerate(slots, 1):
        base.paste(sample_photo(s["w"], s["h"], i), (s["x"], s["y"]))
    base.paste(frame, (0, 0), frame)
    return base


def checker_preview(frame: Image.Image) -> Image.Image:
    w, h = frame.size
    yy, xx = np.mgrid[0:h, 0:w]
    checks = np.where(((xx // 24 + yy // 24) % 2)[..., None] == 0, 255, 205).repeat(3, -1).astype(np.uint8)
    base = Image.fromarray(checks)
    base.paste(frame, (0, 0), frame)
    return base


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--upscaled-dir", type=Path)
    ap.add_argument("--only")
    args = ap.parse_args()

    configs = json.loads(CONFIG.read_text(encoding="utf-8"))
    ASSETS.mkdir(parents=True, exist_ok=True)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    manifest_path = ASSETS / "frames.json"
    manifest = {m["id"]: m for m in json.loads(manifest_path.read_text("utf-8"))} if manifest_path.exists() else {}

    for cfg in configs:
        if args.only and cfg["id"] != args.only:
            continue
        crop = load_source(cfg)
        crop.save(PREVIEWS / f"{cfg['id']}_crop.png")
        region0, rects0 = segment_windows(np.asarray(crop).astype(float), cfg)
        big = upscale(crop, cfg, args.upscaled_dir)
        rgb = np.asarray(big).astype(float)
        alpha = key_windows(rgb, region0, cfg)
        rgb = decontaminate(rgb, alpha, cfg["window_color"])
        rgb = erase_text(rgb, cfg.get("erase", []))
        rgba = np.dstack([rgb, alpha * 255]).round().astype(np.uint8)
        frame = Image.fromarray(rgba, "RGBA")
        slots = to_slots(rects0, big.width / crop.width, big.size)

        out_dir = ASSETS / cfg["id"]
        out_dir.mkdir(exist_ok=True)
        frame.save(out_dir / "frame.png", optimize=True)
        thumb_w = 300
        frame.resize((thumb_w, round(frame.height * thumb_w / frame.width)), Image.LANCZOS).save(out_dir / "thumb.png", optimize=True)

        composite_preview(frame, slots).save(PREVIEWS / f"{cfg['id']}_photos.jpg", quality=90)
        checker_preview(frame).save(PREVIEWS / f"{cfg['id']}_alpha.jpg", quality=90)

        manifest[cfg["id"]] = {
            "id": cfg["id"],
            "name": cfg["name"],
            "file": f"{cfg['id']}/frame.png",
            "thumb": f"{cfg['id']}/thumb.png",
            "width": frame.width,
            "height": frame.height,
            "slots": slots,
            "textArea": cfg.get("text_area"),
        }
        print(f"{cfg['id']}: {frame.width}x{frame.height}, {len(slots)} slots -> {[tuple(s.values()) for s in slots]}")

    order = [c["id"] for c in configs]
    ordered = [manifest[i] for i in order if i in manifest]
    manifest_path.write_text(json.dumps(ordered, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
