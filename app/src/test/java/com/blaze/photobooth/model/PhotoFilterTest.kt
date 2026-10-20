package com.blaze.photobooth.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §5.3: the filter matrices do what their descriptions promise. */
class PhotoFilterTest {

    private fun PhotoFilter.on(r: Int, g: Int, b: Int) = ColorMatrices.apply(matrix, r.toFloat(), g.toFloat(), b.toFloat())

    @Test
    fun originalIsIdentity() {
        assertArrayEquals(ColorMatrices.identity(), PhotoFilter.Original.matrix, 0f)
    }

    @Test
    fun everyFilterKeepsAlphaAndIsWellFormed() {
        for (filter in PhotoFilter.entries) {
            assertEquals(20, filter.matrix.size)
            assertArrayEquals("$filter alpha row", floatArrayOf(0f, 0f, 0f, 1f, 0f), filter.matrix.copyOfRange(15, 20), 1e-5f)
        }
        assertEquals(PhotoFilter.entries.size, PhotoFilter.entries.map { it.id }.toSet().size)
    }

    @Test
    fun monoIsGreyAndContrasty() {
        val out = PhotoFilter.Mono.on(220, 90, 60)
        assertEquals(out[0], out[1], 0.5f)
        assertEquals(out[1], out[2], 0.5f)
        // Higher contrast: darks get darker and lights lighter than plain greyscale.
        assertTrue(PhotoFilter.Mono.on(60, 60, 60)[0] < 60f)
        assertTrue(PhotoFilter.Mono.on(200, 200, 200)[0] > 200f)
    }

    @Test
    fun milkyLiftsShadowsSoftensContrastAndWarmsPink() {
        val black = PhotoFilter.Milky.on(0, 0, 0)
        val white = PhotoFilter.Milky.on(255, 255, 255)
        assertTrue("lifted shadows", black.take(3).all { it > 35f })
        assertTrue("lower contrast", white[1] - black[1] < 230f)
        val grey = PhotoFilter.Milky.on(128, 128, 128)
        assertTrue("brighter", grey[1] > 128f)
        assertTrue("pink warmth", grey[0] > grey[1] && grey[2] > grey[1])
    }

    @Test
    fun peachIsWarmAndSaturated() {
        val grey = PhotoFilter.Peach.on(128, 128, 128)
        assertTrue("warm", grey[0] > grey[2] + 15f)
        val skin = PhotoFilter.Peach.on(200, 150, 120)
        assertTrue("more saturated", skin[0] - skin[2] > 200f - 120f)
    }

    @Test
    fun sodaIsCoolAndCrisp() {
        val grey = PhotoFilter.Cool.on(128, 128, 128)
        assertTrue("cool", grey[2] > grey[0] + 15f)
        val dark = PhotoFilter.Cool.on(40, 40, 40)
        val light = PhotoFilter.Cool.on(210, 210, 210)
        assertTrue("crisp", light[1] - dark[1] > 170f)
    }

    @Test
    fun filmFadesBlacksWarmsHighlightsGreensShadows() {
        val black = PhotoFilter.Film.on(0, 0, 0)
        assertTrue("faded blacks", black.take(3).all { it > 12f })
        assertTrue("green shadows", black[1] > black[0] && black[1] > black[2])
        val white = PhotoFilter.Film.on(255, 255, 255)
        assertTrue("warm highlights", white[0] > white[2] + 10f)
        assertTrue("no pure white", white[2] < 250f)
    }

    @Test
    fun concatAppliesLeftToRight() {
        val brighten = ColorMatrices.tint(1f, 1f, 1f, 10f, 10f, 10f)
        val double = ColorMatrices.tint(2f, 2f, 2f)
        // (x + 10) * 2, not x * 2 + 10.
        assertEquals(40f, ColorMatrices.apply(ColorMatrices.concat(brighten, double), 10f, 10f, 10f)[0], 1e-4f)
    }
}
