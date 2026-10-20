package com.blaze.photobooth.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star

// README §6.3: small 12, medium 20, large 28, sheets 32, buttons fully rounded.

val BoothShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Buttons and chips. */
val PillShape = CircleShape

/** Top corners of bottom sheets. */
val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * Blob polygons from graphics-shapes (§6.3), centred on (0, 0) with radius 1,
 * so any two can be fed to a [androidx.graphics.shapes.Morph].
 */
object BlobPolygons {
    /** Cookie with 9 rounded scallops. */
    val Cookie: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.82f,
        rounding = CornerRounding(radius = 0.32f, smoothing = 0.6f),
        innerRounding = CornerRounding(radius = 0.32f, smoothing = 0.6f),
    )

    /** Six soft petals. */
    val Flower: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        innerRadius = 0.62f,
        rounding = CornerRounding(radius = 0.42f, smoothing = 0.8f),
        innerRounding = CornerRounding(radius = 0.18f, smoothing = 0.4f),
    )

    /** Five-point star with pillowy corners. */
    val SoftStar: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 5,
        innerRadius = 0.58f,
        rounding = CornerRounding(radius = 0.28f, smoothing = 0.5f),
        innerRounding = CornerRounding(radius = 0.12f),
    )
}
