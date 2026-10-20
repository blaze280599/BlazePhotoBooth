package com.blaze.photobooth.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.blaze.photobooth.model.Decoration
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps.toRect
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * The one strip renderer (README §10). It draws in canvas units (§5.1 export pixels, or an image
 * frame's native pixels), so export draws it 1:1 into a bitmap and the live preview scales the
 * canvas down: same code, same composition, so preview and export can never drift apart.
 *
 * Thread-safe: every [draw] call uses its own paints, so the preview (main thread) and an export
 * (`Dispatchers.Default`) may render at the same time.
 */
class StripRenderer(private val fonts: StripFonts) {

    /**
     * Draws [spec] with [photos] (index = slot; null = empty). Bitmaps can be any resolution:
     * each is cover-cropped into its slot.
     */
    fun draw(canvas: Canvas, spec: StripSpec, photos: List<Bitmap?>) {
        when (val frame = spec.frame) {
            is FrameSource.Theme -> drawTheme(canvas, spec, frame.theme, photos)
            is FrameSource.Image -> drawImage(canvas, spec, frame, photos)
        }
    }

    // Theme frames: background → photo cells → placeholders → stickers → footer → extras (§10).
    private fun drawTheme(canvas: Canvas, spec: StripSpec, theme: FrameTheme, photos: List<Bitmap?>) {
        val size = spec.canvasSize
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (theme.isGradient) {
            paint.shader = LinearGradient(0f, 0f, 0f, h, theme.backgroundTop, theme.backgroundBottom, Shader.TileMode.CLAMP)
        } else {
            paint.color = theme.backgroundTop
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val clip = Path()
        spec.slots.forEachIndexed { index, cell ->
            val photo = photos.getOrNull(index)
            clip.rewind()
            clip.addRoundRect(cell.toRectF(), CellCorner, CellCorner, Path.Direction.CW)
            canvas.withClip(clip) {
                when {
                    photo != null -> drawPhoto(this, photo, cell, spec.filter)
                    spec.preview -> drawPlaceholder(this, cell, themePlaceholderColors(theme), index)
                    else -> {
                        paint.color = themePlaceholderColors(theme).fill
                        drawRect(cell.toRectF(), paint)
                    }
                }
            }
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            color = StickerBorder
        }
        for (sticker in StickerLayout.place(theme, spec.layout)) {
            val color = theme.stickerColors.getOrElse(sticker.colorIndex) { theme.ink }
            Stickers.draw(canvas, sticker, color, fill, border)
        }

        drawFooter(canvas, spec, theme.ink, LayoutGeometry.footer(spec.layout), StickerLayout.footerTextBlock(spec.layout).w)

        if (theme.decoration == Decoration.Sprockets) drawSprockets(canvas, spec, theme)
    }

    // Image frames: white base → photos in each window → artwork on top → optional text area (§5.5.1).
    private fun drawImage(canvas: Canvas, spec: StripSpec, frame: FrameSource.Image, photos: List<Bitmap?>) {
        val size = spec.canvasSize
        val bounds = RectF(0f, 0f, size.width.toFloat(), size.height.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ImageBase }
        // Exports are JPEG, so anything the artwork leaves see-through must land on white, not black.
        canvas.drawRect(bounds, paint)

        spec.slots.forEachIndexed { index, slot ->
            val photo = photos.getOrNull(index)
            canvas.withClip(slot.toRectF()) {
                when {
                    photo != null -> drawPhoto(this, photo, slot, spec.filter)
                    spec.preview -> drawPlaceholder(this, slot, imagePlaceholderColors(index), index)
                }
            }
        }

        val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(frame.bitmap, null, bounds, art)

        frame.frame.textArea?.let { area -> drawFooter(canvas, spec, ImageFrameInk, area, area.w) }
    }

    private fun drawPhoto(canvas: Canvas, photo: Bitmap, slot: SlotRect, filter: PhotoFilter) {
        val src = BitmapOps.coverSource(photo.width, photo.height, slot.w, slot.h).toRect()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = Filters.platform(filter) }
        canvas.drawBitmap(photo, src, slot.toRectF(), paint)
    }

    private class PlaceholderColors(val fill: Int, val figure: Int)

    private fun themePlaceholderColors(theme: FrameTheme): PlaceholderColors {
        val luminance = ColorUtils.calculateLuminance(theme.backgroundTop)
        val dark = luminance < 0.3
        val fill = when {
            dark -> ColorUtils.blendARGB(theme.backgroundTop, Color.White, 0.14f)
            // Near-white paper (Milk): tint the cell with a whisper of ink so it still reads as a window.
            luminance > 0.9 -> ColorUtils.blendARGB(theme.backgroundTop, theme.ink, 0.06f)
            else -> ColorUtils.blendARGB(theme.backgroundTop, Color.White, 0.6f)
        }
        return PlaceholderColors(fill, ColorUtils.blendARGB(fill, theme.ink, if (dark) 0.22f else 0.16f))
    }

    private fun imagePlaceholderColors(index: Int): PlaceholderColors {
        val fill = PlaceholderPastels[index % PlaceholderPastels.size]
        return PlaceholderColors(fill, ColorUtils.blendARGB(fill, ImageFrameInk, 0.13f))
    }

    /**
     * A cute stand-in for an empty cell (preview only): a soft card with a little person and a heart,
     * so the strip reads as "your photos go here".
     */
    private fun drawPlaceholder(canvas: Canvas, cell: SlotRect, colors: PlaceholderColors, index: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.fill }
        canvas.drawRect(cell.toRectF(), paint)

        val unit = min(cell.w, cell.h)
        val cx = cell.x + cell.w / 2f
        val bottom = cell.bottom
        paint.color = colors.figure
        // Shoulders: a wide rounded mound rising from the bottom edge.
        val bodyW = unit * 0.62f
        val bodyTop = bottom - unit * 0.3f
        canvas.drawRoundRect(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bottom + unit * 0.3f, unit * 0.3f, unit * 0.3f, paint)
        // Head.
        val headR = unit * 0.16f
        val headY = bodyTop - headR * 0.82f
        canvas.drawCircle(cx, headY, headR, paint)
        // A little heart floating by, on alternating sides.
        val side = if (index % 2 == 0) 1 else -1
        val heart = Stickers.path(StickerKind.Heart)
        canvas.withTranslation(cx + side * headR * 1.9f, headY - headR * 0.9f) {
            rotate(side * 14f)
            scale(unit * 0.13f, unit * 0.13f)
            drawPath(heart, paint)
        }
    }

    /** Wordmark, caption and date, centred in [area] and shrunk to fit [maxWidth]. */
    private fun drawFooter(canvas: Canvas, spec: StripSpec, ink: Int, area: SlotRect, maxWidth: Float) {
        val wordmarkSize = min(area.h * 0.25f, area.w * 0.1f)
        val lines = buildList {
            if (spec.frame is FrameSource.Theme) add(Line(Wordmark, fonts.wordmark, wordmarkSize))
            spec.caption?.takeIf { it.isNotBlank() }?.let { add(Line(it, fonts.handwritten, wordmarkSize * 0.8f)) }
            spec.date?.let { add(Line(it.format(DateFormat), fonts.handwritten, wordmarkSize * 0.62f)) }
        }
        if (lines.isEmpty()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textAlign = Paint.Align.CENTER
        }
        for (line in lines) {
            paint.typeface = line.typeface
            paint.textSize = line.size
            val width = paint.measureText(line.text)
            if (width > maxWidth) line.size *= maxWidth / width
        }
        val heights = lines.map { it.size * LineHeight }
        var top = area.y + (area.h - heights.sum()) / 2f
        val cx = area.x + area.w / 2f
        lines.forEachIndexed { i, line ->
            paint.typeface = line.typeface
            paint.textSize = line.size
            val fm = paint.fontMetrics
            val baseline = top + (heights[i] - (fm.descent - fm.ascent)) / 2f - fm.ascent
            canvas.drawText(line.text, cx, baseline, paint)
            top += heights[i]
        }
    }

    private class Line(val text: String, val typeface: Typeface, var size: Float)

    /** Film theme: rounded sprocket holes down both outer edges (Haru Film style). */
    private fun drawSprockets(canvas: Canvas, spec: StripSpec, theme: FrameTheme) {
        val layout = spec.layout
        val margin = layout.margin.toFloat()
        val holeW = margin * 0.3f
        val holeH = margin * 0.42f
        val step = margin * 0.9f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(theme.ink, 230) }
        val h = layout.canvasHeight.toFloat()
        var y = step / 2f
        while (y + holeH / 2f < h) {
            for (cx in floatArrayOf(margin / 2f, layout.canvasWidth - margin / 2f)) {
                canvas.drawRoundRect(cx - holeW / 2f, y - holeH / 2f, cx + holeW / 2f, y + holeH / 2f, holeW * 0.28f, holeW * 0.28f, paint)
            }
            y += step
        }
    }

    private fun SlotRect.toRectF() = RectF(x, y, right, bottom)

    private object Color {
        const val White = 0xFFFFFFFF.toInt()
    }

    companion object {
        /** Corner radius of theme photo cells, in canvas pixels (§10). */
        const val CellCorner = 12f

        /** The wordmark printed on theme strips. A brand mark drawn into the image, so it isn't translated. */
        const val Wordmark = "✿ Blaze Booth"

        private const val LineHeight = 1.22f
        private val DateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private const val StickerBorder = 0xF2FFFFFF.toInt()
        private const val ImageBase = 0xFFFFFFFF.toInt()
        private const val ImageFrameInk = 0xFF3A2E33.toInt()
        private val PlaceholderPastels = intArrayOf(
            0xFFFFD6E3.toInt(), 0xFFD7E9FF.toInt(), 0xFFDDF3E4.toInt(), 0xFFFFE5CC.toInt(),
        )

        @Volatile private var instance: StripRenderer? = null

        /** The app-wide renderer; fonts are loaded once. */
        fun get(context: Context): StripRenderer =
            instance ?: synchronized(this) {
                instance ?: StripRenderer(StripFonts(context.applicationContext.resources)).also { instance = it }
            }
    }
}
