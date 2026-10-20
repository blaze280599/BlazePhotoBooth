package com.blaze.photobooth.camera

import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.render.BitmapOps
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How one camera frame becomes a stored shot (README §9.4): rotate upright, mirror if it's a mirrored
 * selfie, centre-crop to the slot's aspect (the same crop the viewfinder showed), then shrink so the long
 * edge is at most [ShotGeometry.MaxEdge].
 *
 * [crop] is in upright pixels (after rotation and mirroring). [affine] maps source pixels straight to
 * output pixels, so the whole thing is one bitmap draw.
 */
data class ShotPlan(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int,
    val mirror: Boolean,
    val uprightWidth: Int,
    val uprightHeight: Int,
    val crop: SlotRect,
    val outWidth: Int,
    val outHeight: Int,
) {
    /**
     * Row-major 2×3 affine `[a, b, c, d, e, f]`: `x' = a·x + b·y + c`, `y' = d·x + e·y + f`, from source
     * pixels to output pixels. Feed it to `android.graphics.Matrix.setValues` with `0, 0, 1` appended.
     */
    val affine: FloatArray
        get() {
            var m = Affine.rotation(rotationDegrees, sourceWidth, sourceHeight)
            if (mirror) m = Affine.then(m, floatArrayOf(-1f, 0f, uprightWidth.toFloat(), 0f, 1f, 0f))
            m = Affine.then(m, floatArrayOf(1f, 0f, -crop.x, 0f, 1f, -crop.y))
            return Affine.then(m, floatArrayOf(outWidth / crop.w, 0f, 0f, 0f, outHeight / crop.h, 0f))
        }

    /** Where the source point ([x], [y]) lands in the output. */
    fun map(x: Float, y: Float): Pair<Float, Float> {
        val a = affine
        return (a[0] * x + a[1] * y + a[2]) to (a[3] * x + a[4] * y + a[5])
    }
}

object ShotGeometry {

    /** Stored shots are at most this many pixels on their long edge (§9.4). */
    const val MaxEdge = 2400

    /** Only front-camera selfies are mirrored, and only when "Mirror selfies" is on (§5.4, §13). */
    fun shouldMirror(lens: Lens, mirrorSetting: Boolean): Boolean = lens == Lens.Front && mirrorSetting

    /** CameraX reports 0/90/180/270; anything else is snapped to the nearest quarter turn. */
    fun normalizeRotation(degrees: Int): Int = (((degrees / 90f).roundToInt() * 90) % 360 + 360) % 360

    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        rotationDegrees: Int,
        mirror: Boolean,
        aspect: Float,
        maxEdge: Int = MaxEdge,
    ): ShotPlan {
        require(sourceWidth > 0 && sourceHeight > 0 && aspect > 0f) { "Bad shot: ${sourceWidth}x$sourceHeight @ $aspect" }
        val rotation = normalizeRotation(rotationDegrees)
        val quarter = rotation % 180 != 0
        val uprightW = if (quarter) sourceHeight else sourceWidth
        val uprightH = if (quarter) sourceWidth else sourceHeight
        val crop = BitmapOps.coverSource(uprightW, uprightH, aspect, 1f)
        val scale = min(1f, maxEdge / max(crop.w, crop.h))
        return ShotPlan(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            rotationDegrees = rotation,
            mirror = mirror,
            uprightWidth = uprightW,
            uprightHeight = uprightH,
            crop = crop,
            outWidth = (crop.w * scale).roundToInt().coerceAtLeast(1),
            outHeight = (crop.h * scale).roundToInt().coerceAtLeast(1),
        )
    }
}

/** Tiny 2×3 affine helpers (row-major `[a, b, c, d, e, f]`). */
internal object Affine {

    /** Clockwise rotation by a quarter-turn multiple, shifted so the rotated image starts at (0, 0). */
    fun rotation(degrees: Int, width: Int, height: Int): FloatArray {
        val w = width.toFloat()
        val h = height.toFloat()
        return when (degrees) {
            90 -> floatArrayOf(0f, -1f, h, 1f, 0f, 0f)
            180 -> floatArrayOf(-1f, 0f, w, 0f, -1f, h)
            270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, w)
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        }
    }

    /** First [first], then [next]. */
    fun then(first: FloatArray, next: FloatArray): FloatArray {
        val (a, b, c, d, e) = next
        val f = next[5]
        return floatArrayOf(
            a * first[0] + b * first[3],
            a * first[1] + b * first[4],
            a * first[2] + b * first[5] + c,
            d * first[0] + e * first[3],
            d * first[1] + e * first[4],
            d * first[2] + e * first[5] + f,
        )
    }
}
