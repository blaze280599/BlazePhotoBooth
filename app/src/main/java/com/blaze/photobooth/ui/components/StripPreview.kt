package com.blaze.photobooth.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.graphics.withScale
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.StripRenderer

/**
 * The live strip, drawn by the real [StripRenderer] (README §10): the native canvas is scaled from
 * strip pixels to the view, then the export code path draws into it. [photos] should be preview-sized
 * (≤ 600 px); the result matches the exported file in composition, pixel for pixel at scale.
 *
 * Sized to the strip's own aspect ratio, so give it a height (or width) constraint.
 */
@Composable
fun StripPreview(
    spec: StripSpec,
    modifier: Modifier = Modifier,
    photos: List<Bitmap?> = emptyList(),
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val renderer = remember(context) { StripRenderer.get(context) }
    val canvasSize = spec.canvasSize
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Canvas(
        modifier
            .aspectRatio(canvasSize.width.toFloat() / canvasSize.height, matchHeightConstraintsFirst = true)
            .then(semantics),
    ) {
        val scale = size.width / canvasSize.width
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.withScale(scale, scale) { renderer.draw(this, spec, photos) }
        }
    }
}
