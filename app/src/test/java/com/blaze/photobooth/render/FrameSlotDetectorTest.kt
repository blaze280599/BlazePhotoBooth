package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/** README §5.5.2 step 5: synthetic frames for every case the import flow (M7) will meet. */
class FrameSlotDetectorTest {

    private data class Box(val x: Int, val y: Int, val w: Int, val h: Int)

    private class Art(val w: Int, val h: Int, bg: Int = Pink) {
        val px = IntArray(w * h) { bg }

        fun fill(box: Box, color: Int, inside: (fx: Float, fy: Float) -> Boolean = { _, _ -> true }) {
            for (y in box.y until box.y + box.h) for (x in box.x until box.x + box.w) {
                // fx, fy in [-1, 1] across the box.
                val fx = (x - box.x + 0.5f) / box.w * 2f - 1f
                val fy = (y - box.y + 0.5f) / box.h * 2f - 1f
                if (inside(fx, fy)) px[y * w + x] = color
            }
        }

        fun noise(amplitude: Int, seed: Int = 7) {
            val random = Random(seed)
            for (i in px.indices) {
                val p = px[i]
                if (p ushr 24 == 0) continue
                fun ch(shift: Int) = (((p shr shift) and 0xFF) + random.nextInt(-amplitude, amplitude + 1)).coerceIn(0, 255)
                px[i] = (p and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }

    private fun detectFound(art: Art): FrameSlotDetector.Result.Found {
        val result = FrameSlotDetector.detect(art.px, art.w, art.h)
        return result as? FrameSlotDetector.Result.Found ?: run { fail("Expected windows, got $result"); error("") }
    }

    /** Detected slots sit on the drawn windows (± a few px of edge growth/downscale rounding). */
    private fun assertSlots(expected: List<Box>, actual: List<SlotRect>, tolerance: Float = 6f) {
        assertEquals("slot count", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            val ok = abs(a.x - e.x) <= tolerance && abs(a.y - e.y) <= tolerance &&
                abs(a.right - (e.x + e.w)) <= tolerance && abs(a.bottom - (e.y + e.h)) <= tolerance
            assertTrue("slot $i: expected $e, got $a", ok)
        }
    }

    private fun strip1x4(w: Int, h: Int): List<Box> {
        val m = w / 15
        val cellH = (h - m * 6) / 4
        return List(4) { Box(m, m + it * (cellH + m), w - 2 * m, cellH) }
    }

    @Test
    fun transparentOneByFour() {
        val art = Art(600, 1800)
        val windows = strip1x4(600, 1800)
        windows.forEach { art.fill(it, Clear) }
        val found = detectFound(art)
        assertEquals(FrameSlotDetector.Mode.Transparent, found.mode)
        assertSlots(windows, found.slots)
    }

    @Test
    fun transparentTwoByTwoIsRowMajor() {
        val art = Art(1200, 1800)
        val windows = listOf(Box(80, 80, 480, 640), Box(640, 80, 480, 640), Box(80, 800, 480, 640), Box(640, 800, 480, 640))
        // Drawn out of order: detection must still number them row by row, left to right.
        windows.reversed().forEach { art.fill(it, Clear) }
        assertSlots(windows, detectFound(art).slots)
    }

    @Test
    fun roundedHeartWindowsUseTheirBoundingBox() {
        val art = Art(800, 1200)
        val hearts = listOf(Box(100, 100, 600, 450), Box(100, 650, 600, 450))
        val heart = { fx: Float, fy: Float ->
            val x = fx * 1.2f
            val y = -fy * 1.25f + 0.2f
            (x * x + y * y - 1).pow(3) - x * x * y.pow(3) <= 0f
        }
        hearts.forEach { art.fill(it, Clear, heart) }
        val slots = detectFound(art).slots
        assertEquals(2, slots.size)
        for ((box, slot) in hearts.zip(slots)) {
            assertTrue("heart slot $slot inside $box", slot.x >= box.x - 4 && slot.right <= box.x + box.w + 4)
            assertTrue("heart slot $slot is a real window", slot.w > box.w * 0.7f && slot.h > box.h * 0.7f)
        }
    }

    @Test
    fun whiteBoxJpegWithNoiseIsKeyedToTransparency() {
        val art = Art(600, 1800)
        val windows = strip1x4(600, 1800)
        windows.forEach { art.fill(it, White) }
        art.noise(amplitude = 6)
        val found = detectFound(art)
        assertEquals(FrameSlotDetector.Mode.SolidBoxes, found.mode)
        assertSlots(windows, found.slots)

        // The boxes are now see-through; the pink frame around them is still solid.
        val inside = windows[1].let { (it.y + it.h / 2) * art.w + it.x + it.w / 2 }
        assertEquals(0, found.pixels[inside] ushr 24)
        assertEquals(255, found.pixels[10 * art.w + 10] ushr 24)
        val between = (windows[1].y - 20) * art.w + art.w / 2
        assertEquals(255, found.pixels[between] ushr 24)
    }

    @Test
    fun sameColouredCloudInTheMarginIsNotAWindow() {
        val art = Art(900, 1800)
        val windows = listOf(Box(150, 100, 600, 450), Box(150, 650, 600, 450), Box(150, 1200, 600, 450))
        windows.forEach { art.fill(it, White) }
        // A round white cloud sticker in the margin: same colour, but not rectangle-like.
        art.fill(Box(10, 560, 130, 90), White) { fx, fy -> fx * fx + fy * fy <= 1f }
        val found = detectFound(art)
        assertSlots(windows, found.slots)
        assertEquals(255, found.pixels[605 * art.w + 75] ushr 24)
    }

    @Test
    fun transparentOutsideOfARoundedFrameIsIgnored() {
        val art = Art(600, 1800)
        // Rounded-corner frame: the corners outside the frame are transparent and touch the border.
        val r = 90f
        for (y in 0 until art.h) for (x in 0 until art.w) {
            val dx = maxOf(r - x, x - (art.w - 1 - r), 0f)
            val dy = maxOf(r - y, y - (art.h - 1 - r), 0f)
            if (dx * dx + dy * dy > r * r) art.px[y * art.w + x] = Clear
        }
        val windows = strip1x4(600, 1800)
        windows.forEach { art.fill(it, Clear) }
        assertSlots(windows, detectFound(art).slots)
    }

    @Test
    fun largeFramesAreDownscaledAndScaledBack() {
        val art = Art(2400, 3600)
        val windows = listOf(Box(96, 96, 1076, 1464), Box(1228, 96, 1076, 1464), Box(96, 1616, 1076, 1464), Box(1228, 1616, 1076, 1464))
        windows.forEach { art.fill(it, Clear) }
        assertSlots(windows, detectFound(art).slots, tolerance = 10f)
    }

    @Test
    fun noWindowsAtAll() {
        val art = Art(600, 1800)
        art.noise(amplitude = 4)
        assertEquals(
            FrameSlotDetector.Result.NotFound(FrameSlotDetector.Failure.NoWindows),
            FrameSlotDetector.detect(art.px, art.w, art.h),
        )
    }

    @Test
    fun tinySpecksAreNotWindows() {
        val art = Art(600, 1800)
        art.fill(Box(300, 300, 20, 20), Clear)
        assertTrue(FrameSlotDetector.detect(art.px, art.w, art.h) is FrameSlotDetector.Result.NotFound)
    }

    @Test
    fun tooManyWindowsFailValidation() {
        val art = Art(1200, 1800)
        for (row in 0 until 3) for (col in 0 until 3) art.fill(Box(60 + col * 380, 60 + row * 560, 320, 480), Clear)
        assertEquals(
            FrameSlotDetector.Result.NotFound(FrameSlotDetector.Failure.TooManyWindows),
            FrameSlotDetector.detect(art.px, art.w, art.h),
        )
    }

    @Test
    fun orderGroupsRowsByTopEdge() {
        val slots = listOf(SlotRect(500f, 110f, 100f, 100f), SlotRect(0f, 400f, 100f, 100f), SlotRect(0f, 100f, 100f, 100f))
        assertEquals(listOf(slots[2], slots[0], slots[1]), FrameSlotDetector.order(slots, imageHeight = 1000))
    }

    private companion object {
        const val Pink = 0xFFF7B6C8.toInt()
        const val White = 0xFFFDFDFD.toInt()
        const val Clear = 0x00000000
    }
}
