package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.WindowEditor
import com.blaze.photobooth.render.SyntheticFrames.Art
import com.blaze.photobooth.render.SyntheticFrames.Box
import com.blaze.photobooth.render.SyntheticFrames.Clear
import com.blaze.photobooth.render.SyntheticFrames.White
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * README §4.9 / §5.5.2 / §13: the import pipeline's pure half on synthetic frames (transparent 1×4 and 2×2, rounded
 * heart windows, white boxes with JPEG-like noise, a transparent outside, no windows, too small, huge), and Mode B's
 * conversion to transparency with its feathered edge.
 */
class FrameImportTest {

    private fun assertWindows(expected: List<Box>, actual: List<SlotRect>, tolerance: Float = 6f) {
        assertEquals("window count", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            val ok = abs(a.x - e.x) <= tolerance && abs(a.y - e.y) <= tolerance &&
                abs(a.right - (e.x + e.w)) <= tolerance && abs(a.bottom - (e.y + e.h)) <= tolerance
            assertTrue("window $i: expected $e, got $a", ok)
        }
    }

    private fun analyze(art: Art, sourceWidth: Int = art.w) = FrameImport.analyze(art.px, art.w, art.h, sourceWidth)

    @Test
    fun transparentOneByFourIsFoundAsIs() {
        val art = Art(900, 2700)
        val windows = SyntheticFrames.strip1x4(900, 2700).onEach { art.fill(it, Clear) }
        val a = analyze(art)
        assertTrue(a.found)
        assertEquals(FrameSlotDetector.Mode.Transparent, a.mode)
        assertNull("a transparent PNG needs no keyed copy", a.keyed)
        assertTrue("Mode A windows aren't re-keyed", a.keyedWindows.isEmpty())
        assertFalse(a.tooSmall)
        assertWindows(windows, a.windows)
    }

    @Test
    fun transparentTwoByTwoIsNumberedRowByRow() {
        val art = Art(1200, 1800)
        val windows = SyntheticFrames.grid2x2(1200, 1800)
        windows.reversed().forEach { art.fill(it, Clear) }
        assertWindows(windows, analyze(art).windows)
    }

    @Test
    fun roundedHeartWindowsAreFound() {
        val art = Art(800, 1200)
        val hearts = listOf(Box(100, 100, 600, 450), Box(100, 650, 600, 450))
        hearts.forEach { art.fill(it, Clear, SyntheticFrames.heart) }
        val a = analyze(art)
        assertEquals(2, a.windows.size)
        hearts.zip(a.windows).forEach { (box, w) ->
            assertTrue("heart window $w sits on $box", w.x >= box.x - 4 && w.right <= box.x + box.w + 4 && w.h > box.h * 0.7f)
        }
    }

    @Test
    fun whiteBoxJpegIsKeyedToTransparencyWithAFeatheredEdge() {
        // 600 × 1200 keeps the detector at full resolution, so edges land on exact pixels.
        val art = Art(600, 1200)
        val windows = listOf(Box(60, 60, 480, 500), Box(60, 640, 480, 500))
        windows.forEach { art.fill(it, White) }
        art.noise(amplitude = 6)
        val a = analyze(art)
        assertEquals(FrameSlotDetector.Mode.SolidBoxes, a.mode)
        assertWindows(windows, a.windows)
        assertEquals(a.windows, a.keyedWindows)
        val keyed = checkNotNull(a.keyed)

        val y = 300
        val left = windows[0].x
        fun alpha(x: Int) = keyed[y * art.w + x] ushr 24
        assertEquals("inside the box", 0, alpha(left + 200))
        assertEquals("the box's first column", 0, alpha(left))
        assertEquals("1 px out: a third", 85, alpha(left - 1))
        assertEquals("2 px out: two thirds", 170, alpha(left - 2))
        assertEquals("3 px out: the frame", 255, alpha(left - 3))
        // The colour is kept under the feather (only alpha changes) and the original is untouched.
        assertEquals(art.px[y * art.w + left - 1] and 0xFFFFFF, keyed[y * art.w + left - 1] and 0xFFFFFF)
        assertEquals(255, art.px[y * art.w + left] ushr 24)
    }

    @Test
    fun aTransparentOutsideIsNotAWindow() {
        val art = Art(600, 1800).roundOutside(90f)
        val windows = SyntheticFrames.strip1x4(600, 1800).onEach { art.fill(it, Clear) }
        assertWindows(windows, analyze(art).windows)
    }

    @Test
    fun noWindowsOpensTheEditorWithOneDefaultWindow() {
        val art = Art(600, 1800).noise(4)
        val a = analyze(art)
        assertFalse(a.found)
        assertNull(a.mode)
        assertEquals(listOf(WindowEditor.defaultWindow(600, 1800)), a.windows)
        val w = a.windows.single()
        assertEquals("centred", 300f, w.x + w.w / 2, 0.5f)
        assertEquals(900f, w.y + w.h / 2, 0.5f)
    }

    @Test
    fun aSmallPictureWarnsButStillWorks() {
        val art = Art(480, 1440)
        SyntheticFrames.strip1x4(480, 1440).forEach { art.fill(it, Clear) }
        val a = analyze(art)
        assertTrue(a.tooSmall)
        assertTrue(a.found)
        assertEquals(4, a.windows.size)
        assertFalse("600 px is enough", analyze(Art(600, 1800).also { a2 -> SyntheticFrames.strip1x4(600, 1800).forEach { a2.fill(it, Clear) } }).tooSmall)
    }

    @Test
    fun hugePicturesAreScaledDownOnImport() {
        // Long edge capped at 7200 px…
        assertEquals(800 to 7200, FrameImport.targetSize(1000, 9000))
        // …and the whole picture at 12 MP (the phone's heap), keeping its shape.
        for ((sw, sh) in listOf(8000 to 8000, 2400 to 9000, 7600 to 2000)) {
            val (w, h) = FrameImport.targetSize(sw, sh)
            assertTrue("$w × $h", w <= 7200 && h <= 7200 && w.toLong() * h <= FrameImport.MaxPixels)
            assertEquals(sw.toFloat() / sh, w.toFloat() / h, 0.002f)
        }
        assertEquals(1788 to 6708, FrameImport.targetSize(2400, 9000))
        // Normal frames are left alone.
        assertEquals(1200 to 3600, FrameImport.targetSize(1200, 3600))
        assertEquals(2400 to 3600, FrameImport.targetSize(2400, 3600))
        // The decoder halves while it stays at or above the target, then the last step is an exact scale.
        assertEquals(1, FrameImport.sampleSize(2400, 3600))
        assertEquals(2, FrameImport.sampleSize(8000, 8000))
        assertEquals(1, FrameImport.sampleSize(2400, 9000))
        assertEquals(4, FrameImport.sampleSize(16000, 16000))
    }

    @Test
    fun hugeFramesStillDetectAfterTheScale() {
        // A 2400 × 9000 strip scaled to 1788 × 6708 by targetSize: windows come back in the scaled pixels.
        val (w, h) = FrameImport.targetSize(2400, 9000)
        val art = Art(w, h)
        val windows = SyntheticFrames.strip1x4(w, h).onEach { art.fill(it, Clear) }
        assertWindows(windows, analyze(art, sourceWidth = 2400).windows, tolerance = 14f)
    }

    @Test
    fun thumbnailsAre300PxWide() {
        assertEquals(300 to 900, FrameImport.thumbSize(1200, 3600))
        assertEquals(200 to 600, FrameImport.thumbSize(200, 600))
    }
}
