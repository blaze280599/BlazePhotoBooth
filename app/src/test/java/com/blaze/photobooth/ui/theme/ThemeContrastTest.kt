package com.blaze.photobooth.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.blaze.photobooth.model.FrameTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * README §6.7: text on surfaces must have a contrast ratio of at least 4.5:1 (3:1 for icons and other non-text
 * marks), and footer text on every frame theme at least 3:1. Every text/background pair the screens use is listed
 * here, in both schemes.
 */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private class Pair(val name: String, val fg: Color, val bg: Color, val min: Float = 4.5f)

    /** The text (and icon) colours the app puts on each background, per scheme. */
    private fun pairs(s: ColorScheme, x: BoothExtraColors): List<Pair> {
        val surfaces = listOf(
            "surface" to s.surface,
            "background" to s.background,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "raised" to x.raised,
            "gradientTop" to x.gradientTop,
            "gradientBottom" to x.gradientBottom,
        )
        return buildList {
            // Body text and secondary text sit on every surface, the gradient included.
            for ((name, bg) in surfaces + ("surfaceContainerHighest" to s.surfaceContainerHighest) + ("surfaceVariant" to s.surfaceVariant)) {
                add(Pair("onSurface/$name", s.onSurface, bg))
                add(Pair("onSurfaceVariant/$name", s.onSurfaceVariant, bg))
            }
            add(Pair("onBackground/background", s.onBackground, s.background))
            // Strawberry text: titles' notes, group labels, pills and tonal buttons on raised controls, text buttons in
            // dialogs (surfaceContainerHigh). Never directly on the pink bottom of the gradient.
            for ((name, bg) in surfaces.filter { it.first != "gradientBottom" }) add(Pair("primary/$name", s.primary, bg))
            // Icons in strawberry on the tab row and the export buttons' circles.
            add(Pair("primary icon/surfaceContainerHighest", s.primary, s.surfaceContainerHighest, 3f))
            // Strawberry text low on the gradient (Add frame's notes) uses the deeper on-container berry.
            add(Pair("onPrimaryContainer/gradientBottom", s.onPrimaryContainer, x.gradientBottom))
            // Filled buttons, chips and the selected tab.
            add(Pair("onPrimary/primary", s.onPrimary, s.primary))
            add(Pair("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer))
            add(Pair("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer))
            add(Pair("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer))
            // Home's draft card: its text and the Continue button.
            add(Pair("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface))
            add(Pair("inversePrimary/inverseSurface", s.inversePrimary, s.inverseSurface))
            // Error text (a frame that couldn't be saved). Secondary (butter) and tertiary (lavender) are decor only.
            add(Pair("error/surfaceContainerLow", s.error, s.surfaceContainerLow))
        }
    }

    private fun check(scheme: ColorScheme, extra: BoothExtraColors, label: String) {
        val failures = pairs(scheme, extra).mapNotNull { p ->
            val ratio = contrast(p.fg, p.bg)
            if (ratio < p.min) "$label ${p.name}: ${"%.2f".format(ratio)} < ${p.min}" else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyLightPairIsReadable() = check(LightBoothColors, LightExtraColors, "light")

    @Test
    fun everyDarkPairIsReadable() = check(DarkBoothColors, DarkExtraColors, "dark")

    @Test
    fun theDecorPinkIsNeverTheTextColour() {
        // The bubblegum stays for particles, the blob and confetti; text uses primary, which is deeper.
        assertTrue(contrast(LightExtraColors.candy, LightBoothColors.surface) < 4.5f)
        assertTrue(LightBoothColors.primary != LightExtraColors.candy)
    }

    @Test
    fun everyFrameThemeFooterIsReadable() {
        // The footer (wordmark, caption, date) is drawn in the theme's ink over its background; on gradient themes the
        // footer sits on the bottom colour, but both ends are checked.
        val failures = FrameTheme.entries.flatMap { theme ->
            listOf(theme.backgroundTop, theme.backgroundBottom).mapNotNull { bg ->
                val ratio = contrast(Color(theme.ink), Color(bg))
                if (ratio < 3f) "${theme.id}: ${"%.2f".format(ratio)} on #${Integer.toHexString(bg)}" else null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
