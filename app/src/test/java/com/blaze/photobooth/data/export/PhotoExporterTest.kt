package com.blaze.photobooth.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * README §11.5 with a fake MediaStore: a strip is a byte copy of `strip.jpg`; a photo carries the session's filter at
 * full size; rows are pending while written and published after; a failure deletes the pending row and stops the
 * batch where Retry can carry on; a missing file is skipped (§13).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoExporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private val created = LocalDateTime.of(2026, 10, 20, 14, 30, 12).atZone(zone).toInstant().toEpochMilli()

    /** MediaStore stand-in: records every call; [failWriteOn] makes writing that name throw. */
    private class FakeGallery(val existing: MutableSet<String> = mutableSetOf()) : MediaGallery {
        val calls = mutableListOf<String>()
        val pending = mutableMapOf<Uri, PendingImage>()
        val made = mutableListOf<PendingImage>()
        val published = mutableMapOf<String, ByteArray>()
        val bytes = mutableMapOf<Uri, ByteArray>()
        var failWriteOn: String? = null
        var failMessage = "boom"
        private var next = 1

        override fun exists(displayName: String) = displayName in existing || displayName in published

        override fun createPending(image: PendingImage): Uri {
            val uri = Uri.parse("content://media/external/images/media/${next++}")
            pending[uri] = image
            made += image
            calls += "pending ${image.displayName}"
            return uri
        }

        override fun write(uri: Uri, from: File) {
            val name = pending.getValue(uri).displayName
            if (name == failWriteOn) throw IOException(failMessage)
            bytes[uri] = from.readBytes()
            calls += "write $name"
        }

        override fun publish(uri: Uri) {
            val image = pending.remove(uri) ?: error("not pending")
            published[image.displayName] = bytes.getValue(uri)
            calls += "publish ${image.displayName}"
        }

        override fun delete(uri: Uri) {
            calls += "delete ${pending.remove(uri)?.displayName}"
        }

        override fun displayName(uri: Uri): String? = null
    }

    private fun session(filter: PhotoFilter = PhotoFilter.Original) =
        SessionEntity("s1", created, created, "classic", "theme:milk", filter.id, null, true)

    private fun setUp(gallery: FakeGallery): Pair<PhotoExporter, SessionFileStore> {
        val files = SessionFileStore(temp.newFolder("sessions"))
        files.dir("s1").mkdirs()
        val exporter = PhotoExporter(gallery, files, File(temp.root, "work"), { zone }, Dispatchers.Unconfined, Dispatchers.Unconfined)
        return exporter to files
    }

    private fun writeJpeg(file: File, bitmap: Bitmap) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
    }

    /** A flat mid-grey photo, so a filter's effect is easy to read in its pixels. */
    private fun grey(w: Int = 300, h: Int = 200) = createBitmap(w, h).apply { eraseColor(Color.rgb(128, 128, 128)) }

    @Test
    fun stripIsCopiedPendingThenPublishedWithItsDate() = runBlocking {
        val gallery = FakeGallery()
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.strip("s1"), grey(120, 360))
        val original = files.strip("s1").readBytes()

        val outcome = exporter.save(listOf(ExportJob(session(), ExportItem.Strip)))

        assertTrue(outcome.ok)
        val name = "BlazeBooth_20261020_143012_strip.jpg"
        assertEquals(listOf(name), outcome.saved)
        assertEquals(listOf("pending $name", "write $name", "publish $name"), gallery.calls)
        assertTrue(gallery.pending.isEmpty())
        // The pixels are the saved strip's: decoding both gives the same image (only EXIF was added).
        val saved = gallery.published.getValue(name)
        val a = BitmapFactory.decodeByteArray(original, 0, original.size)
        val b = BitmapFactory.decodeByteArray(saved, 0, saved.size)
        assertEquals(a.width, b.width)
        assertEquals(a.getPixel(60, 180), b.getPixel(60, 180))
        // Photos files it on the day of the photos (§11.5): DATE_TAKEN and the EXIF date.
        val out = File(temp.root, "check.jpg").apply { writeBytes(saved) }
        assertEquals("2026:10:20 14:30:12", ExifInterface(out.path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        // Nothing is left behind in the work folder.
        val left = File(temp.root, "work").listFiles().orEmpty().map { it.name }
        assertTrue("left: $left", left.isEmpty())
    }

    @Test
    fun pendingRowCarriesTheSessionTimeAndSize() = runBlocking {
        val gallery = FakeGallery()
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.strip("s1"), grey(120, 360))

        exporter.save(listOf(ExportJob(session(), ExportItem.Strip)))

        assertEquals(listOf(PendingImage("BlazeBooth_20261020_143012_strip.jpg", created, 120, 360)), gallery.made)
    }

    @Test
    fun aCutIsSavedWithTheSessionsFilterAtFullSize() = runBlocking {
        val gallery = FakeGallery()
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.shot("s1", 0), grey(300, 200))

        val outcome = exporter.save(listOf(ExportJob(session(PhotoFilter.Cool), ExportItem.Photo(0, ExportItem.Photo.Kind.Cut, 1))))

        val name = "BlazeBooth_20261020_143012_cut1.jpg"
        assertEquals(listOf(name), outcome.saved)
        val bytes = gallery.published.getValue(name)
        val saved = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(300, saved.width)
        assertEquals(200, saved.height)
        // Soda (§5.3) is a cool blue tint: grey comes out bluer than red. The original stays grey.
        val p = saved.getPixel(150, 100)
        assertTrue("blue ${Color.blue(p)} vs red ${Color.red(p)}", Color.blue(p) - Color.red(p) > 15)
        val o = BitmapFactory.decodeFile(files.shot("s1", 0).path).getPixel(150, 100)
        assertTrue(abs(Color.blue(o) - Color.red(o)) < 4)
    }

    @Test
    fun filteredPixelsMatchTheFilterMatrix() {
        val source = File(temp.root, "shot.jpg")
        // Left half dark, right half light, top-left corner is the "upright" marker.
        val photo = createBitmap(200, 100).apply {
            eraseColor(Color.rgb(40, 40, 40))
            for (x in 100 until 200) for (y in 0 until 100) setPixel(x, y, Color.rgb(220, 220, 220))
        }
        writeJpeg(source, photo)
        val out = File(temp.root, "mono.jpg")

        PhotoExporter.renderFiltered(source, PhotoFilter.Mono, out)

        val result = BitmapFactory.decodeFile(out.path)
        val m = PhotoFilter.Mono.matrix
        fun expected(v: Int, row: Int) = (m[row * 5] * v + m[row * 5 + 1] * v + m[row * 5 + 2] * v + m[row * 5 + 4]).toInt().coerceIn(0, 255)
        for ((x, v) in listOf(50 to 40, 150 to 220)) {
            val p = result.getPixel(x, 50)
            val want = intArrayOf(expected(v, 0), expected(v, 1), expected(v, 2))
            val got = intArrayOf(Color.red(p), Color.green(p), Color.blue(p))
            for (c in 0..2) assertTrue("x=$x want ${want.toList()} got ${got.toList()}", abs(want[c] - got[c]) <= 6)
        }
        // Same orientation: the dark half is still on the left.
        assertTrue(Color.red(result.getPixel(20, 50)) < Color.red(result.getPixel(180, 50)))
    }

    @Test
    fun originalFilterIsAByteCopy() = runBlocking {
        val gallery = FakeGallery()
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.shot("s1", 3), grey())
        val dir = temp.newFolder("shared")

        val shared = exporter.prepare(ExportJob(session(), ExportItem.Photo(3, ExportItem.Photo.Kind.Bonus, 1)), dir)

        assertEquals("BlazeBooth_20261020_143012_bonus1.jpg", shared.name)
        val a = BitmapFactory.decodeFile(files.shot("s1", 3).path)
        val b = BitmapFactory.decodeFile(shared.path)
        assertEquals(a.getPixel(10, 10), b.getPixel(10, 10))
    }

    @Test
    fun takenNamesGetANumber() = runBlocking {
        val gallery = FakeGallery(existing = mutableSetOf("BlazeBooth_20261020_143012_strip.jpg"))
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.strip("s1"), grey())

        val first = exporter.save(listOf(ExportJob(session(), ExportItem.Strip)))
        val second = exporter.save(listOf(ExportJob(session(), ExportItem.Strip)))

        assertEquals(listOf("BlazeBooth_20261020_143012_strip_2.jpg"), first.saved)
        assertEquals(listOf("BlazeBooth_20261020_143012_strip_3.jpg"), second.saved)
    }

    @Test
    fun aFailedWriteDeletesThePendingRowAndStopsWhereRetryCanCarryOn() = runBlocking {
        val gallery = FakeGallery().apply { failWriteOn = "BlazeBooth_20261020_143012_cut2.jpg" }
        val (exporter, files) = setUp(gallery)
        (0..2).forEach { writeJpeg(files.shot("s1", it), grey()) }
        val jobs = (0..2).map { ExportJob(session(), ExportItem.Photo(it, ExportItem.Photo.Kind.Cut, it + 1)) }
        val progress = mutableListOf<Pair<Int, Int>>()

        val outcome = exporter.save(jobs) { done, total -> progress += done to total }

        assertEquals(listOf("BlazeBooth_20261020_143012_cut1.jpg"), outcome.saved)
        assertEquals(ExportError.Failed, outcome.error)
        assertEquals(1, outcome.stoppedAt)
        assertFalse(outcome.ok)
        assertTrue("delete BlazeBooth_20261020_143012_cut2.jpg" in gallery.calls)
        assertTrue(gallery.pending.isEmpty())
        assertFalse(gallery.calls.any { it.endsWith("cut3.jpg") })
        assertEquals(0 to 3, progress.first())

        // Retry carries on with the rest.
        gallery.failWriteOn = null
        val retry = exporter.save(jobs.drop(outcome.stoppedAt ?: 0))
        assertEquals(listOf("BlazeBooth_20261020_143012_cut2.jpg", "BlazeBooth_20261020_143012_cut3.jpg"), retry.saved)
    }

    @Test
    fun aFullDiskIsToldApart() = runBlocking {
        val gallery = FakeGallery().apply {
            failWriteOn = "BlazeBooth_20261020_143012_strip.jpg"
            failMessage = "write failed: ENOSPC (No space left on device)"
        }
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.strip("s1"), grey())

        assertEquals(ExportError.NoSpace, exporter.save(listOf(ExportJob(session(), ExportItem.Strip))).error)
    }

    @Test
    fun missingFilesAreSkippedAndCounted() = runBlocking {
        val gallery = FakeGallery()
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.shot("s1", 0), grey())
        // shot_1.jpg was lost; strip.jpg too.
        val jobs = listOf(
            ExportJob(session(PhotoFilter.Peach), ExportItem.Photo(0, ExportItem.Photo.Kind.Cut, 1)),
            ExportJob(session(PhotoFilter.Peach), ExportItem.Photo(1, ExportItem.Photo.Kind.Cut, 2)),
            ExportJob(session(), ExportItem.Strip),
        )

        val outcome = exporter.save(jobs)

        assertEquals(listOf("BlazeBooth_20261020_143012_cut1.jpg"), outcome.saved)
        assertEquals(2, outcome.missing)
        assertNull(outcome.error)
        // No pending row was ever made for a missing file.
        assertEquals(1, gallery.calls.count { it.startsWith("pending") })
    }

    @Test
    fun aStripWhoseFrameIsGoneStillSaves() = runBlocking {
        // §13: the frame "asset:deleted-frame" isn't in the catalog any more; strip.jpg is all that's needed.
        val gallery = FakeGallery()
        val (exporter, files) = setUp(gallery)
        writeJpeg(files.strip("s1"), grey())
        val orphan = session().copy(layoutId = SessionEntity.ImageLayoutId, frameId = "asset:deleted-frame")

        assertTrue(exporter.save(listOf(ExportJob(orphan, ExportItem.Strip))).ok)
    }

    @Test
    fun errorsAreClassified() {
        assertEquals(ExportError.MissingFile, PhotoExporter.errorOf(MissingExportFile("x")))
        assertEquals(ExportError.NoSpace, PhotoExporter.errorOf(IOException("x", IOException("ENOSPC"))))
        assertEquals(ExportError.Failed, PhotoExporter.errorOf(IllegalStateException("x")))
    }
}
