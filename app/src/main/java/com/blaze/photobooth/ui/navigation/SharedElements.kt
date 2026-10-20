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
