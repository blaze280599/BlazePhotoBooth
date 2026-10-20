package com.blaze.photobooth.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.screenshots.TestArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/**
 * README §10: Print writes `strip.jpg` at export size and `thumb.jpg` 600 px wide, and the strip matches what
 * Pick's preview showed (same renderer, preview-size inputs).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()
    private val renderer get() = StripRenderer.get(context)

    private fun shots(count: Int, w: Int, h: Int): List<File> = List(count) { i ->
        File(tmp.root, "shot_$i.jpg").also { f -> f.outputStream().use { TestArt.photo(i + 1, w, h).compress(Bitmap.CompressFormat.JPEG, 95, it) } }
    }

    private suspend fun exportAndCompare(exportSpec: StripSpec, previewSpec: StripSpec, files: List<File>) {
        val dir = tmp.newFolder()
        StripExporter(renderer, compute = Dispatchers.Unconfined, io = Dispatchers.Unconfined).export(exportSpec, files, dir)

        val strip = BitmapFactory.decodeFile(File(dir, SessionFileStore.StripName).path)
        val thumb = BitmapFactory.decodeFile(File(dir, SessionFileStore.ThumbName).path)
        assertEquals(ExportSize.strip(exportSpec.canvasSize).width, strip.width)
        assertEquals(ExportSize.strip(exportSpec.canvasSize).height, strip.height)
        assertEquals(600, thumb.width)

        // What Pick showed: the preview artwork and ≤ 600 px photos, at a quarter size.
        val previewPhotos = files.map { BitmapOps.downscale(BitmapFactory.decodeFile(it.path), BitmapOps.PreviewMaxPx) }
        val preview = TestArt.render(context, previewSpec, previewPhotos, scale = 0.25f)
        val diff = meanDifference(strip.scale(preview.width, preview.height), preview)
        assertTrue("${exportSpec.frame.id}: strip.jpg differs from the preview by $diff per channel", diff < 7.0)
    }

    @Test
    fun themeStripMatchesThePreview() = runTest {
        val date = LocalDate.of(2026, 10, 20)
        val spec = StripSpec(FrameSource.Theme(FrameTheme.Cherry), BoothLayout.Classic, PhotoFilter.Film, caption = "우리 둘 ♡", date = date)
        exportAndCompare(spec, spec.copy(preview = true), shots(4, 2400, 1680))
    }

    @Test
    fun herLilacPalsMatchesThePreview() = runTest {
        val frame = TestArt.herFrames(context).first { it.id == FrameId.asset("lilac-pals") }
        val full = StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork)), filter = PhotoFilter.Milky)
        val preview = StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork, BitmapOps.PreviewMaxPx)), filter = PhotoFilter.Milky)
        exportAndCompare(full, preview, shots(3, 2400, 1940))
    }

    private fun meanDifference(a: Bitmap, b: Bitmap): Double {
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var sum = 0L
        for (i in pa.indices) {
            for (shift in intArrayOf(16, 8, 0)) sum += abs(((pa[i] shr shift) and 0xFF) - ((pb[i] shr shift) and 0xFF))
        }
        return sum.toDouble() / (pa.size * 3)
    }
}
