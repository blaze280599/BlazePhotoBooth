package com.blaze.photobooth.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette, README §6.1. Dynamic colour is off on purpose.

// Light ("strawberry milk")
val Strawberry = Color(0xFFF0628F)
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
val MidnightContainer = Color(0xFF261E2C)
val Marshmallow = Color(0xFFF3E8EE)
val Dusk = Color(0xFF5A4A55)
val MintDark = Color(0xFF7FCFAA)
val SkyDark = Color(0xFF86BDEB)

internal val LightBoothColors = lightColorScheme(
    primary = Strawberry,
    onPrimary = Color.White,
    secondary = Butter,
    onSecondary = Cocoa,
    tertiary = Lavender,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Cocoa,
    surface = Cream,
    onSurface = Cocoa,
    surfaceContainer = CreamContainer,
    outline = Blush,
)

internal val DarkBoothColors = darkColorScheme(
    primary = StrawberryDark,
    onPrimary = OnStrawberryDark,
    secondary = ButterDark,
    onSecondary = Midnight,
    tertiary = LavenderDark,
    onTertiary = Midnight,
    background = Midnight,
    onBackground = Marshmallow,
    surface = Midnight,
    onSurface = Marshmallow,
    surfaceContainer = MidnightContainer,
    outline = Dusk,
)
