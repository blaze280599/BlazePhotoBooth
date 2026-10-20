package com.blaze.photobooth.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Share (README §11.5): the files are made (or copied) into `cache/shared/` with the same names and filter as in
 * Photos, handed out through the app's `FileProvider` (`${applicationId}.files`), and sent to the system share
 * sheet: `ACTION_SEND` for one image, `ACTION_SEND_MULTIPLE` for several, read permission granted per uri.
 * Files older than [MaxAgeMillis] are cleared on every share, so the folder never grows.
 */
class ShareHelper(
    private val context: Context,
    private val exporter: PhotoExporter,
    private val dir: File = File(context.cacheDir, SharedDir),
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** The `content://` uri other apps can read (tests on Windows fake it: FileProvider expects `/` paths). */
    private val uriFor: (File) -> Uri = { FileProvider.getUriForFile(context, "${context.packageName}.files", it) },
) {

    /** The share sheet for [jobs] (the system's own title), or throws (e.g. [MissingExportFile]). */
    suspend fun chooser(jobs: List<ExportJob>, title: CharSequence? = null): Intent {
        require(jobs.isNotEmpty())
        withContext(io) { cleanOld() }
        val uris = jobs.map { job -> uriFor(exporter.prepare(job, dir)) }
        Log.i(Tag, "Sharing ${uris.size} image(s)")
        return Intent.createChooser(intentFor(uris), title)
    }

    /** Deletes shared files older than [MaxAgeMillis] (another app may still be reading the newer ones). */
    fun cleanOld(): Int {
        val cutoff = clock() - MaxAgeMillis
        return dir.listFiles()?.count { it.isFile && it.lastModified() < cutoff && it.delete() } ?: 0
    }

    companion object {
        private const val Tag = "ShareHelper"
        const val SharedDir = "shared"

        /** An hour is plenty for any app to pick a shared photo up. */
        const val MaxAgeMillis = 60 * 60 * 1000L

        /** The share intent itself (before the chooser). Pure apart from `Intent`; unit-tested. */
        fun intentFor(uris: List<Uri>): Intent {
            require(uris.isNotEmpty())
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            return intent.apply {
                type = ResolverGallery.MimeJpeg
                // The chooser shows a preview from the clip and passes the grant on to the app she picks.
                clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
