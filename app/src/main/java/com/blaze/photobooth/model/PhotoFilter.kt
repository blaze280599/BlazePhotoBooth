package com.blaze.photobooth.model

/**
 * The six booth filters (README §5.3). Each is a row-major 4×5 colour matrix, the layout both
 * `android.graphics.ColorMatrix` and Compose's `ColorMatrix` use: offsets are in 0–255 units.
 * The same matrix drives the live viewfinder, thumbnails and the exported strip.
 */
enum class PhotoFilter(val id: String, val matrix: FloatArray) {
    /** Identity. */
    Original("original", ColorMatrices.identity()),

    /** Mono 흑백: high-contrast black and white, the classic Korean booth look. */
    Mono("mono", ColorMatrices.concat(ColorMatrices.contrast(1.24f), ColorMatrices.saturation(0f))),

    /** Milky 뽀샤시: brighter, lifted shadows, lower contrast, a faint pink warmth ("glow skin"). */
    Milky(
        "milky",
        ColorMatrices.concat(
            ColorMatrices.tint(r = 1.02f, g = 0.99f, b = 1.0f, rOffset = 10f, gOffset = 2f, bOffset = 6f),
            ColorMatrices.liftAndFade(scale = 0.80f, lift = 46f),
            ColorMatrices.saturation(0.92f),
        ),
    ),

    /** Peach: warm, slightly saturated. */
    Peach(
        "peach",
        ColorMatrices.concat(
            ColorMatrices.tint(r = 1.06f, g = 1.0f, b = 0.88f, rOffset = 10f, gOffset = 3f, bOffset = -4f),
            ColorMatrices.saturation(1.18f),
        ),
    ),

    /** Soda: a cool blue tint, crisp. */
    Cool(
        "cool",
        ColorMatrices.concat(
            ColorMatrices.tint(r = 0.92f, g = 1.0f, b = 1.08f, rOffset = -6f, gOffset = 2f, bOffset = 14f),
            ColorMatrices.contrast(1.10f),
        ),
    ),

    /** Film: faded blacks, warm highlights, slightly green shadows. */
    Film(
        "film",
        ColorMatrices.concat(
            ColorMatrices.tint(r = 0.90f, g = 0.86f, b = 0.76f, rOffset = 16f, gOffset = 24f, bOffset = 20f),
            ColorMatrices.saturation(0.86f),
        ),
    ),
    ;

    companion object {
        val Default = Original

        fun fromId(id: String?): PhotoFilter? = entries.firstOrNull { it.id == id }
    }
}

/** Pure-Kotlin builders for 4×5 colour matrices (row-major, 20 floats, offsets in 0–255). */
object ColorMatrices {

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Rec. 601 luma weights, the same ones Android's `ColorMatrix.setSaturation` uses. */
    fun saturation(s: Float): FloatArray {
        val r = 0.213f * (1 - s)
        val g = 0.715f * (1 - s)
        val b = 0.072f * (1 - s)
        return floatArrayOf(
            r + s, g, b, 0f, 0f,
            r, g + s, b, 0f, 0f,
            r, g, b + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Contrast around mid-grey (128). */
    fun contrast(c: Float): FloatArray {
        val t = 128f * (1 - c)
        return floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Lower contrast by [scale] and lift every channel by [lift]: brighter shadows, softer image. */
    fun liftAndFade(scale: Float, lift: Float): FloatArray = floatArrayOf(
        scale, 0f, 0f, 0f, lift,
        0f, scale, 0f, 0f, lift,
        0f, 0f, scale, 0f, lift,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Per-channel gain and offset. */
    fun tint(r: Float, g: Float, b: Float, rOffset: Float = 0f, gOffset: Float = 0f, bOffset: Float = 0f): FloatArray =
        floatArrayOf(
            r, 0f, 0f, 0f, rOffset,
            0f, g, 0f, 0f, gOffset,
            0f, 0f, b, 0f, bOffset,
            0f, 0f, 0f, 1f, 0f,
        )

    /**
     * Matrices applied left to right: `concat(a, b)` first applies [a], then [b] to a's result
     * (like Android's `ColorMatrix.postConcat`).
     */
    fun concat(vararg matrices: FloatArray): FloatArray = matrices.reduce { acc, next -> multiply(next, acc) }

    /** `a ∘ b`: apply [b] first, then [a]. Treats each matrix as 5×5 with an implicit [0 0 0 0 1] row. */
    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = if (col == 4) a[row * 5 + 4] else 0f
                for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = sum
            }
        }
        return out
    }

    /** Applies [m] to one 0–255 RGB colour, clamped. Handy for tests and swatch maths. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float, a: Float = 255f): FloatArray = FloatArray(4) { row ->
        (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a + m[row * 5 + 4]).coerceIn(0f, 255f)
    }
}
