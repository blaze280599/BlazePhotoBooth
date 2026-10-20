package com.blaze.photobooth.ui.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.blaze.photobooth.core.LocalReduceMotion

/** Keys of the shared elements that travel between screens (§7). */
object SharedKeys {
    /** §7 A7: Capture's shot tray grows into Pick's tray grid. */
    const val ShotTray = "shot-tray"

    /** §7 A10: a saved strip's picture, from its Album card into Detail's hero. */
    fun strip(sessionId: String) = "strip-$sessionId"
}

/**
 * A shared-element hero (§7 A10): the same picture on two screens, whose bounds glide from one to the other while
 * the screens slide. Both ends must show the same picture at the same aspect ratio. Like [sharedContainer], it does
 * nothing outside [BoothNavHost] or with reduce motion on.
 */
@Composable
fun Modifier.sharedHero(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalReduceMotion.current) return this
    val animated = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedHero.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
        )
    }
}

/**
 * A container transform between two screens (§7 A7): the element with the same [key] on the screen being left
 * morphs into this one, cross-fading its contents. Outside the app's [BoothNavHost] (previews, screenshot tests)
 * or with reduce motion on, it does nothing.
 */
@Composable
fun Modifier.sharedContainer(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalReduceMotion.current) return this
    val animated = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp)),
        )
    }
}
