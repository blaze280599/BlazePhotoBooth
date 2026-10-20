package com.blaze.photobooth.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand palette, README §6.1. Dynamic colour is off on purpose.

// Light ("strawberry milk")
/** The bubblegum brand pink. Decor only in light mode (particles, blob, confetti): as text it's under 3:1 on cream. */
val Strawberry = Color(0xFFF0628F)

/** Light `primary`: the same strawberry, deep enough for text and white labels (≥ 4.5:1 on every light surface, §6.7). */
val StrawberryInk = Color(0xFFC2305E)
val StrawberryMilk = Color(0xFFFFD6E3)
val Butter = Color(0xFFF5C84C)
val Lavender = Color(0xFF9C83F0)
val Cream = Color(0xFFFFF7F3)
val CreamContainer = Color(0xFFFFEDEF)
val Cocoa = Color(0xFF3A2E33)
val Blush = Color(0xFFE8C9D2)
val Mint = Color(0xFF9FE2BF)
val Sky = Color(0xFFA7D8FF)

// Dark ("midnight candy")
val StrawberryDark = Color(0xFFFF8FB1)
val OnStrawberryDark = Color(0xFF3B0A1E)
val ButterDark = Color(0xFFFFE38A)
val LavenderDark = Color(0xFFC9B6FF)
val Midnight = Color(0xFF1B1520)
val MidnightPlum = Color(0xFF3A2342)
val MidnightContainer = Color(0xFF261E2C)
val Marshmallow = Color(0xFFF3E8EE)
val Dusk = Color(0xFF5A4A55)
val MintDark = Color(0xFF7FCFAA)
val SkyDark = Color(0xFF86BDEB)

internal val LightBoothColors = lightColorScheme(
    primary = StrawberryInk,
    onPrimary = Color.White,
    primaryContainer = StrawberryMilk,
    onPrimaryContainer = Color(0xFF6B1534),
    inversePrimary = StrawberryDark,
    secondary = Butter,
    onSecondary = Cocoa,
    secondaryContainer = Color(0xFFFFF1B8),
    onSecondaryContainer = Color(0xFF5C4200),
    tertiary = Lavender,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6DCFF),
    onTertiaryContainer = Color(0xFF35206F),
    background = Cream,
    onBackground = Cocoa,
    surface = Cream,
    onSurface = Cocoa,
    surfaceVariant = Color(0xFFFBE4EA),
    onSurfaceVariant = Color(0xFF6E5A62),
    surfaceTint = StrawberryInk,
    inverseSurface = Cocoa,
    inverseOnSurface = Color(0xFFFFF0F3),
    surfaceBright = Cream,
    surfaceDim = Color(0xFFF1DCDF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF2F1),
    surfaceContainer = CreamContainer,
    surfaceContainerHigh = Color(0xFFFFE6EA),
    surfaceContainerHighest = Color(0xFFFDDDE4),
    outline = Blush,
    outlineVariant = Color(0xFFF3DCE2),
)

internal val DarkBoothColors = darkColorScheme(
    primary = StrawberryDark,
    onPrimary = OnStrawberryDark,
    primaryContainer = Color(0xFF6B2142),
    onPrimaryContainer = Color(0xFFFFD9E4),
    // On the light inverse surface (the draft card's Continue): the bubblegum would be 2.6:1, this is 5.2:1.
    inversePrimary = Color(0xFFB4285A),
    secondary = ButterDark,
    onSecondary = Midnight,
    secondaryContainer = Color(0xFF5A4710),
    onSecondaryContainer = Color(0xFFFFF1B8),
    tertiary = LavenderDark,
    onTertiary = Midnight,
    tertiaryContainer = Color(0xFF4A3A86),
    onTertiaryContainer = Color(0xFFE9DFFF),
    background = Midnight,
    onBackground = Marshmallow,
    surface = Midnight,
    onSurface = Marshmallow,
    surfaceVariant = Color(0xFF3A2E3D),
    onSurfaceVariant = Color(0xFFD8C5CF),
    surfaceTint = StrawberryDark,
    inverseSurface = Marshmallow,
    inverseOnSurface = Color(0xFF2E2433),
    surfaceBright = Color(0xFF3B3141),
    surfaceDim = Midnight,
    surfaceContainerLowest = Color(0xFF140F18),
    surfaceContainerLow = Color(0xFF211A26),
    surfaceContainer = MidnightContainer,
    surfaceContainerHigh = Color(0xFF302736),
    surfaceContainerHighest = Color(0xFF3B3141),
    outline = Dusk,
    outlineVariant = Color(0xFF3F3340),
)

/**
 * Brand colours Material's [androidx.compose.material3.ColorScheme] has no slot for:
 * the decor-only accents (§6.1), the Home background gradient (§4.1) and raised controls on it.
 */
@Immutable
data class BoothExtraColors(
    /** The brand pink for decoration (particles, the blob, confetti, heart bursts); never for text. */
    val candy: Color,
    val mint: Color,
    val sky: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    /** Raised controls floating on the gradient (My Album, the settings gear). */
    val raised: Color,
)

internal val LightExtraColors = BoothExtraColors(
    candy = Strawberry,
    mint = Mint,
    sky = Sky,
    gradientTop = Cream,
    gradientBottom = StrawberryMilk,
    raised = Color.White,
)

internal val DarkExtraColors = BoothExtraColors(
    candy = StrawberryDark,
    mint = MintDark,
    sky = SkyDark,
    gradientTop = Midnight,
    gradientBottom = MidnightPlum,
    raised = Color(0xFF3B3141),
)

internal val LocalBoothExtraColors = staticCompositionLocalOf { LightExtraColors }
