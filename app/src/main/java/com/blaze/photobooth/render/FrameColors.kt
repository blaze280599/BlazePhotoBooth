package com.blaze.photobooth.render

import com.blaze.photobooth.model.FrameTheme

/** The one colour that stands for a frame, e.g. around the Capture viewfinder (README §4.3). Pure, unit-tested. */
object FrameColors {

    /** A theme's colour is its top background colour. */
    fun of(theme: FrameTheme): Int = theme.backgroundTop

    /**
     * An artwork frame's colour: the average of its solid pixels (ARGB [pixels]); see-through windows
     * and soft edges are ignored. Falls back to [fallback] if there are none.
     */
    fun average(pixels: IntArray, fallback: Int = FrameTheme.Default.backgroundTop): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (p in pixels) {
            if ((p ushr 24) < SolidAlpha) continue
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
        if (n == 0L) return fallback
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private const val SolidAlpha = 200
}
