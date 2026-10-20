package com.blaze.photobooth.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

/**
 * A little pastel scene drawn in code (sky, sun, cloud, hills, a flower and a heart) used as the
 * "sample photo" on the Filter tab (§4.2). Drawn here so the app ships no photos of people.
 * Every hue a filter changes is in it: blue sky, warm sun, green grass, pink petals, white cloud.
 */
object SampleArt {

    fun filterSample(size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val c = Canvas(bitmap)
        val s = size.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, 0f, s, 0xFF8EC9FF.toInt(), 0xFFFFDDE8.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        p.shader = null

        p.color = 0xFFFFB36B.toInt()
        c.drawCircle(s * 0.72f, s * 0.3f, s * 0.14f, p)

        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(s * 0.24f, s * 0.3f, s * 0.08f, p)
        c.drawCircle(s * 0.34f, s * 0.26f, s * 0.1f, p)
        c.drawCircle(s * 0.44f, s * 0.31f, s * 0.07f, p)
        c.drawRoundRect(s * 0.17f, s * 0.3f, s * 0.5f, s * 0.38f, s * 0.04f, s * 0.04f, p)

        p.color = 0xFF9ED98F.toInt()
        c.drawOval(-s * 0.3f, s * 0.6f, s * 0.75f, s * 1.3f, p)
        p.color = 0xFF6FBF73.toInt()
        c.drawOval(s * 0.35f, s * 0.66f, s * 1.4f, s * 1.35f, p)

        // Flower: five pink petals around a butter centre.
        p.color = 0xFFFF7FA8.toInt()
        val fx = s * 0.3f
        val fy = s * 0.74f
        for (i in 0 until 5) {
            val a = Math.toRadians(i * 72.0 - 90.0)
            c.drawCircle(fx + (Math.cos(a) * s * 0.055).toFloat(), fy + (Math.sin(a) * s * 0.055).toFloat(), s * 0.045f, p)
        }
        p.color = 0xFFFFD86B.toInt()
        c.drawCircle(fx, fy, s * 0.035f, p)

        p.color = 0xFFE84A7F.toInt()
        val heart = Stickers.path(StickerKind.Heart)
        c.withTranslation(s * 0.7f, s * 0.78f) {
            rotate(12f)
            scale(s * 0.16f, s * 0.16f)
            drawPath(heart, p)
        }
        return bitmap
    }
}
