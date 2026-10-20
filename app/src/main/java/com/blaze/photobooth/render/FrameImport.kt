package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.WindowEditor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The pure half of importing a frame on the phone (README §4.9, §13): how big to decode it, and what the detector
 * makes of it. The Android half (decoding the picked file, saving PNGs) is `data/frames/FrameImporter`.
 */
object FrameImport {

    /** Huge images are scaled down to this long edge on import (§13). */
    const val MaxEdge = 7200

    /** …and to at most this many pixels, so the import fits the app's 256 MB heap on the Pixel 9. */
    const val MaxPixels = 12_000_000

    /** Narrower than this, strips may look blurry: she's warned, but can carry on (§4.9 step 5). */
    const val MinWidth = 600

    /** The size to import a [width] × [height] image at: as is, unless it's over [MaxEdge] or [MaxPixels]. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        val f = minOf(1.0, MaxEdge.toDouble() / maxOf(width, height), sqrt(MaxPixels.toDouble() / (width.toDouble() * height)))
        if (f >= 1.0) return width to height
        val w = (width * f).roundToInt().coerceAtLeast(1)
        val h = (height * f).roundToInt().coerceAtLeast(1)
        // Rounding up can nudge it just over the pixel cap: round down instead.
        if (w.toLong() * h <= MaxPixels) return w to h
        return (width * f).toInt().coerceAtLeast(1) to (height * f).toInt().coerceAtLeast(1)
    }

    /** A power-of-two decoder sample size that still leaves the image at least [targetSize]. */
    fun sampleSize(width: Int, height: Int): Int {
        val (tw, th) = targetSize(width, height)
        var s = 1
        while (width / (s * 2) >= tw && height / (s * 2) >= th) s *= 2
        return s
    }

    /**
     * What the detector found in the imported pixels.
     *  - [windows]: the found windows in strip order, or one default window for the editor when none were found.
     *  - [mode]: null when nothing was found (the flow goes straight to the editor, §13).
     *  - [keyed]: Mode B's copy with the boxes keyed to transparency (null otherwise).
     *  - [tooSmall]: the picked file is under [MinWidth] px wide.
     */
    class Analysis(
        val windows: List<SlotRect>,
        val mode: FrameSlotDetector.Mode?,
        val keyed: IntArray?,
        val tooSmall: Boolean,
    ) {
        val found: Boolean get() = mode != null

        /** The windows the detector keyed (Mode B), which the cut-out can keep as they are. */
        val keyedWindows: List<SlotRect> get() = if (mode == FrameSlotDetector.Mode.SolidBoxes) windows else emptyList()
    }

    /** Runs the detector over [pixels] ([width] × [height]); [sourceWidth] is the picked file's own width. */
    fun analyze(pixels: IntArray, width: Int, height: Int, sourceWidth: Int): Analysis {
        val tooSmall = sourceWidth < MinWidth
        return when (val r = FrameSlotDetector.detect(pixels, width, height)) {
            is FrameSlotDetector.Result.Found -> Analysis(
                windows = r.slots,
                mode = r.mode,
                keyed = r.pixels.takeIf { r.mode == FrameSlotDetector.Mode.SolidBoxes },
                tooSmall = tooSmall,
            )
            is FrameSlotDetector.Result.NotFound ->
                Analysis(listOf(WindowEditor.defaultWindow(width, height)), mode = null, keyed = null, tooSmall = tooSmall)
        }
    }

    /** The carousel thumbnail's size (§11.1): 300 px wide, or less for a frame that's narrower. */
    fun thumbSize(width: Int, height: Int): Pair<Int, Int> {
        val w = min(ThumbWidth, width)
        return w to (height * w.toFloat() / width).roundToInt().coerceAtLeast(1)
    }

    const val ThumbWidth = 300
}
