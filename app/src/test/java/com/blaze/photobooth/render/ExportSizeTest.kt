package com.blaze.photobooth.render

import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.CanvasSize
import com.blaze.photobooth.model.SlotRect
import org.junit.Assert.assertEquals
import org.junit.Test

/** README §5.5.1 and §10: how big `strip.jpg` and `thumb.jpg` come out. */
class ExportSizeTest {

    @Test
    fun themeStripsExportAtTheirCanvasSize() {
        for (layout in BoothLayout.entries) {
            val canvas = CanvasSize(layout.canvasWidth, layout.canvasHeight)
            assertEquals(1f, ExportSize.scale(canvas))
            assertEquals(canvas, ExportSize.strip(canvas))
        }
    }

    @Test
    fun herFramesExportAtNativeSize() {
        // Lilac Pals is 1200×3142 (§5.5.6).
        assertEquals(CanvasSize(1200, 3142), ExportSize.strip(CanvasSize(1200, 3142)))
    }

    @Test
    fun narrowFramesAreDoubled() {
        assertEquals(CanvasSize(1600, 4800), ExportSize.strip(CanvasSize(800, 2400)))
    }

    @Test
    fun hugeFramesShrinkToTheLongEdgeLimit() {
        assertEquals(CanvasSize(3600, 7200), ExportSize.strip(CanvasSize(4000, 8000)))
        // Doubled first, then capped: 1000×4000 → 2000×8000 → 1800×7200.
        assertEquals(CanvasSize(1800, 7200), ExportSize.strip(CanvasSize(1000, 4000)))
    }

    @Test
    fun thumbnailsAre600Wide() {
        assertEquals(CanvasSize(600, 1800), ExportSize.thumb(CanvasSize(1200, 3600)))
        assertEquals(CanvasSize(600, 1571), ExportSize.thumb(CanvasSize(1200, 3142)))
        assertEquals(CanvasSize(600, 900), ExportSize.thumb(CanvasSize(2400, 3600)))
    }

    @Test
    fun photosAreDecodedJustBigEnoughToCoverTheirSlot() {
        val classicCell = SlotRect(72f, 72f, 1056f, 739f) // ≈ 1.43
        // A shot cropped to the same aspect only needs the slot's width.
        assertEquals(1056, ExportSize.photoWidth(classicCell, 2400, 1680, scale = 1f))
        // A wider 4:3-ish bonus shot in a taller slot is matched on height.
        val squareSlot = SlotRect(0f, 0f, 1000f, 1000f)
        assertEquals(1334, ExportSize.photoWidth(squareSlot, 2400, 1800, scale = 1f))
        // Never more than the stored photo.
        assertEquals(800, ExportSize.photoWidth(squareSlot, 800, 600, scale = 2f))
    }
}
