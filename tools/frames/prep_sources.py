"""Clean up sources that process_frames.py can't key directly, writing photo-frames/prepped/<id>.png.

- union-jack: a printed Life4Cuts sheet with photos in the windows. Keeps the left strip, repaints the
  navy border flat, fills the four photo windows with the key colour, mirrors the left flag edge over the
  sliver of the neighbouring strip's flag, and paints out the printed date.
- snowy-christmas: windows are a soft blue/lilac gradient with sparkles, so no single key colour fits.
  Grows each window from a seed through smooth gradients (stopping at outlines and snow) and fills it
  with the key colour.

The outputs are committed, so process_frames.py runs without this step. Requirements: pillow, numpy, scipy.
Usage: python tools/frames/prep_sources.py
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "photo-frames"
OUT = SRC / "prepped"
KEY = (255, 0, 255)


def union_jack() -> Image.Image:
    a = np.asarray(Image.open(SRC / "35995ba50a5edb01f459111cd0273540.jpg").convert("RGB"))[:, :382].copy()
    flag_top = 905
    navy = np.median(a[:30, :29].reshape(-1, 3), axis=0).round().astype(np.uint8)
    a[:flag_top] = navy
    # The flag behind the strip is centred on x=191; the right edge shows the next strip's flag instead.
    a[flag_top:, 353:] = a[flag_top:, :29][:, ::-1]
    # Date printed over the vertical bar of the cross: the bar's columns are uniform, so copy a clean row band.
    clean = np.median(a[1040:1052, 150:232], axis=0).round().astype(np.uint8)
    a[1054:1092, 150:232] = clean
    for y0, y1 in ((30, 246), (261, 477), (492, 709), (723, 940)):
        a[y0:y1, 29:353] = KEY
    return Image.fromarray(a)


def snowy_christmas() -> Image.Image:
    a = np.asarray(Image.open(SRC / "cbc2c6af3b300fff7a7c900b3d29a8fc.jpg").convert("RGB").crop((22, 50, 247, 726)))
    smooth = ndi.gaussian_filter(a.astype(float), (0.7, 0.7, 0))
    gy = np.abs(np.diff(smooth, axis=0, append=smooth[-1:])).sum(-1)
    gx = np.abs(np.diff(smooth, axis=1, append=smooth[:, -1:])).sum(-1)
    lab, _ = ndi.label(np.maximum(gx, gy) < 34)
    windows = np.zeros(lab.shape, dtype=bool)
    for x, y in ((115, 100), (115, 257), (115, 397), (115, 547)):
        windows |= lab == lab[y, x]
    windows = ndi.binary_dilation(ndi.binary_fill_holes(windows), iterations=1)
    out = a.copy()
    out[windows] = KEY
    return Image.fromarray(out)


def main() -> None:
    OUT.mkdir(exist_ok=True)
    for name, fn in (("union-jack", union_jack), ("snowy-christmas", snowy_christmas)):
        fn().save(OUT / f"{name}.png")
        print(f"{name} -> {OUT / name}.png")


if __name__ == "__main__":
    main()
