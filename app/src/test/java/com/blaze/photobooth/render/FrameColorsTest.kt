package com.blaze.photobooth.render

import com.blaze.photobooth.model.FrameTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** README §4.3: the viewfinder is framed in the frame's colour. */
class FrameColorsTest {

    @Test
    fun themesUseTheirBackground() {
        assertEquals(0xFFFFD6E3.toInt(), FrameColors.of(FrameTheme.Strawberry))
    }

    @Test
    fun artworkAveragesItsSolidPixelsAndIgnoresWindows() {
        val lilac = 0xFFC8B4F0.toInt()
        val white = 0xFFFFFFFF.toInt()
        val window = 0x00000000
        val pixels = IntArray(100) { i -> if (i < 50) window else if (i < 75) lilac else white }
        assertEquals(0xFFE3D9F7.toInt(), FrameColors.average(pixels))
    }

    @Test
    fun allSeeThroughFallsBack() {
        assertEquals(7, FrameColors.average(IntArray(10), fallback = 7))
    }
}
