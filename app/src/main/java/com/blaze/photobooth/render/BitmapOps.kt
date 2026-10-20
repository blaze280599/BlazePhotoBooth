package com.blaze.photobooth.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.core.graphics.scale
import com.blaze.photobooth.model.SlotRect
import java.io.InputStream
import kotlin.math.roundToInt

/** Bitmap helpers for the renderer and the frame repository. Call off the main thread (§8.2). */
object BitmapOps {

    /**
     * The centred source rect that fills a `dstW × dstH` box ("cover", like `ContentScale.Crop`).
     * Pure maths, so it's unit-tested on the JVM.
     */
    fun coverSource(srcW: Int, srcH: Int, dstW: Float, dstH: Float): SlotRect {
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW / dstH
        return if (srcAspect > dstAspect) {
            val w = srcH * dstAspect
            SlotRect((srcW - w) / 2f, 0f, w, srcH.toFloat())
        } else {
            val h = srcW / dstAspect
            SlotRect(0f, (srcH - h) / 2f, srcW.toFloat(), h)
        }
    }

    internal fun SlotRect.toRect() = Rect(x.roundToInt(), y.roundToInt(), right.roundToInt(), bottom.roundToInt())

    /** Power-of-two sample size that keeps the decoded width at or above [maxWidth] before the final scale. */
    fun sampleSizeFor(width: Int, maxWidth: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxWidth) sample *= 2
        return sample
    }

    /**
     * Decodes [open]'s image, downsampled so its width is at most [maxWidth] (null = full size).
     * [open] is called twice: once for the bounds, once for the pixels.
     */
    fun decode(open: () -> InputStream, maxWidth: Int? = null): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (maxWidth != null && bounds.outWidth > 0) inSampleSize = sampleSizeFor(bounds.outWidth, maxWidth)
        }
        val bitmap = open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Couldn't decode image")
        if (maxWidth == null || bitmap.width <= maxWidth) return bitmap
        val height = (bitmap.height * maxWidth.toFloat() / bitmap.width).roundToInt()
        return bitmap.scale(maxWidth, height).also { if (it !== bitmap) bitmap.recycle() }
    }

    /** Downscale so the long edge is at most [maxEdge]; returns [bitmap] itself when it's already small enough. */
    fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val long = maxOf(bitmap.width, bitmap.height)
        if (long <= maxEdge) return bitmap
        val f = maxEdge.toFloat() / long
        return bitmap.scale((bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1))
    }

    /**
     * For a bitmap that's about to be shown (and never changed again): starts its upload to the GPU now, in the
     * background, instead of in the first frame that draws it (M9: five photos uploading at once made Edit's first
     * frame miss its deadline). Returns [bitmap].
     */
    fun forScreen(bitmap: Bitmap): Bitmap = bitmap.apply { prepareToDraw() }

    /** Preview bitmaps (Setup, Pick) are at most this many pixels on their long edge (§10). */
    const val PreviewMaxPx = 600
}
