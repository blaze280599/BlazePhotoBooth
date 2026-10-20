package com.blaze.photobooth.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.StripRenderer

/** Generated stand-ins for tests: pastel "photos" with numbers (no real people) and her bundled frames. */
object TestArt {

    private val palettes = listOf(
        0xFFFFB3C7.toInt() to 0xFFFFECB3.toInt(),
        0xFFB3DCFF.toInt() to 0xFFD6C4FF.toInt(),
        0xFFBEF0D2.toInt() to 0xFFFFF0B4.toInt(),
        0xFFFFCDAA.toInt() to 0xFFFAAAD2.toInt(),
    )

    /**
     * A 4:3 landscape pastel gradient with a white disc and the shot number, like the frame
     * pipeline's previews (`tools/frames/process_frames.py`). 4:3 is the camera's shape, so
     * every render exercises the cover-crop.
     */
    fun photo(number: Int, width: Int = 800, height: Int = 600): Bitmap {
        val bitmap = createBitmap(width, height)
        val c = Canvas(bitmap)
        val (top, bottom) = palettes[(number - 1).mod(palettes.size)]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
        val r = minOf(width, height) / 5f
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(width / 2f, height / 2f, r, p)
        p.color = 0xFFF0628F.toInt()
        p.textSize = r * 1.1f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(number.toString(), width / 2f, height / 2f + p.textSize * 0.36f, p)
        return bitmap
    }

    fun photos(count: Int): List<Bitmap> = List(count) { photo(it + 1) }

    fun herFrames(context: Context): List<ImageFrame> =
        FrameRepository.parseManifest(context.assets.open(FrameRepository.Manifest).bufferedReader().use { it.readText() })

    fun load(context: Context, location: ArtworkLocation, maxWidth: Int? = null): Bitmap =
        BitmapOps.decode(open = { context.assets.open((location as ArtworkLocation.Asset).path) }, maxWidth = maxWidth)

    /** Renders [spec] at [scale] (1 = export size) into a fresh bitmap. */
    fun render(context: Context, spec: StripSpec, photos: List<Bitmap?>, scale: Float): Bitmap {
        val size = spec.canvasSize
        val bitmap = createBitmap((size.width * scale).toInt(), (size.height * scale).toInt())
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        StripRenderer.get(context).draw(canvas, spec, photos)
        return bitmap
    }

    /** Lays bitmaps side by side, top-aligned, on a soft cream backdrop. */
    fun sheet(bitmaps: List<Bitmap>, gap: Int = 32): Bitmap {
        val width = bitmaps.sumOf { it.width } + gap * (bitmaps.size + 1)
        val height = bitmaps.maxOf { it.height } + gap * 2
        val out = createBitmap(width, height)
        val c = Canvas(out)
        c.drawColor(0xFFF4ECEF.toInt())
        var x = gap.toFloat()
        for (b in bitmaps) {
            c.drawBitmap(b, x, gap.toFloat(), null)
            x += b.width + gap
        }
        return out
    }
}
