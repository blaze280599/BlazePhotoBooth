package com.blaze.photobooth.core

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * README §6.7: when the system animator duration scale is 0 (Developer options, or
 * Accessibility → "Remove animations"), ambient motion stops: floating particles freeze,
 * the blob and wordmark hold still, and confetti and the printing sway are skipped.
 * State changes still cross-fade.
 *
 * Provided by [com.blaze.photobooth.ui.theme.BoothTheme]; read it with `LocalReduceMotion.current`.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** True while the system asks for no animations. Updates live if the setting changes. */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = !ValueAnimator.areAnimatorsEnabled() ||
                    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        observer.onChange(false)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}
