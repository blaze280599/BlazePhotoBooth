package com.blaze.photobooth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.blaze.photobooth.R

// README §6.2: Fredoka (variable) for all UI text, Gaegu Bold for handwritten touches.

private fun fredoka(weight: FontWeight) = Font(
    resId = R.font.fredoka,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Fredoka = FontFamily(
    fredoka(FontWeight.Normal),
    fredoka(FontWeight.Medium),
    fredoka(FontWeight.SemiBold),
    fredoka(FontWeight.Bold),
)

val Gaegu = FontFamily(Font(R.font.gaegu_bold, FontWeight.Bold))

private val Base = Typography()

private fun TextStyle.fredoka(weight: FontWeight) = copy(fontFamily = Fredoka, fontWeight = weight)

val BoothTypography = Typography(
    displayLarge = Base.displayLarge.fredoka(FontWeight.SemiBold),
    displayMedium = Base.displayMedium.fredoka(FontWeight.SemiBold),
    displaySmall = Base.displaySmall.fredoka(FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.fredoka(FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.fredoka(FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.fredoka(FontWeight.SemiBold),
    titleLarge = Base.titleLarge.fredoka(FontWeight.Medium),
    titleMedium = Base.titleMedium.fredoka(FontWeight.Medium),
    titleSmall = Base.titleSmall.fredoka(FontWeight.Medium),
    bodyLarge = Base.bodyLarge.fredoka(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.fredoka(FontWeight.Normal),
    bodySmall = Base.bodySmall.fredoka(FontWeight.Normal),
    labelLarge = Base.labelLarge.fredoka(FontWeight.Medium),
    labelMedium = Base.labelMedium.fredoka(FontWeight.Medium),
    labelSmall = Base.labelSmall.fredoka(FontWeight.Medium),
)

/** Handwritten Gaegu style for taglines, captions and date stamps. */
fun TextStyle.handwritten() = copy(fontFamily = Gaegu, fontWeight = FontWeight.Bold)
