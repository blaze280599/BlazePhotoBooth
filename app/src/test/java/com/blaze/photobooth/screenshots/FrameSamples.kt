package com.blaze.photobooth.screenshots

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.EditorWindow
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.SnapGuide
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.model.WindowEditor
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.FrameImport
import com.blaze.photobooth.render.SyntheticFrames
import com.blaze.photobooth.ui.feature.frames.AddFrameStep
import com.blaze.photobooth.ui.feature.frames.AddFrameUiState
import com.blaze.photobooth.ui.feature.frames.EditorUiState
import com.blaze.photobooth.ui.feature.frames.ManageFramesUiState
import com.blaze.photobooth.ui.feature.frames.ManagedFrameRow
import com.blaze.photobooth.ui.feature.setup.FrameCardState
import com.blaze.photobooth.ui.feature.setup.SetupUiState
import java.io.File
import java.time.LocalDate

/**
 * Import-flow, Manage-frames and Setup states built around code-drawn frames ([SyntheticFrames], never third-party
 * art). Detection runs for real on the synthetic strip, so the windows are what the phone would find.
 */
object FrameSamples {

    /** Her-style 4-window strip (1200 × 3600): the full artwork, its windows, and the preview-size picture. */
    class Strip(val art: Bitmap, val preview: Bitmap, val windows: List<SlotRect>) {
        val width get() = art.width
        val height get() = art.height
    }

    fun strip(): Strip {
        val (art, _) = SyntheticFrames.herStyleStrip()
        val analysis = FrameImport.analyze(art.px, art.w, art.h, art.w)
        val bitmap = art.toBitmap()
        return Strip(bitmap, BitmapOps.downscale(bitmap, 1600), analysis.windows)
    }

    fun finding(): AddFrameUiState = AddFrameUiState(step = AddFrameStep.Finding, artwork = strip().preview)

    fun confirm(naming: Boolean = false, tooSmall: Boolean = false): AddFrameUiState {
        val s = strip()
        return AddFrameUiState(
            step = AddFrameStep.Confirm,
            artwork = s.preview,
            result = s.preview,
            frameWidth = s.width,
            frameHeight = s.height,
            windows = s.windows,
            found = true,
            tooSmall = tooSmall,
            sourceWidth = if (tooSmall) 480 else s.width,
            naming = naming,
            name = if (naming) "Frame 1" else "",
        )
    }

    /** The editor with window 2 selected and snapped to the centre line (its guide showing). */
    fun editor(): AddFrameUiState {
        val s = strip()
        val windows = s.windows.mapIndexed { i, r -> EditorWindow(i, r) }
        val g = WindowEditor(s.width, s.height)
        return AddFrameUiState(
            step = AddFrameStep.Editor,
            artwork = s.preview,
            frameWidth = s.width,
            frameHeight = s.height,
            windows = s.windows,
            editor = EditorUiState(
                windows = g.ordered(windows),
                selected = 1,
                guides = listOf(SnapGuide(vertical = true, at = s.width / 2f)),
                problem = g.problem(windows),
            ),
        )
    }

    /** Detection found nothing on a plain opaque frame: the editor opens with one default window. */
    fun editorNoWindows(): AddFrameUiState {
        val art = SyntheticFrames.Art(1200, 1800, SyntheticFrames.Lilac).noise(3)
        SyntheticFrames.grid2x2(1200, 1800).forEach { b ->
            // Soft white dots on the paper, but no windows.
            art.fill(SyntheticFrames.Box(b.x + b.w / 3, b.y + b.h / 3, b.w / 3, b.w / 3), SyntheticFrames.White, SyntheticFrames.circle)
        }
        val bitmap = art.toBitmap()
        val window = WindowEditor.defaultWindow(1200, 1800)
        return AddFrameUiState(
            step = AddFrameStep.Editor,
            artwork = BitmapOps.downscale(bitmap, 1600),
            frameWidth = 1200,
            frameHeight = 1800,
            windows = listOf(window),
            editor = EditorUiState(windows = listOf(EditorWindow(0, window)), selected = 0, fromStart = true),
        )
    }

    fun saved(): AddFrameUiState = confirm(naming = true).copy(step = AddFrameStep.Saved)

    // ---- Imported frames for Manage frames and Setup ----

    private fun imported(uuid: String, name: String, art: SyntheticFrames.Art): Pair<ImageFrame, Bitmap> {
        val analysis = FrameImport.analyze(art.px, art.w, art.h, art.w)
        val frame = ImageFrame(
            id = FrameId.custom(uuid),
            name = name,
            width = art.w,
            height = art.h,
            slots = analysis.windows,
            textArea = null,
            artwork = ArtworkLocation.LocalFile(File("$uuid/frame.png")),
            thumb = ArtworkLocation.LocalFile(File("$uuid/thumb.png")),
        )
        val bitmap = art.toBitmap()
        val (tw, th) = FrameImport.thumbSize(art.w, art.h)
        return frame to bitmap.scale(tw, th)
    }

    /** Three frames "she" imported: the lilac strip, a mint 2×2 print and a butter trio. */
    fun importedFrames(): List<Pair<ImageFrame, Bitmap>> {
        val lilac = SyntheticFrames.herStyleStrip().first
        val mint = SyntheticFrames.Art(1200, 1800, 0xFFBEEBD3.toInt())
        SyntheticFrames.grid2x2(1200, 1800).forEach { mint.fill(it, SyntheticFrames.Clear, SyntheticFrames.rounded(it.w.toFloat() / it.h)) }
        val butter = SyntheticFrames.Art(900, 2700, 0xFFFFE9A8.toInt())
        val m = 70
        val h = (2700 - 4 * m - 300) / 3
        repeat(3) { i -> butter.fill(SyntheticFrames.Box(m, m + i * (h + m), 900 - 2 * m, h), SyntheticFrames.Clear, SyntheticFrames.heart) }
        return listOf(
            imported("11111111", "Our first date ♡", lilac),
            imported("22222222", "Mint picnic", mint),
            imported("33333333", "Frame 3", butter),
        )
    }

    fun manage(context: Context): ManageFramesUiState {
        val bundled = TestArt.herFrames(context).map { f ->
            ManagedFrameRow(f, bundled = true, hidden = f.id == FrameId.asset("ikmyeong-night"), thumbnail = TestArt.load(context, f.thumb))
        }
        val imported = importedFrames().mapIndexed { i, (f, thumb) -> ManagedFrameRow(f, bundled = false, hidden = i == 2, thumbnail = thumb) }
        return ManageFramesUiState(bundled = bundled, imported = imported, loaded = true)
    }

    /** Setup's Frame tab with her imported lilac strip selected. */
    fun setupImported(context: Context): SetupUiState {
        val her = TestArt.herFrames(context).map { FrameCardState(FrameOption.Artwork(it), TestArt.load(context, it.thumb)) }
        val imports = importedFrames().take(2)
        val mine = imports.map { (f, thumb) -> FrameCardState(FrameOption.Artwork(f), thumb) }
        val (frame, _) = imports.first()
        val art = BitmapOps.downscale(SyntheticFrames.herStyleStrip().first.toBitmap(), 1800).let { b ->
            if (b.width > BitmapOps.PreviewMaxPx) b.scale(BitmapOps.PreviewMaxPx, b.height * BitmapOps.PreviewMaxPx / b.width) else b
        }
        val config = BoothConfig(frameId = frame.id)
        return SetupUiState(
            config = config,
            frames = her + mine + FrameTheme.entries.map { FrameCardState(FrameOption.Theme(it)) },
            preview = StripSpec(FrameSource.Image(frame, art), filter = config.filter, date = LocalDate.of(2026, 10, 20), preview = true),
        )
    }
}
