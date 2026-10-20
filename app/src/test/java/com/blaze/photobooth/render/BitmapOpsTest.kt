package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapOpsTest {

    @Test
    fun coverCropsWideSourceFromTheMiddle() {
        // A 4:3 photo into a square cell keeps the full height and trims the sides.
        assertEquals(SlotRect(500f, 0f, 3000f, 3000f), BitmapOps.coverSource(4000, 3000, 100f, 100f))
    }

    @Test
    fun coverCropsTallSourceFromTheMiddle() {
        val r = BitmapOps.coverSource(3000, 4000, 1056f, 736f)
        assertEquals(3000f, r.w, 0.01f)
        assertEquals(1056f / 736f, r.aspect, 0.001f)
        assertEquals((4000f - r.h) / 2f, r.y, 0.01f)
    }

    @Test
    fun sampleSizeIsAPowerOfTwoThatNeverUndershoots() {
        assertEquals(1, BitmapOps.sampleSizeFor(1000, 600))
        assertEquals(2, BitmapOps.sampleSizeFor(1200, 600))
        assertEquals(4, BitmapOps.sampleSizeFor(4000, 600))
    }
}
