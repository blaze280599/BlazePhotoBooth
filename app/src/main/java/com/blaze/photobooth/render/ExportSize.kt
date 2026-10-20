package com.blaze.photobooth.render

import com.blaze.photobooth.model.CanvasSize
import com.blaze.photobooth.model.SlotRect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How big the printed files are (README §5.5.1, §10). Pure maths, unit-tested.
 * - Theme strips export 1:1 at their §5.1 canvas size.
 * - Image frames export at their native size; narrower than 1200 px is upscaled ×2, and a long edge over
 *   7200 px is scaled down to fit.
 * - `thumb.jpg` is 600 px wide.
 */
object ExportSize {
    const val MinWidth = 1200
    const val MaxLongEdge = 7200
    const val ThumbWidth = 600

    /** Strip pixels per canvas unit. */
    fun scale(canvas: CanvasSize): Float {
        var s = if (canvas.width < MinWidth) 2f else 1f
        val long = max(canvas.width, canvas.height) * s
        if (long > MaxLongEdge) s *= MaxLongEdge / long
        return s
    }

    fun strip(canvas: CanvasSize): CanvasSize {
        val s = scale(canvas)
        return CanvasSize((canvas.width * s).roundToInt(), (canvas.height * s).roundToInt())
    }

    fun thumb(strip: CanvasSize): CanvasSize =
        CanvasSize(ThumbWidth, (strip.height * ThumbWidth.toFloat() / strip.width).roundToInt().coerceAtLeast(1))

    /**
     * How wide a `shotW × shotH` photo must be decoded to cover [slot] at [scale] without upscaling
     * past the stored file: no wider than the photo itself.
     */
    fun photoWidth(slot: SlotRect, shotW: Int, shotH: Int, scale: Float): Int {
        val shotAspect = shotW.toFloat() / shotH
        val needed = if (shotAspect > slot.aspect) slot.h * scale * shotAspect else slot.w * scale
        return ceil(needed).toInt().coerceIn(1, shotW)
    }
}
