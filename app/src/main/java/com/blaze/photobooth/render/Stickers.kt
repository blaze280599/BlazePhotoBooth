package com.blaze.photobooth.render

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector sticker art (README §5.2): hearts, clouds, clovers, stars, moons, daisies, petals and
 * sparkles. Each path fits a unit box centred on (0, 0), so a sticker is drawn by translating,
 * rotating and scaling the canvas. No bitmap assets.
 */
internal object Stickers {

    private val paths: Map<StickerKind, Path> = StickerKind.entries.associateWith { build(it) }

    /** The unit-box outline of [kind] (shared with placeholders and the sample art). */
    fun path(kind: StickerKind): Path = paths.getValue(kind)

    private val daisyCenter = Path().apply { addCircle(0f, 0f, 0.15f, Path.Direction.CW) }

    /**
     * Draws [sticker] in [color]. Most stickers get a white die-cut border like real paper stickers;
     * sparkles are drawn bare so they read as glints.
     */
    fun draw(canvas: Canvas, sticker: Sticker, color: Int, fill: Paint, border: Paint) {
        val path = paths.getValue(sticker.kind)
        canvas.withTranslation(sticker.cx, sticker.cy) {
            rotate(sticker.rotation)
            scale(sticker.size, sticker.size)
            if (sticker.kind != StickerKind.Sparkle) {
                border.strokeWidth = BorderWidth
                drawPath(path, border)
            }
            fill.color = color
            drawPath(path, fill)
            if (sticker.kind == StickerKind.Daisy) {
                fill.color = DaisyYellow
                drawPath(daisyCenter, fill)
            }
        }
    }

    private fun build(kind: StickerKind): Path = when (kind) {
        StickerKind.Heart -> heart()
        StickerKind.Cloud -> cloud()
        StickerKind.Clover -> clover()
        StickerKind.Star -> star(points = 5, outer = 0.5f, inner = 0.23f)
        StickerKind.Moon -> moon()
        StickerKind.Daisy -> daisy()
        StickerKind.Petal -> petal()
        StickerKind.Sparkle -> sparkle()
    }

    private fun heart() = Path().apply {
        moveTo(0f, 0.42f)
        cubicTo(-0.62f, 0.02f, -0.48f, -0.52f, 0f, -0.22f)
        cubicTo(0.48f, -0.52f, 0.62f, 0.02f, 0f, 0.42f)
        close()
    }

    /** Overlapping puffs merged into one outline, so the die-cut border hugs the whole cloud. */
    private fun cloud(): Path {
        val parts = listOf(
            Path().apply { addCircle(-0.22f, 0.06f, 0.2f, Path.Direction.CW) },
            Path().apply { addCircle(0.02f, -0.08f, 0.27f, Path.Direction.CW) },
            Path().apply { addCircle(0.26f, 0.06f, 0.19f, Path.Direction.CW) },
            Path().apply { addRoundRect(RectF(-0.42f, 0.0f, 0.45f, 0.26f), 0.13f, 0.13f, Path.Direction.CW) },
        )
        return Path().apply { parts.forEach { op(it, Path.Op.UNION) } }
    }

    private fun clover(): Path {
        val result = Path()
        val leaf = Path().apply {
            // A heart leaf pointing at the centre.
            moveTo(0f, 0f)
            cubicTo(-0.32f, -0.12f, -0.3f, -0.46f, 0f, -0.34f)
            cubicTo(0.3f, -0.46f, 0.32f, -0.12f, 0f, 0f)
            close()
        }
        val m = Matrix()
        for (i in 0 until 4) {
            m.setRotate(45f + i * 90f)
            val rotated = Path(leaf).apply { transform(m) }
            result.op(rotated, Path.Op.UNION)
        }
        val stem = Path().apply { addRoundRect(RectF(-0.035f, 0.05f, 0.035f, 0.48f), 0.03f, 0.03f, Path.Direction.CW) }
        m.setRotate(-20f)
        stem.transform(m)
        result.op(stem, Path.Op.UNION)
        return result
    }

    private fun star(points: Int, outer: Float, inner: Float) = Path().apply {
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / points
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun moon(): Path {
        val disc = Path().apply { addCircle(0f, 0f, 0.44f, Path.Direction.CW) }
        val bite = Path().apply { addCircle(0.2f, -0.12f, 0.36f, Path.Direction.CW) }
        return Path().apply { op(disc, bite, Path.Op.DIFFERENCE) }
    }

    private fun daisy(): Path {
        val result = Path()
        val m = Matrix()
        for (i in 0 until 8) {
            val petal = Path().apply { addOval(RectF(-0.1f, -0.5f, 0.1f, -0.06f), Path.Direction.CW) }
            m.setRotate(i * 45f)
            petal.transform(m)
            result.op(petal, Path.Op.UNION)
        }
        result.op(Path().apply { addCircle(0f, 0f, 0.17f, Path.Direction.CW) }, Path.Op.UNION)
        return result
    }

    private fun petal() = Path().apply {
        moveTo(0f, 0.48f)
        cubicTo(-0.5f, 0.22f, -0.4f, -0.42f, -0.1f, -0.44f)
        lineTo(0f, -0.32f)
        lineTo(0.1f, -0.44f)
        cubicTo(0.4f, -0.42f, 0.5f, 0.22f, 0f, 0.48f)
        close()
    }

    private fun sparkle() = Path().apply {
        moveTo(0f, -0.5f)
        quadTo(0.07f, -0.07f, 0.5f, 0f)
        quadTo(0.07f, 0.07f, 0f, 0.5f)
        quadTo(-0.07f, 0.07f, -0.5f, 0f)
        quadTo(-0.07f, -0.07f, 0f, -0.5f)
        close()
    }

    /** Border width in unit-box terms (a sixth of a 1-unit sticker, split across both sides of the edge). */
    private const val BorderWidth = 0.16f
    private const val DaisyYellow = 0xFFFFC93C.toInt()
}
