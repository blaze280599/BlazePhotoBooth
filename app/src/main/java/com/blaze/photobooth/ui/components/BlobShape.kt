package com.blaze.photobooth.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.MutableCubic
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.ui.theme.BlobPolygons
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme

/**
 * A soft blob that breathes between two graphics-shapes polygons (cookie ↔ flower by default)
 * while slowly spinning (README §4.1, §6.3, §7 A2). [content] sits upright in the middle.
 *
 * Performance (§7): the morph progress is read only while drawing, and the spin is a
 * `graphicsLayer` rotation, so neither recomposes. The morph path is reused every frame.
 * With reduce motion on (§6.7) the blob holds still as a cookie.
 */
@Composable
fun BlobShape(
    modifier: Modifier = Modifier,
    from: RoundedPolygon = BlobPolygons.Cookie,
    to: RoundedPolygon = BlobPolygons.Flower,
    brush: Brush = Brush.linearGradient(listOf(BoothTheme.extraColors.candy, MaterialTheme.colorScheme.tertiary)),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val morph = remember(from, to) { Morph(from, to) }
    val reduceMotion = LocalReduceMotion.current
    val progress: State<Float>
    val spin: State<Float>
    if (reduceMotion) {
        progress = remember { mutableFloatStateOf(0f) }
        spin = remember { mutableFloatStateOf(0f) }
    } else {
        val transition = rememberInfiniteTransition(label = "blob")
        progress = transition.animateFloat(0f, 1f, BoothMotion.float(BoothMotion.FloatMillis), label = "morph")
        spin = transition.animateFloat(0f, 360f, BoothMotion.floatSpin(), label = "spin")
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = spin.value }
                .drawWithCache {
                    val path = MorphPath(morph, radius = size.minDimension / 2f)
                    onDrawBehind {
                        path.update(progress.value)
                        translate(size.width / 2f, size.height / 2f) { drawPath(path.path, brush) }
                    }
                },
        )
        content()
    }
}

/** A reusable path that follows a [Morph] without allocating per frame. */
private class MorphPath(private val morph: Morph, private val radius: Float) {
    private val androidPath = android.graphics.Path()
    val path = androidPath.asComposePath()
    private val cubic = MutableCubic()
    private var started = false
    private val append: (MutableCubic) -> Unit = { c ->
        if (!started) {
            androidPath.moveTo(c.anchor0X * radius, c.anchor0Y * radius)
            started = true
        }
        androidPath.cubicTo(
            c.control0X * radius, c.control0Y * radius,
            c.control1X * radius, c.control1Y * radius,
            c.anchor1X * radius, c.anchor1Y * radius,
        )
    }

    fun update(progress: Float) {
        androidPath.rewind()
        started = false
        morph.forEachCubic(progress, cubic, append)
        androidPath.close()
    }
}

/** Home logo (§4.1): the morphing blob with a camera glyph inside. Decorative: the wordmark under it names the app. */
@Composable
fun BlobLogo(modifier: Modifier = Modifier, size: Dp = 184.dp) {
    BlobShape(modifier.size(size)) {
        Icon(
            imageVector = Icons.Rounded.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.36f),
        )
    }
}

@Preview(name = "Blob shapes")
@Composable
private fun BlobShapePreview() {
    BoothTheme(darkTheme = false) { BlobShapeSamples() }
}

/** Shared by the preview and the screenshot test: the logo plus the cookie, flower and soft star at rest. */
@Composable
internal fun BlobShapeSamples() {
    // Full width: Robolectric renders a wrap-content root holding a rotated layer as blank.
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BlobLogo(size = 160.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val extra = BoothTheme.extraColors
                val scheme = MaterialTheme.colorScheme
                BlobShape(Modifier.size(88.dp), from = BlobPolygons.Cookie, to = BlobPolygons.Flower, brush = Brush.linearGradient(listOf(scheme.secondary, extra.mint)))
                BlobShape(Modifier.size(88.dp), from = BlobPolygons.Flower, to = BlobPolygons.Cookie, brush = Brush.linearGradient(listOf(scheme.tertiary, extra.sky)))
                BlobShape(Modifier.size(88.dp), from = BlobPolygons.SoftStar, to = BlobPolygons.Cookie, brush = Brush.linearGradient(listOf(scheme.primaryContainer, scheme.primary)))
            }
        }
    }
}
