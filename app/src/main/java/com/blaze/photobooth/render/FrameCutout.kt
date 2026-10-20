package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns an imported frame plus her final windows into artwork whose windows are see-through (README §4.9 step 4,
 * §5.5.1). Pure Kotlin over ARGB pixels, so it's unit-tested, and the same code makes the Confirm preview and the
 * saved `frame.png` (only the resolution differs).
 *
 * For each window:
 *  - **Keep keyed:** an unchanged window that Mode B found (solid box) gets the detector's keyed pixels back, which
 *    keep stars or hearts drawn inside the box on top of the photo.
 *  - **Already open:** a window over pixels that are already mostly transparent (a transparent PNG) is left alone.
 *  - **Cut:** anything else (a window she drew or moved on an opaque frame) is cut out as the rounded rect the editor
 *    shows, with a [FeatherPx] soft edge that lies just inside the window, so the photo covers it.
 *
 * Mode B boxes she removed or moved get their original colour back, because the cut starts from the original.
 */
object FrameCutout {

    /** Soft edge of a cut window, in full-resolution pixels. */
    const val FeatherPx = 2f

    /** A cut window's corner radius as a share of its short side (the editor draws the same). */
    const val CornerFraction = 0.06f

    /** A window over pixels at least this transparent already has its hole. */
    const val OpenFraction = 0.5f

    sealed interface Action {
        data class KeepKeyed(val window: SlotRect) : Action
        data class AlreadyOpen(val window: SlotRect) : Action
        data class Cut(val window: SlotRect) : Action
    }

    fun cornerRadius(window: SlotRect): Float = min(window.w, window.h) * CornerFraction

    /**
     * What to do with each of [windows] (full-resolution rects). [original] is [width] × [height] pixels at [scale]
     * of full resolution. [keyedSlots] are the detector's Mode B windows (empty otherwise).
     */
    fun plan(original: IntArray, width: Int, height: Int, scale: Float, keyedSlots: List<SlotRect>, windows: List<SlotRect>): List<Action> =
        windows.map { w ->
            when {
                keyedSlots.any { it.sameAs(w) } -> Action.KeepKeyed(w)
                transparentFraction(original, width, height, w.scaled(scale)) >= OpenFraction -> Action.AlreadyOpen(w)
                else -> Action.Cut(w)
            }
        }

    /**
     * Applies [plan] to [pixels] (the original artwork, changed in place). [keyed] is the detector's keyed copy at the
     * same resolution (needed only for [Action.KeepKeyed]). [keyPad] is how far around a Mode B window the detector's
     * keying can reach, in full-resolution pixels.
     */
    fun apply(
        pixels: IntArray,
        keyed: IntArray?,
        width: Int,
        height: Int,
        scale: Float,
        actions: List<Action>,
        keyPad: Float,
    ) {
        for (action in actions) {
            if (action is Action.KeepKeyed && keyed != null) paste(pixels, keyed, width, height, action.window.grow(keyPad).scaled(scale))
        }
        for (action in actions) {
            if (action is Action.Cut) {
                val w = action.window.scaled(scale)
                cut(pixels, width, height, w, cornerRadius(w), max(1f, FeatherPx * scale))
            }
        }
    }

    /** How far the detector's keying reaches past a Mode B window for a [width] × [height] frame. */
    fun keyPadFor(width: Int, height: Int): Float = FrameSlotDetector.workFactor(width, height) + 6f

    /** Share of the pixels inside [rect] with alpha < [FrameSlotDetector.AlphaThreshold]. */
    fun transparentFraction(pixels: IntArray, width: Int, height: Int, rect: SlotRect): Float {
        val (x0, y0, x1, y1) = bounds(rect, width, height) ?: return 0f
        var clear = 0
        for (y in y0 until y1) for (x in x0 until x1) if ((pixels[y * width + x] ushr 24) < FrameSlotDetector.AlphaThreshold) clear++
        return clear.toFloat() / ((x1 - x0) * (y1 - y0))
    }

    /**
     * Makes the rounded rect [rect] see-through: alpha is 0 deeper than [feather] inside the edge, rising to the
     * artwork's own alpha at the edge (a soft inner edge the photo covers).
     */
    fun cut(pixels: IntArray, width: Int, height: Int, rect: SlotRect, radius: Float, feather: Float) {
        val (x0, y0, x1, y1) = bounds(rect, width, height) ?: return
        val r = radius.coerceIn(0f, min(rect.w, rect.h) / 2f)
        val cx = rect.x + rect.w / 2f
        val cy = rect.y + rect.h / 2f
        val hx = rect.w / 2f - r
        val hy = rect.h / 2f - r
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val qx = abs(x + 0.5f - cx) - hx
                val qy = abs(y + 0.5f - cy) - hy
                val ox = max(qx, 0f)
                val oy = max(qy, 0f)
                val sd = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - r
                // sd < 0 inside: 1 at the edge, 0 from [feather] inward.
                val m = (1f + sd / feather).coerceIn(0f, 1f)
                if (m >= 1f) continue
                val i = y * width + x
                val a = ((pixels[i] ushr 24) * m).roundToInt()
                pixels[i] = (a shl 24) or (pixels[i] and 0x00FFFFFF)
            }
        }
    }

    private fun paste(pixels: IntArray, keyed: IntArray, width: Int, height: Int, rect: SlotRect) {
        val (x0, y0, x1, y1) = bounds(rect, width, height) ?: return
        for (y in y0 until y1) for (x in x0 until x1) pixels[y * width + x] = keyed[y * width + x]
    }

    /** Pixel bounds [x0, x1) × [y0, y1) of [rect] clipped to the image, or null if it's off the image. */
    private fun bounds(rect: SlotRect, width: Int, height: Int): IntArray? {
        val x0 = max(0, floor(rect.x).toInt())
        val y0 = max(0, floor(rect.y).toInt())
        val x1 = min(width, ceil(rect.right).toInt())
        val y1 = min(height, ceil(rect.bottom).toInt())
        return if (x1 <= x0 || y1 <= y0) null else intArrayOf(x0, y0, x1, y1)
    }

    private fun SlotRect.sameAs(o: SlotRect) =
        abs(x - o.x) < 0.5f && abs(y - o.y) < 0.5f && abs(right - o.right) < 0.5f && abs(bottom - o.bottom) < 0.5f

    private fun SlotRect.scaled(s: Float) = if (s == 1f) this else SlotRect(x * s, y * s, w * s, h * s)

    private fun SlotRect.grow(by: Float) = SlotRect(x - by, y - by, w + 2 * by, h + 2 * by)
}
