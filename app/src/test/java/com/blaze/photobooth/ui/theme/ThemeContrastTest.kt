package com.blaze.photobooth.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §6.7: text on surfaces must have a contrast ratio of at least 4.5:1. */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun lightSchemeTextIsReadable() {
        val ratio = contrast(LightBoothColors.onSurface, LightBoothColors.surface)
        assertTrue("light onSurface/surface contrast was $ratio", ratio >= 4.5f)
    }

    @Test
    fun darkSchemeTextIsReadable() {
        val ratio = contrast(DarkBoothColors.onSurface, DarkBoothColors.surface)
        assertTrue("dark onSurface/surface contrast was $ratio", ratio >= 4.5f)
    }
}
