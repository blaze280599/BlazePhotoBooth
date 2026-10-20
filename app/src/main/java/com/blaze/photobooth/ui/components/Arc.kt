package com.blaze.photobooth.ui.components

import androidx.compose.ui.geometry.Offset

/**
 * The little hop a photo makes on its way into a slot (§7 A6 Capture → tray, A8 Pick tray → strip): a quadratic
 * Bézier from [from] to [to] whose control point sits [lift] px above the higher of the two, halfway across.
 * Pure (and allocation-free: [Offset] is a value class), so it's safe in a draw or layer block and unit-tested.
 */
object Arc {
    fun point(from: Offset, to: Offset, lift: Float, t: Float): Offset {
        val controlX = (from.x + to.x) / 2f
        val controlY = minOf(from.y, to.y) - lift
        val u = 1f - t
        return Offset(
            u * u * from.x + 2 * u * t * controlX + t * t * to.x,
            u * u * from.y + 2 * u * t * controlY + t * t * to.y,
        )
    }
}
