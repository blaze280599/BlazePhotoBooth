package com.blaze.photobooth.data.export

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** A new image row in Photos, still hidden while it's written (`IS_PENDING = 1`). */
data class PendingImage(val displayName: String, val takenAt: Long, val width: Int, val height: Int)

/**
 * The phone's photo gallery (README §11.5). A small seam over `MediaStore` so the insert flow (pending → write →
 * publish, or delete on failure) is unit-tested with a fake.
 */
interface MediaGallery {
    /** Whether a file of this name is already in `Pictures/Blaze Booth` (only the app's own files are visible). */
    fun exists(displayName: String): Boolean

    /** Inserts a pending row and returns its uri; throws if MediaStore says no. */
    fun createPending(image: PendingImage): Uri

    /** Copies [from] into the pending row's file. */
    fun write(uri: Uri, from: File)

    /** `IS_PENDING = 0`: the file shows up in Photos. */
    fun publish(uri: Uri)

    /** Removes a row that never got published. */
    fun delete(uri: Uri)

    /** The name MediaStore actually gave the file (it renames clashes we couldn't see). */
    fun displayName(uri: Uri): String?
}

/**
 * [MediaGallery] on the real `MediaStore.Images` (API 29+): the app's own files need no storage permission (§12).
 */
class ResolverGallery(private val resolver: ContentResolver, private val clock: () -> Long = System::currentTimeMillis) : MediaGallery {

    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override fun exists(displayName: String): Boolean =
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, "${ExportNames.Folder}/"),
            null,
        )?.use { it.count > 0 } ?: false

    override fun createPending(image: PendingImage): Uri {
        val nowSeconds = clock() / 1000
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, image.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, MimeJpeg)
            put(MediaStore.MediaColumns.RELATIVE_PATH, ExportNames.Folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.DATE_TAKEN, image.takenAt)
            put(MediaStore.MediaColumns.DATE_ADDED, nowSeconds)
            put(MediaStore.MediaColumns.DATE_MODIFIED, nowSeconds)
            if (image.width > 0 && image.height > 0) {
                put(MediaStore.MediaColumns.WIDTH, image.width)
                put(MediaStore.MediaColumns.HEIGHT, image.height)
            }
        }
        return resolver.insert(collection, values) ?: throw IOException("MediaStore refused ${image.displayName}")
    }

    override fun write(uri: Uri, from: File) {
        val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Can't open $uri")
        out.use { stream -> from.inputStream().use { it.copyTo(stream) } }
    }

    override fun publish(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        if (resolver.update(uri, values, null, null) != 1) throw IOException("Couldn't publish $uri")
    }

    override fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    override fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    companion object {
        const val MimeJpeg = "image/jpeg"
    }
}
