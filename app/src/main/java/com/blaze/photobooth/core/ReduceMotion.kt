package com.blaze.photobooth.core

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * README §6.7: when the system animator duration scale is 0 (Developer options, or
 * Accessibility → "Remove animations"), ambient motion stops: floating particles freeze,
 * the blob and wordmark hold still, the Setup preview stops tilting, and confetti, the printing
 * sway, the countdown wiggle and heart bursts are skipped. State changes still cross-fade.
 *
 * Provided by [com.blaze.photobooth.ui.theme.BoothTheme]; read it with `LocalReduceMotion.current`.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * A debug-only switch over the system setting (README §15.3), so reduce motion can be tried on the phone without
 * touching its settings. Only debug builds set it (`app/src/debug/`, from the `files/debug_reduce_motion` trigger);
 * in release builds it stays `null` and the system setting rules.
 */
object ReduceMotionOverride {
    private val state = MutableStateFlow<Boolean?>(null)
    val value: StateFlow<Boolean?> = state.asStateFlow()

    fun set(reduce: Boolean?) {
        state.value = reduce
    }

    /** What the app uses: the override if a debug build set one, else the system's answer. */
    fun resolve(system: Boolean, override: Boolean?): Boolean = override ?: system
}

/** True while the system asks for no animations (or a debug override says so). Updates live if either changes. */
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
    val override by ReduceMotionOverride.value.collectAsStateWithLifecycle()
    return ReduceMotionOverride.resolve(reduce, override)
}

/**
 * Motion that follows reduce motion (§6.7): with it on, things appear and leave with a plain cross-fade and
 * values snap instead of springing. Pure, so it's unit-tested.
 */
object Calm {
    /** [motion] normally; a plain fade with reduce motion. */
    fun enter(reduce: Boolean, motion: EnterTransition): EnterTransition = if (reduce) fadeIn() else motion

    /** [motion] normally; a plain fade with reduce motion. */
    fun exit(reduce: Boolean, motion: ExitTransition): ExitTransition = if (reduce) fadeOut() else motion

    /** [spec] normally; an instant change with reduce motion (used for pops and squishes on state changes). */
    fun <T> spec(reduce: Boolean, spec: AnimationSpec<T>): AnimationSpec<T> = if (reduce) snap() else spec
}
