package com.blaze.photobooth.screenshots

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.SampleArt
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.abs

/**
 * The strip renderer drawn straight into bitmaps (README §10, §15.4): her five frames, every layout
 * in a few themes, every filter, and the Setup placeholders. Compare the `her_frame_` PNGs with the
 * pipeline previews she approved (the `_photos.jpg` files in `photo-frames/_previews/`).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h923dp-port-420dpi")
class StripRenderScreenshotTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val date = LocalDate.of(2026, 10, 20)

    private fun Bitmap.save(name: String) = captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun herFrames() {
        val renders = TestArt.herFrames(context).map { frame ->
            val art = TestArt.load(context, frame.artwork)
            val spec = StripSpec(FrameSource.Image(frame, art))
            TestArt.render(context, spec, TestArt.photos(frame.slots.size), scale = 0.5f).also {
                it.save("her_frame_${frame.id.localId}")
            }
        }
        TestArt.sheet(renders.map { it.scale(it.width / 2, it.height / 2) }).save("her_frames_all")
    }

    @Test
    fun everyLayoutInThreeThemes() {
        for (theme in listOf(FrameTheme.Strawberry, FrameTheme.Cloud, FrameTheme.Film)) {
            val renders = BoothLayout.entries.map { layout ->
                val spec = StripSpec(FrameSource.Theme(theme), layout, caption = "우리 둘 ♡ best day", date = date)
                TestArt.render(context, spec, TestArt.photos(layout.cuts), scale = 900f / layout.canvasHeight)
            }
            TestArt.sheet(renders).save("strip_layouts_${theme.id}")
        }
    }

    @Test
    fun everyThemeAsSetupShowsIt() {
        val renders = FrameTheme.entries.map { theme ->
            val spec = StripSpec(FrameSource.Theme(theme), BoothLayout.Classic, date = date, preview = true)
            TestArt.render(context, spec, emptyList(), scale = 0.25f)
        }
        TestArt.sheet(renders).save("strip_themes_preview")
    }

    @Test
    fun herFramesAsSetupShowsThem() {
        val renders = TestArt.herFrames(context).map { frame ->
            val art = TestArt.load(context, frame.artwork, maxWidth = BitmapOps.PreviewMaxPx)
            TestArt.render(context, StripSpec(FrameSource.Image(frame, art), preview = true), emptyList(), scale = 0.25f)
        }
        TestArt.sheet(renders).save("her_frames_preview")
    }

    @Test
    fun everyFilter() {
        val scene = SampleArt.filterSample(600)
        val photos = listOf(scene, TestArt.photo(1), scene, TestArt.photo(4))
        val renders = PhotoFilter.entries.map { filter ->
            val spec = StripSpec(FrameSource.Theme(FrameTheme.Milk), BoothLayout.Classic, filter, caption = filter.id, date = date)
            TestArt.render(context, spec, photos, scale = 0.25f)
        }
        TestArt.sheet(renders).save("strip_filters")
    }

    /**
     * §10: the preview is the export, scaled. Rendering at 1:1 and shrinking must match rendering
     * straight at preview scale with ≤ 600 px photos, apart from resampling softness.
     */
    @Test
    fun previewMatchesExport() {
        val frame = TestArt.herFrames(context).first { it.slots.size == 4 }
        val theme = StripSpec(FrameSource.Theme(FrameTheme.Strawberry), BoothLayout.Classic, PhotoFilter.Peach, caption = "hi", date = date)
        val cases = listOf(
            theme to theme,
            StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork)), filter = PhotoFilter.Mono) to
                StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork, BitmapOps.PreviewMaxPx)), filter = PhotoFilter.Mono),
        )
        for ((exportSpec, previewSpec) in cases) {
            val fullPhotos = List(exportSpec.cuts) { TestArt.photo(it + 1, 2400, 1800) }
            val export = TestArt.render(context, exportSpec, fullPhotos, scale = 1f)
            val preview = TestArt.render(context, previewSpec, fullPhotos.map { BitmapOps.downscale(it, BitmapOps.PreviewMaxPx) }, scale = 0.25f)
            val shrunk = export.scale(preview.width, preview.height)
            val diff = meanDifference(shrunk, preview)
            assertTrue("${exportSpec.frame.id}: preview differs from export by $diff per channel", diff < 6.0)
        }
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
