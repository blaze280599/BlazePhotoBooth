package com.blaze.photobooth.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.DraftShot
import com.blaze.photobooth.render.BitmapOps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** A shot on disk plus a small thumbnail for the tray. */
class SavedShot(val shot: DraftShot, val thumbnail: Bitmap)

/**
 * Camera frame → stored shot (README §9.4): decode, rotate upright, mirror (front + mirror on),
 * centre-crop to the slot's aspect, shrink to ≤ 2400 px, then JPEG 95 into `_draft/shot_{i}.jpg`.
 * The filter is **not** baked in. All of it runs off the main thread, and every big bitmap is recycled.
 */
class ShotProcessor(
    private val draftStore: BoothDraftStore,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Saves [image] (and closes it) as shot [index]. The draft itself is updated by the caller. */
    suspend fun save(image: ImageProxy, index: Int, aspect: Float, mirror: Boolean): SavedShot {
        val (upright, thumb) = withContext(compute) {
            val rotation = image.imageInfo.rotationDegrees
            val source = try {
                image.toBitmap()
            } finally {
                image.close()
            }
            val out = transform(source, ShotGeometry.plan(source.width, source.height, rotation, mirror, aspect))
            source.recycle()
            out to thumbnail(out)
        }
        val shot = DraftShot(index, upright.width, upright.height)
        try {
            withContext(io) {
                val part = draftStore.pendingShotFile(index)
                part.parentFile?.mkdirs()
                write(upright, part)
            }
            draftStore.commitShotFile(index)
        } finally {
            upright.recycle()
        }
        return SavedShot(shot, thumb)
    }

    companion object {
        const val JpegQuality = 95

        /** Tray thumbnails, long edge in px. */
        const val ThumbPx = 320

        /** Draws [source] through [plan] into a new bitmap of the output size, in one filtered pass. */
        fun transform(source: Bitmap, plan: ShotPlan): Bitmap {
            val out = createBitmap(plan.outWidth, plan.outHeight)
            val matrix = Matrix().apply {
                val a = plan.affine
                setValues(floatArrayOf(a[0], a[1], a[2], a[3], a[4], a[5], 0f, 0f, 1f))
            }
            Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            return out
        }

        /** A small copy for the tray (always a new bitmap, so the original can be recycled). */
        fun thumbnail(bitmap: Bitmap): Bitmap {
            val small = BitmapOps.downscale(bitmap, ThumbPx)
            return if (small === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else small
        }

        fun write(bitmap: Bitmap, file: File) {
            FileOutputStream(file).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)) { "JPEG encode failed" }
                out.fd.sync()
            }
        }
    }
}
