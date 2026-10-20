package com.blaze.photobooth.data.export

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.files.SessionFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

/** README §11.5: one image goes out with `ACTION_SEND`, several with `ACTION_SEND_MULTIPLE`, readable by the target only. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareHelperTest {

    private val a = Uri.parse("content://com.blaze.photobooth.files/shared/a.jpg")
    private val b = Uri.parse("content://com.blaze.photobooth.files/shared/b.jpg")

    @Suppress("DEPRECATION")
    private fun Intent.stream(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streams(): List<Uri>? = getParcelableArrayListExtra(Intent.EXTRA_STREAM)

    @Test
    fun oneImageIsSentAsAJpeg() {
        val intent = ShareHelper.intentFor(listOf(a))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(a, intent.stream())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(1, intent.clipData?.itemCount)
        assertEquals(a, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun severalImagesAreSentTogether() {
        val intent = ShareHelper.intentFor(listOf(a, b))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(listOf(a, b), intent.streams())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(listOf(a, b), (0 until (intent.clipData?.itemCount ?: 0)).map { intent.clipData?.getItemAt(it)?.uri })
    }

    private fun helper(clock: () -> Long = System::currentTimeMillis): Pair<ShareHelper, SessionFileStore> {
        val context = RuntimeEnvironment.getApplication()
        val files = SessionFileStore(File(context.filesDir, "sessions"))
        val gallery = object : MediaGallery {
            override fun exists(displayName: String) = false
            override fun createPending(image: PendingImage) = throw IOException("not used")
            override fun write(uri: Uri, from: File) = Unit
            override fun publish(uri: Uri) = Unit
            override fun delete(uri: Uri) = Unit
            override fun displayName(uri: Uri): String? = null
        }
        val exporter = PhotoExporter(gallery, files, File(context.cacheDir, "export"), io = Dispatchers.Unconfined, compute = Dispatchers.Unconfined)
        // FileProvider's own path check expects "/" separators, so on a Windows build machine the uri is made here
        // the same way: content://<package>.files/shared/<name> (its <cache-path name="shared">).
        val uriFor = { f: File -> Uri.parse("content://${context.packageName}.files/shared/${f.name}") }
        return ShareHelper(context, exporter, clock = clock, io = Dispatchers.Unconfined, uriFor = uriFor) to files
    }

    @Test
    fun theChooserCarriesFileProviderUrisOfFilesInCacheShared() = runBlocking {
        val (share, files) = helper()
        files.dir("s1").mkdirs()
        files.strip("s1").outputStream().use { createBitmap(60, 180).apply { eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val session = SessionEntity("s1", 1_790_000_000_000, 1_790_000_000_000, "classic", "theme:milk", "original", null, true)

        val chooser = share.chooser(listOf(ExportJob(session, ExportItem.Strip)))

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, inner?.action)
        val uri = inner?.stream()
        assertEquals("content", uri?.scheme)
        assertEquals("com.blaze.photobooth.files", uri?.authority)
        assertTrue(uri?.path.orEmpty().startsWith("/shared/BlazeBooth_"))
        val shared = File(RuntimeEnvironment.getApplication().cacheDir, "shared").listFiles().orEmpty()
        assertEquals(1, shared.size)
        assertTrue(shared.single().name.startsWith("BlazeBooth_") && shared.single().name.endsWith("_strip.jpg"))
    }

    @Test
    fun oldSharedFilesAreCleanedUp() {
        val now = 10 * ShareHelper.MaxAgeMillis
        val (share, _) = helper(clock = { now })
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "shared").apply { mkdirs() }
        val old = File(dir, "old.jpg").apply { writeText("x"); setLastModified(now - ShareHelper.MaxAgeMillis - 1) }
        val fresh = File(dir, "fresh.jpg").apply { writeText("x"); setLastModified(now - 1_000) }

        assertEquals(1, share.cleanOld())
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
