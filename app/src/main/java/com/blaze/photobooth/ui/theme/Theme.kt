package com.blaze.photobooth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberSystemReduceMotion

/**
 * Blaze Booth theme (README §6): brand colours (dynamic colour off), Fredoka/Gaegu type,
 * rounded shapes, plus the extra decor colours and the reduce-motion flag as locals.
 */
@Composable
fun BoothTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = rememberSystemReduceMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBoothExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkBoothColors else LightBoothColors,
            typography = BoothTypography,
            shapes = BoothShapes,
            content = content,
        )
    }
}

/** Accessors for the Blaze Booth tokens that MaterialTheme doesn't carry. */
object BoothTheme {
    val extraColors: BoothExtraColors
        @Composable @ReadOnlyComposable
        get() = LocalBoothExtraColors.current
}
