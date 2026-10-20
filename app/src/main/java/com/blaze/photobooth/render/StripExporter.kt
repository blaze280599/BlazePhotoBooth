package com.blaze.photobooth.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.StripSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Print (README §10): the same [StripRenderer] the preview uses, drawn into a full-size bitmap
 * ([ExportSize]) and written as `strip.jpg` (JPEG 95) plus a 600 px-wide `thumb.jpg`. Photos are decoded
 * only as large as their slot needs. Runs on `Dispatchers.Default`; nothing touches the main thread.
 */
class StripExporter(
    private val renderer: StripRenderer,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Renders [spec] (its frame artwork at full size, `preview = false`) with [photos] (index = slot,
     * null = empty) into [dir]. Each file is written beside its final name and then renamed into place.
     */
    suspend fun export(spec: StripSpec, photos: List<File?>, dir: File) {
        val size = ExportSize.strip(spec.canvasSize)
        val scale = ExportSize.scale(spec.canvasSize)
        val (strip, thumb) = withContext(compute) {
            val decoded = spec.slots.mapIndexed { i, slot -> photos.getOrNull(i)?.let { decodeFor(it, slot, scale) } }
            val out = createBitmap(size.width, size.height)
            try {
                val canvas = Canvas(out)
                canvas.scale(scale, scale)
                renderer.draw(canvas, spec, decoded)
            } finally {
                decoded.forEach { it?.recycle() }
            }
            val t = ExportSize.thumb(size)
            out to out.scale(t.width, t.height)
        }
        try {
            withContext(io) {
                write(strip, File(dir, SessionFileStore.StripName), StripQuality)
                write(thumb, File(dir, SessionFileStore.ThumbName), ThumbQuality)
            }
        } finally {
            strip.recycle()
            thumb.recycle()
        }
    }

    private fun decodeFor(file: File, slot: SlotRect, scale: Float): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        check(bounds.outWidth > 0) { "Can't read ${file.name}" }
        val width = ExportSize.photoWidth(slot, bounds.outWidth, bounds.outHeight, scale)
        return BitmapOps.decode(open = { file.inputStream() }, maxWidth = width)
    }

    companion object {
        const val StripQuality = 95
        const val ThumbQuality = 90

        fun write(bitmap: Bitmap, file: File, quality: Int) {
            file.parentFile?.mkdirs()
            val part = File(file.parentFile, "${file.name}.part")
            FileOutputStream(part).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
                out.fd.sync()
            }
            Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
