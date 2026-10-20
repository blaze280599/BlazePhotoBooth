package com.blaze.photobooth.render

import android.graphics.Bitmap
import com.blaze.photobooth.model.SlotRect
import kotlin.math.pow
import kotlin.random.Random

/**
 * Code-generated stand-ins for frame artwork (never third-party art): flat colours, windows that are transparent or
 * solid boxes, hearts, dots, noise. Shared by the import, cut-out and screenshot tests.
 */
object SyntheticFrames {

    const val Pink = 0xFFF6B7C9.toInt()
    const val Lilac = 0xFFD9C8F5.toInt()
    const val White = 0xFFFFFFFF.toInt()
    const val Clear = 0x00000000
    const val Plum = 0xFF8C5BB8.toInt()

    data class Box(val x: Int, val y: Int, val w: Int, val h: Int) {
        val slot: SlotRect get() = SlotRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
        val centerIndex: (Int) -> Int get() = { stride -> (y + h / 2) * stride + x + w / 2 }
    }

    class Art(val w: Int, val h: Int, bg: Int = Pink) {
        val px = IntArray(w * h) { bg }

        /** Paints [box] with [color] wherever [inside] says so ([fx], [fy] run from -1 to 1 across the box). */
        fun fill(box: Box, color: Int, inside: (fx: Float, fy: Float) -> Boolean = { _, _ -> true }): Art {
            for (y in box.y until box.y + box.h) for (x in box.x until box.x + box.w) {
                val fx = (x - box.x + 0.5f) / box.w * 2f - 1f
                val fy = (y - box.y + 0.5f) / box.h * 2f - 1f
                if (inside(fx, fy)) px[y * w + x] = color
            }
            return this
        }

        /** JPEG-like grain on every opaque pixel. */
        fun noise(amplitude: Int, seed: Int = 7): Art {
            val random = Random(seed)
            for (i in px.indices) {
                val p = px[i]
                if (p ushr 24 == 0) continue
                fun ch(shift: Int) = (((p shr shift) and 0xFF) + random.nextInt(-amplitude, amplitude + 1)).coerceIn(0, 255)
                px[i] = (p and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            return this
        }

        /** Rounded outer corners made transparent (a frame cut out with a transparent outside). */
        fun roundOutside(r: Float): Art {
            for (y in 0 until h) for (x in 0 until w) {
                val dx = maxOf(r - x, x - (w - 1 - r), 0f)
                val dy = maxOf(r - y, y - (h - 1 - r), 0f)
                if (dx * dx + dy * dy > r * r) px[y * w + x] = Clear
            }
            return this
        }

        fun alphaAt(x: Int, y: Int) = px[y * w + x] ushr 24

        /** Needs Robolectric (native graphics). */
        fun toBitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
    }

    /** Four stacked windows, like a 2×6" strip. */
    fun strip1x4(w: Int, h: Int): List<Box> {
        val m = w / 15
        val cellH = (h - m * 6) / 4
        return List(4) { Box(m, m + it * (cellH + m), w - 2 * m, cellH) }
    }

    /** Four windows in two rows, like a 4×6" print. */
    fun grid2x2(w: Int, h: Int): List<Box> {
        val m = w / 20
        val cw = (w - 3 * m) / 2
        val ch = (h - 3 * m - h / 8) / 2
        return listOf(Box(m, m, cw, ch), Box(2 * m + cw, m, cw, ch), Box(m, 2 * m + ch, cw, ch), Box(2 * m + cw, 2 * m + ch, cw, ch))
    }

    /** A heart inside the box (for "rounded heart windows"). */
    val heart = { fx: Float, fy: Float ->
        val x = fx * 1.2f
        val y = -fy * 1.25f + 0.2f
        (x * x + y * y - 1).pow(3) - x * x * y.pow(3) <= 0f
    }

    val circle = { fx: Float, fy: Float -> fx * fx + fy * fy <= 1f }

    /** Rounded-corner window shape for a box of aspect [aspect] (w / h), corner radius [r] as a share of the height. */
    fun rounded(aspect: Float, r: Float = 0.18f) = { fx: Float, fy: Float ->
        val rx = r / aspect
        val dx = maxOf(kotlin.math.abs(fx) - (1 - 2 * rx), 0f) / (2 * rx)
        val dy = maxOf(kotlin.math.abs(fy) - (1 - 2 * r), 0f) / (2 * r)
        dx * dx + dy * dy <= 1f
    }

    /**
     * A cute strip in the style of her frames (all drawn here): lilac paper, four rounded transparent windows, polka
     * dots and a few hearts in the margins, a plum heart band at the foot. 1200 × 3600 like a 2×6" strip.
     */
    fun herStyleStrip(w: Int = 1200, h: Int = 3600): Pair<Art, List<Box>> {
        val art = Art(w, h, Lilac)
        val m = w / 14
        val foot = h / 9
        val cellH = (h - m * 5 - foot) / 4
        val windows = List(4) { Box(m, m + it * (cellH + m), w - 2 * m, cellH) }
        val random = Random(11)
        repeat(70) {
            val r = random.nextInt(w / 120, w / 50)
            val cx = random.nextInt(r, w - r)
            val cy = random.nextInt(r, h - r)
            art.fill(Box(cx - r, cy - r, 2 * r, 2 * r), White, circle)
        }
        windows.forEach { art.fill(it, Clear, rounded(it.w.toFloat() / it.h)) }
        for (i in 0 until 3) {
            val s = w / 9
            art.fill(Box(w / 4 + i * (w / 4) - s / 2, h - foot / 2 - s / 2, s, s), if (i == 1) Pink else Plum, heart)
        }
        // Little hearts peeking over the window edges, like stickers.
        art.fill(Box(w - m - w / 12, m - w / 30, w / 10, w / 10), Pink, heart)
        art.fill(Box(m - w / 30, windows[2].y - w / 20, w / 11, w / 11), Plum, heart)
        return art to windows
    }
}
