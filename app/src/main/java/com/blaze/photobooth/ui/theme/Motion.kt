package com.blaze.photobooth.ui.theme

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens, README §7. Everything that moves in Blaze Booth uses one of these,
 * so the whole app shares one physical "feel".
 */
object BoothMotion {

    /** Stickers, countdown pop, selected cards: lively overshoot. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 400f)

    /** Presses, toggles, slot fills: quick with a hint of give. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 800f)

    /** Screen-level layout and sheets: calm, no overshoot. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 200f)

    /** Release after a squish (§7 A3): springs back past 1.0 before settling. */
    fun <T> release(): SpringSpec<T> = spring(dampingRatio = 0.38f, stiffness = 500f)

    /** Ambient drift: an infinite ease-in-out back-and-forth, 6–12 s per leg. */
    fun <T> float(durationMillis: Int = FloatMillis): InfiniteRepeatableSpec<T> {
        require(durationMillis in FloatMinMillis..FloatMaxMillis) { "Float runs 6–12 s, got $durationMillis ms" }
        return infiniteRepeatable(tween(durationMillis, easing = EaseInOutSine), RepeatMode.Reverse)
    }

    /**
     * Endless spin for the Float family (blob rotation). Linear so the turn never pauses,
     * one revolution per [durationMillis].
     */
    fun <T> floatSpin(durationMillis: Int = SpinMillis): InfiniteRepeatableSpec<T> =
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart)

    /** The selected-card ✓ sticker's endless little wobble (§7 A4): a quick ease-in-out rock. */
    fun <T> wobble(): InfiniteRepeatableSpec<T> =
        infiniteRepeatable(tween(WobbleMillis, easing = EaseInOutSine), RepeatMode.Reverse)

    const val WobbleMillis = 900

    const val FloatMinMillis = 6_000
    const val FloatMaxMillis = 12_000
    const val FloatMillis = 8_000
    const val SpinMillis = 24_000

    /** One full sine cycle of the Home wordmark's letter bob (§7 A2), linear so it loops seamlessly. */
    const val BobMillis = 2_600

    /** Squish target scale for pressed buttons (§7 A3). */
    const val PressedScale = 0.92f

    /** Nav slide distance as a fraction of the screen width (§7 A1). */
    const val NavSlideFraction = 0.12f
}
