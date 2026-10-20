package com.blaze.photobooth.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hearts ♡, stars ☆ and sparkles ✦ drifting slowly upward at three parallax depths (README §4.1, §7 A2).
 *
 * Performance (§7): all particles live in one [Canvas]. The clock is a float state that only the
 * draw lambda reads, so each frame is a redraw with no recomposition. Glyph paths are built once,
 * and the draw loop allocates nothing.
 *
 * With reduce motion on (§6.7) the clock never starts and the particles hold still.
 */
@Composable
fun FloatingParticles(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    count: Int = DefaultParticleCount,
    seed: Int = 7,
) {
    val particles = remember(count, seed) { seededParticles(count, seed) }
    val glyphs = remember { ParticleGlyphs() }
    val clock = remember { mutableFloatStateOf(0f) }
    val reduceMotion = LocalReduceMotion.current

    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) {
                withInfiniteAnimationFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
            }
        }
    }

    Canvas(modifier) {
        val seconds = clock.floatValue
        val unit = 1.dp.toPx()
        for (i in particles.indices) {
            val p = particles[i]
            val depth = Depths[p.depth]
            val travel = size.height + depth.sizeDp * unit * 4f
            // Rise from the bottom, wrap around at the top; sway sideways on a slow sine.
            val rise = (p.startY * travel + seconds * depth.speedDpPerSec * unit) % travel
            val y = size.height + depth.sizeDp * unit * 2f - rise
            val sway = sin(seconds * p.swaySpeed + p.phase) * p.swayDp * unit
            val x = p.x * size.width + sway
            val spin = sin(seconds * p.swaySpeed * 0.7f + p.phase) * 18f
            val color = colors[p.colorIndex % colors.size]
            drawGlyph(glyphs.forKind(p.kind), Offset(x, y), depth.sizeDp * unit * p.scale, spin, color, depth.alpha)
        }
    }
}

private fun DrawScope.drawGlyph(path: Path, center: Offset, size: Float, degrees: Float, color: Color, alpha: Float) {
    translate(center.x, center.y) {
        rotate(degrees, pivot = Offset.Zero) {
            scale(size, pivot = Offset.Zero) {
                drawPath(path, color = color, alpha = alpha)
            }
        }
    }
}

const val DefaultParticleCount = 14

private enum class ParticleKind { Heart, Star, Sparkle }

/** One parallax layer: far particles are small, faint and slow. */
private class Depth(val sizeDp: Float, val speedDpPerSec: Float, val alpha: Float)

private val Depths = arrayOf(
    Depth(sizeDp = 9f, speedDpPerSec = 9f, alpha = 0.35f),
    Depth(sizeDp = 14f, speedDpPerSec = 15f, alpha = 0.55f),
    Depth(sizeDp = 20f, speedDpPerSec = 24f, alpha = 0.8f),
)

@Immutable
private class Particle(
    val kind: ParticleKind,
    val depth: Int,
    val x: Float,
    val startY: Float,
    val phase: Float,
    val swaySpeed: Float,
    val swayDp: Float,
    val scale: Float,
    val colorIndex: Int,
)

/** Deterministic layout (same seed, same sky), spread evenly so nothing clumps on the first frame. */
private fun seededParticles(count: Int, seed: Int): List<Particle> {
    val random = Random(seed)
    val kinds = ParticleKind.entries
    return List(count) { i ->
        Particle(
            kind = kinds[i % kinds.size],
            depth = i % Depths.size,
            // Jittered columns keep particles from stacking on top of each other.
            x = ((i * 0.618f) % 1f) * 0.9f + 0.05f + random.nextFloat() * 0.04f,
            startY = (i + random.nextFloat() * 0.6f) / count,
            phase = random.nextFloat() * 2f * PI.toFloat(),
            swaySpeed = 0.35f + random.nextFloat() * 0.35f,
            swayDp = 8f + random.nextFloat() * 14f,
            scale = 0.85f + random.nextFloat() * 0.4f,
            colorIndex = random.nextInt(0, 1000),
        )
    }
}

/** Unit-sized glyph paths (about −1…1), built once and reused every frame. */
private class ParticleGlyphs {
    val heart: Path = Path().apply {
        moveTo(0f, 0.9f)
        cubicTo(-1.25f, 0.05f, -1.05f, -1.0f, -0.48f, -0.95f)
        cubicTo(-0.18f, -0.93f, -0.02f, -0.72f, 0f, -0.5f)
        cubicTo(0.02f, -0.72f, 0.18f, -0.93f, 0.48f, -0.95f)
        cubicTo(1.05f, -1.0f, 1.25f, 0.05f, 0f, 0.9f)
        close()
    }
    val star: Path = RoundedPolygon.star(
        numVerticesPerRadius = 5,
        innerRadius = 0.5f,
        rounding = CornerRounding(radius = 0.18f),
        innerRounding = CornerRounding(radius = 0.08f),
    ).toPath().asComposePath()
    val sparkle: Path = Path().apply {
        moveTo(0f, -1f)
        quadraticTo(0.12f, -0.12f, 1f, 0f)
        quadraticTo(0.12f, 0.12f, 0f, 1f)
        quadraticTo(-0.12f, 0.12f, -1f, 0f)
        quadraticTo(-0.12f, -0.12f, 0f, -1f)
        close()
    }

    fun forKind(kind: ParticleKind): Path = when (kind) {
        ParticleKind.Heart -> heart
        ParticleKind.Star -> star
        ParticleKind.Sparkle -> sparkle
    }
}

/** Palette for particles on the Home gradient: strawberry, butter, lavender, mint and sky. */
@Composable
fun boothParticleColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val extra = BoothTheme.extraColors
    return remember(scheme, extra) { listOf(scheme.primary, scheme.secondary, scheme.tertiary, extra.mint, extra.sky) }
}

@Preview(name = "Particles", widthDp = 360, heightDp = 480)
@Composable
private fun FloatingParticlesPreview() {
    BoothTheme(darkTheme = false) {
        Box(
            Modifier
                .size(360.dp, 480.dp)
                .background(Brush.verticalGradient(listOf(BoothTheme.extraColors.gradientTop, BoothTheme.extraColors.gradientBottom))),
        ) {
            FloatingParticles(colors = boothParticleColors(), modifier = Modifier.fillMaxSize(), count = 24)
        }
    }
}
