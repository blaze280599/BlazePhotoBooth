package com.blaze.photobooth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** What a confetti piece looks like (§4.5: hearts, stars, pastel rectangles). */
enum class ConfettiShape { Heart, Star, Strip, Dot }

/**
 * One confetti piece. Positions and speeds are in **canvas widths** (and widths per second), so the burst looks
 * the same on any screen. y grows downward.
 */
@Immutable
data class ConfettiParticle(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val spin: Float,
    val rotation0: Float,
    val shape: ConfettiShape,
    val colorIndex: Int,
)

/**
 * The confetti burst after a strip prints (§7 A9): ≈ 80 pieces fired upward from one point, then pulled down by
 * gravity and slowed by air drag, fading out over the last half second of [DurationSeconds]. The motion has a
 * closed form, so any moment can be drawn (and screenshot-tested) without stepping a simulation.
 */
object ConfettiPhysics {
    const val Count = 80
    const val DurationSeconds = 2.5f

    /** Gravity, in widths/s². */
    const val Gravity = 2.4f

    /** Linear air drag, per second. */
    const val Drag = 1.6f
    private const val FadeSeconds = 0.5f

    /** A deterministic burst from ([originX], [originY]), in widths. */
    fun burst(originX: Float, originY: Float, seed: Int = 0x5EED, count: Int = Count): List<ConfettiParticle> {
        val rng = Random(seed)
        return List(count) {
            // Upward cone, wider than it is tall, so pieces rain over the whole screen.
            val angle = (-90f + rng.nextFloat() * 150f - 75f) * PI.toFloat() / 180f
            val speed = 1.1f + rng.nextFloat() * 1.5f
            ConfettiParticle(
                x0 = originX,
                y0 = originY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                size = 0.022f + rng.nextFloat() * 0.022f,
                spin = (rng.nextFloat() - 0.5f) * 720f,
                rotation0 = rng.nextFloat() * 360f,
                shape = ConfettiShape.entries[rng.nextInt(ConfettiShape.entries.size)],
                colorIndex = rng.nextInt(1024),
            )
        }
    }

    /** Where [p] is [t] seconds after the burst, in widths. */
    fun position(p: ConfettiParticle, t: Float): Offset {
        val decay = (1f - exp(-Drag * t)) / Drag
        val terminal = Gravity / Drag
        return Offset(
            p.x0 + p.vx * decay,
            p.y0 + terminal * t + (p.vy - terminal) * decay,
        )
    }

    fun alpha(t: Float): Float = ((DurationSeconds - t) / FadeSeconds).coerceIn(0f, 1f)

    fun rotation(p: ConfettiParticle, t: Float): Float = p.rotation0 + p.spin * t
}

/**
 * Draws [particles] [seconds] after the burst, all in one Canvas (§7 performance rules). Nothing is drawn
 * before 0 or after the burst ends. Decorative, so it's hidden from TalkBack.
 */
@Composable
fun Confetti(particles: List<ConfettiParticle>, seconds: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val heart = remember { heartPath() }
    val star = remember { starPath() }
    Canvas(modifier.clearAndSetSemantics {}) {
        if (seconds <= 0f || seconds >= ConfettiPhysics.DurationSeconds) return@Canvas
        val alpha = ConfettiPhysics.alpha(seconds)
        val w = size.width
        for (p in particles) {
            val pos = ConfettiPhysics.position(p, seconds) * w
            val s = p.size * w
            if (pos.y > size.height + s || pos.x < -s || pos.x > w + s) continue
            val color = colors[p.colorIndex % colors.size].copy(alpha = alpha)
            translate(pos.x, pos.y) {
                rotate(ConfettiPhysics.rotation(p, seconds), Offset.Zero) {
                    when (p.shape) {
                        ConfettiShape.Strip -> drawRect(color, Offset(-s / 2, -s / 4), Size(s, s / 2))
                        ConfettiShape.Dot -> drawCircle(color, s / 2.6f, Offset.Zero)
                        ConfettiShape.Heart -> scale(s, s, Offset.Zero) { drawPath(heart, color) }
                        ConfettiShape.Star -> scale(s, s, Offset.Zero) { drawPath(star, color) }
                    }
                }
            }
        }
    }
}

/** A unit heart centred on the origin. */
private fun heartPath() = Path().apply {
    moveTo(0f, 0.38f)
    cubicTo(-0.55f, 0.02f, -0.52f, -0.42f, -0.22f, -0.42f)
    cubicTo(-0.08f, -0.42f, 0f, -0.3f, 0f, -0.2f)
    cubicTo(0f, -0.3f, 0.08f, -0.42f, 0.22f, -0.42f)
    cubicTo(0.52f, -0.42f, 0.55f, 0.02f, 0f, 0.38f)
    close()
}

/** A unit five-pointed star centred on the origin. */
private fun starPath() = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 0.5f else 0.22f
        val a = (i * 36f - 90f) * PI.toFloat() / 180f
        if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
    }
    close()
}
