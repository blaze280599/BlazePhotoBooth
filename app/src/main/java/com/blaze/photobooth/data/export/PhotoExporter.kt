package com.blaze.photobooth.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.render.Filters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One file to save: [item] of [session]. */
data class ExportJob(val session: SessionEntity, val item: ExportItem)

/** Why a save didn't work, in words she can act on (§13). */
enum class ExportError {
    /** The photo or strip isn't on disk any more. */
    MissingFile,

    /** The phone is out of space. */
    NoSpace,

    /** Anything else MediaStore or the disk threw. */
    Failed,
}

/**
 * How a batch went. [saved] are the names now in Photos. Missing files are skipped and counted in [missing]; any
 * other error stops the batch at job [stoppedAt] (so Retry can carry on from there) with [error].
 */
data class ExportOutcome(
    val saved: List<String>,
    val missing: Int = 0,
    val error: ExportError? = null,
    val stoppedAt: Int? = null,
) {
    val ok: Boolean get() = error == null && missing == 0
}

/** A file couldn't be read: told apart from write errors so it's skipped, not fatal. */
class MissingExportFile(message: String) : IOException(message)

/**
 * Saves strips and photos to the phone's Photos (README §11.5) under `Pictures/Blaze Booth`, and prepares the same
 * files for sharing. A strip is a copy of the saved `strip.jpg` (so it never depends on its frame still existing,
 * §13); a photo is the original shot with the session's filter drawn in, at stored resolution, JPEG 95. Each file is
 * first made in [workDir] (with its EXIF date), then streamed into a pending MediaStore row and published; on
 * failure the pending row is deleted. Batches run one file at a time. Everything is off the main thread.
 */
class PhotoExporter(
    private val gallery: MediaGallery,
    private val files: SessionFileStore,
    private val workDir: File,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Saves every job in order; [onProgress] gets (finished, total) before each file and once at the end. */
    suspend fun save(jobs: List<ExportJob>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ExportOutcome {
        val saved = ArrayList<String>()
        var missing = 0
        jobs.forEachIndexed { i, job ->
            onProgress(i, jobs.size)
            try {
                saved += saveOne(job)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingExportFile) {
                Log.w(Tag, "Skipped ${job.item} of ${job.session.id}: ${e.message}")
                missing++
            } catch (e: Exception) {
                Log.e(Tag, "Saving ${job.item} of ${job.session.id} failed", e)
                onProgress(i, jobs.size)
                return ExportOutcome(saved, missing, errorOf(e), stoppedAt = i)
            }
        }
        onProgress(jobs.size, jobs.size)
        if (saved.isNotEmpty()) Log.i(Tag, "Saved to Photos: ${saved.joinToString()}")
        return ExportOutcome(saved, missing)
    }

    /**
     * The file for [job] in [dir], named as it is in Photos ([ExportNames]), ready to share (§11.5). Throws
     * [MissingExportFile] if its source is gone.
     */
    suspend fun prepare(job: ExportJob, dir: File): File = withContext(compute) {
        withContext(io) { dir.mkdirs() }
        val out = File(dir, ExportNames.nameOf(stampOf(job.session), job.item))
        make(job, out)
        out
    }

    private suspend fun saveOne(job: ExportJob): String {
        val temp = withContext(compute) {
            withContext(io) { workDir.mkdirs() }
            val file = File(workDir, "export-${System.nanoTime()}.jpg")
            try {
                make(job, file)
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            file
        }
        try {
            return withContext(io) { publish(job, temp) }
        } finally {
            withContext(NonCancellable + io) { temp.delete() }
        }
    }

    /** Pending row → bytes → published. Any failure (or cancellation) deletes the row so no half-file lingers. */
    private fun publish(job: ExportJob, file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        val wanted = ExportNames.unique(ExportNames.nameOf(stampOf(job.session), job.item), gallery::exists)
        val uri: Uri = gallery.createPending(PendingImage(wanted, job.session.createdAt, bounds.outWidth, bounds.outHeight))
        try {
            gallery.write(uri, file)
            gallery.publish(uri)
        } catch (e: Throwable) {
            runCatching { gallery.delete(uri) }.onFailure { Log.w(Tag, "Couldn't remove pending $uri", it) }
            throw e
        }
        return gallery.displayName(uri) ?: wanted
    }

    /** Writes [job]'s JPEG to [out]: the strip copied, or the photo with its filter. Then stamps the EXIF date. */
    private fun make(job: ExportJob, out: File) {
        when (val item = job.item) {
            ExportItem.Strip -> copy(files.strip(job.session.id), out)
            is ExportItem.Photo -> {
                val source = files.shot(job.session.id, item.shotIndex)
                val filter = PhotoFilter.fromId(job.session.filterId) ?: PhotoFilter.Original
                if (filter == PhotoFilter.Original) copy(source, out) else renderFiltered(source, filter, out)
            }
        }
        stampDate(out, job.session.createdAt)
    }

    private fun copy(source: File, out: File) {
        if (!source.isFile) throw MissingExportFile("No ${source.name}")
        source.copyTo(out, overwrite = true)
    }

    /** Stamps when the photos were taken, so Photos files them on that day and not "today". Best effort. */
    private fun stampDate(file: File, createdAt: Long) {
        try {
            val time = Instant.ofEpochMilli(createdAt).atZone(zone())
            ExifInterface(file.path).apply {
                val stamp = ExifDate.format(time)
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
                setAttribute(ExifInterface.TAG_DATETIME, stamp)
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifOffset.format(time))
                setAttribute(ExifInterface.TAG_SOFTWARE, "Blaze Booth")
                saveAttributes()
            }
        } catch (e: IOException) {
            Log.w(Tag, "No EXIF date for ${file.name}", e)
        }
    }

    private fun stampOf(session: SessionEntity) = ExportNames.stamp(session.createdAt, zone())

    companion object {
        private const val Tag = "PhotoExporter"
        private const val JpegQuality = 95
        private val ExifDate = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
        private val ExifOffset = DateTimeFormatter.ofPattern("xxx", Locale.ROOT)

        /**
         * [source] (an upright original, §9.4) with [filter] drawn in, same size, as JPEG 95 in [out] (§11.5).
         * Public for the pixel test.
         */
        fun renderFiltered(source: File, filter: PhotoFilter, out: File) {
            if (!source.isFile) throw MissingExportFile("No ${source.name}")
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val photo = BitmapFactory.decodeFile(source.path, options) ?: throw MissingExportFile("Can't read ${source.name}")
            val result = try {
                createBitmap(photo.width, photo.height).also { bitmap ->
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = Filters.platform(filter) }
                    Canvas(bitmap).drawBitmap(photo, 0f, 0f, paint)
                }
            } finally {
                photo.recycle()
            }
            try {
                out.outputStream().buffered().use { stream ->
                    if (!result.compress(Bitmap.CompressFormat.JPEG, JpegQuality, stream)) throw IOException("JPEG encode failed")
                }
            } finally {
                result.recycle()
            }
        }

        /** Disk full is worth its own message (§13); everything else is a plain failure. */
        fun errorOf(e: Throwable): ExportError {
            val text = generateSequence(e) { it.cause }.joinToString { it.message.orEmpty() }
            return when {
                e is MissingExportFile || e is FileNotFoundException && text.contains("ENOENT") -> ExportError.MissingFile
                text.contains("ENOSPC") || text.contains("No space", ignoreCase = true) -> ExportError.NoSpace
                else -> ExportError.Failed
            }
        }
    }
}
