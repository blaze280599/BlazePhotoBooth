package com.blaze.photobooth.model

import com.blaze.photobooth.render.LayoutGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §5.1: cells stay on the canvas, never overlap, and have the documented aspect ratios. */
class LayoutGeometryTest {

    /** Cell aspect (w:h) from the §5.1 table. */
    private val expectedAspect = mapOf(
        BoothLayout.Classic to 1.43f,
        BoothLayout.Grid to 0.73f,
        BoothLayout.Trio to 1.06f,
        BoothLayout.Six to 1.12f,
        BoothLayout.Solo to 1.00f,
    )

    @Test
    fun cellCountMatchesCuts() {
        assertEquals(listOf(4, 4, 3, 6, 1), BoothLayout.entries.map { it.cuts })
        for (layout in BoothLayout.entries) assertEquals(layout.cuts, LayoutGeometry.cells(layout).size)
    }

    @Test
    fun cellsAreInsideTheCanvasAboveTheFooter() {
        for (layout in BoothLayout.entries) {
            val footerTop = layout.canvasHeight - layout.footer
            for (cell in LayoutGeometry.cells(layout)) {
                assertTrue("$layout $cell", cell.x >= layout.margin - 0.01f && cell.y >= layout.margin - 0.01f)
                assertTrue("$layout $cell", cell.right <= layout.canvasWidth - layout.margin + 0.01f)
                assertTrue("$layout $cell", cell.bottom <= footerTop + 0.01f)
            }
        }
    }

    @Test
    fun cellsFillTheGridExactly() {
        for (layout in BoothLayout.entries) {
            val cells = LayoutGeometry.cells(layout)
            assertEquals("$layout right edge", layout.canvasWidth - layout.margin.toFloat(), cells.maxOf { it.right }, 0.01f)
            assertEquals("$layout bottom edge", (layout.canvasHeight - layout.footer).toFloat(), cells.maxOf { it.bottom }, 0.01f)
        }
    }

    @Test
    fun cellsNeverOverlap() {
        for (layout in BoothLayout.entries) {
            val cells = LayoutGeometry.cells(layout)
            for (i in cells.indices) for (j in i + 1 until cells.size) {
                assertFalse("$layout cells $i and $j overlap", cells[i].overlaps(cells[j]))
            }
        }
    }

    @Test
    fun aspectMatchesSpec() {
        for (layout in BoothLayout.entries) {
            for (cell in LayoutGeometry.cells(layout)) {
                assertEquals("$layout aspect", expectedAspect.getValue(layout), cell.aspect, 0.01f)
            }
        }
    }

    @Test
    fun cellsAreRowMajor() {
        val six = LayoutGeometry.cells(BoothLayout.Six)
        assertEquals(six[0].y, six[1].y, 0f)
        assertTrue(six[1].x > six[0].x)
        assertTrue(six[2].y > six[1].y)
    }

    @Test
    fun classicNumbersAreExact() {
        assertEquals(SlotRect(72f, 72f, 1056f, 736f), LayoutGeometry.cells(BoothLayout.Classic).first())
    }
}
