package com.blaze.photobooth.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.frames.CustomFrameStore
import com.blaze.photobooth.data.frames.FrameImporter
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.render.FrameSlotDetector
import com.blaze.photobooth.render.SyntheticFrames
import com.blaze.photobooth.render.SyntheticFrames.Art
import com.blaze.photobooth.render.SyntheticFrames.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * README §4.9 end to end on real bitmaps: a transparent-window PNG, a white-box JPEG and an opaque frame with a window
 * she drew go through detection, the Confirm preview and Save, and come out as `frame.png` with see-through windows,
 * a 300 px `thumb.png` and a `frames` row.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BoothDatabase
    private lateinit var store: CustomFrameStore
    private lateinit var importer: FrameImporter

    @Before
    fun open() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, BoothDatabase::class.java).allowMainThreadQueries().build()
        store = CustomFrameStore(db.frames(), tmp.newFolder("frames"), { emptySet() }, { 0 })
        importer = FrameImporter(app, store, compute = Dispatchers.Unconfined, io = Dispatchers.Unconfined)
    }

    @After
    fun close() = db.close()

    private fun decode(file: File): Bitmap = file.inputStream().use { checkNotNull(BitmapFactory.decodeStream(it)) }

    private fun Bitmap.alphaAt(x: Int, y: Int) = getPixel(x, y) ushr 24

    private fun SlotRect.cx() = (x + w / 2).toInt()

    private fun SlotRect.cy() = (y + h / 2).toInt()

    @Test
    fun aTransparentPngIsSavedWithItsWindowsOpen() = runTest {
        val (art, windows) = SyntheticFrames.herStyleStrip()
        val frame = importer.prepare(art.toBitmap(), sourceWidth = art.w)
        assertEquals(FrameSlotDetector.Mode.Transparent, frame.analysis.mode)
        assertEquals(4, frame.analysis.windows.size)
        assertFalse(frame.analysis.tooSmall)
        assertEquals(1600, frame.preview.height)

        val preview = importer.cutPreview(frame, frame.analysis.windows)
        assertEquals(frame.preview.width, preview.width)
        val s = preview.width / art.w.toFloat()
        val w0 = frame.analysis.windows[0]
        assertEquals(0, preview.alphaAt((w0.cx() * s).toInt(), (w0.cy() * s).toInt()))

        val id = importer.save(frame, frame.analysis.windows, "  Frame 1 ")
        val saved = store.frames.first().single().frame
        assertEquals(id, saved.id)
        assertEquals("Frame 1", saved.name)
        assertEquals(art.w to art.h, saved.width to saved.height)
        assertEquals(4, saved.slots.size)
        val png = decode(store.artworkFile(id.localId))
        assertEquals(art.w, png.width)
        windows.forEach { b -> assertEquals("window $b is open", 0, png.alphaAt(b.x + b.w / 2, b.y + b.h / 2)) }
        assertEquals("the paper stays", 255, png.alphaAt(art.w / 2, windows[1].y - 20))
        val thumb = decode(store.thumbFile(id.localId))
        assertEquals(300 to 900, thumb.width to thumb.height)
    }

    @Test
    fun aWhiteBoxJpegIsKeyedThenSaved() = runTest {
        val art = Art(600, 1800)
        val boxes = SyntheticFrames.strip1x4(600, 1800).onEach { art.fill(it, White) }
        val jpeg = tmp.newFile("frame.jpg")
        FileOutputStream(jpeg).use { art.toBitmap().compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val decoded = decode(jpeg).copy(Bitmap.Config.ARGB_8888, false)

        val frame = importer.prepare(decoded, sourceWidth = decoded.width)
        assertEquals(FrameSlotDetector.Mode.SolidBoxes, frame.analysis.mode)
        assertEquals(4, frame.analysis.windows.size)

        val id = importer.save(frame, frame.analysis.windows, "Frame 2")
        val png = decode(store.artworkFile(id.localId))
        boxes.forEach { b -> assertEquals("box $b is see-through", 0, png.alphaAt(b.x + b.w / 2, b.y + b.h / 2)) }
        assertEquals("the pink frame stays", 255, png.alphaAt(art.w / 2, boxes[1].y - 15))
        assertEquals("the corner stays", 255, png.alphaAt(5, 5))
    }

    @Test
    fun aWindowSheDrewOnAnOpaqueFrameIsCutOut() = runTest {
        val art = Art(600, 1800).noise(3)
        val frame = importer.prepare(art.toBitmap(), sourceWidth = 480)
        assertFalse("nothing to find", frame.analysis.found)
        assertTrue("480 px: the blurry warning", frame.analysis.tooSmall)
        val window = SlotRect(100f, 300f, 400f, 300f)

        val id = importer.save(frame, listOf(window), "Frame 3")
        val png = decode(store.artworkFile(id.localId))
        assertEquals(0, png.alphaAt(300, 450))
        assertEquals(255, png.alphaAt(300, 250))
        assertEquals(listOf(window), store.frames.first().single().frame.slots)
    }

    @Test
    fun openDecodesThePickedFileAndShrinksHugeOnes() = runTest {
        // 8000 × 1000 is over the 7200 px long edge: imported at 7200 × 900.
        val art = Art(8000, 1000)
        listOf(
            SyntheticFrames.Box(200, 150, 3600, 700),
            SyntheticFrames.Box(4200, 150, 3600, 700),
        ).forEach { art.fill(it, SyntheticFrames.Clear) }
        val file = tmp.newFile("huge.png")
        FileOutputStream(file).use { art.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }

        val frame = importer.open(Uri.fromFile(file))
        assertEquals(7200, frame.width)
        assertEquals(900, frame.height)
        assertEquals(8000, frame.sourceWidth)
        assertEquals(2, frame.analysis.windows.size)
        assertTrue(frame.analysis.windows[0].right < frame.analysis.windows[1].x)
    }

    /**
     * §13: a picked file that isn't a picture the phone can decode fails with an ordinary exception, which Add frame
     * shows as "Couldn't open that picture" (Pick another / Cancel); nothing is saved.
     */
    @Test
    fun aFileThatIsntAPictureFailsGently() = runTest {
        val file = tmp.newFile("not-a-picture.png").apply { writeText("this is not a picture") }
        for (attempt in listOf<suspend () -> Unit>({ importer.quickPreview(Uri.fromFile(file)) }, { importer.open(Uri.fromFile(file)) })) {
            val failure = try {
                attempt()
                null
            } catch (e: Exception) {
                e
            }
            assertTrue("decoding garbage must throw an Exception", failure != null)
        }
        assertTrue(store.frames.first().isEmpty())
    }
}
