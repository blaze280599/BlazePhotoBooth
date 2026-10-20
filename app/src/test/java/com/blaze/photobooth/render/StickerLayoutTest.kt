package com.blaze.photobooth.render

import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.Decoration
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.SlotRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §5.2: stickers are deterministic and keep off faces (≤ 12 px into any cell) and the footer text. */
class StickerLayoutTest {

    @Test
    fun stickersNeverCoverFaces() {
        for (theme in FrameTheme.entries) for (layout in BoothLayout.entries) {
            val cells = LayoutGeometry.cells(layout)
            val text = StickerLayout.footerTextBlock(layout)
            for (s in StickerLayout.place(theme, layout)) {
                val box = SlotRect(s.cx - s.size / 2, s.cy - s.size / 2, s.size, s.size)
                for (cell in cells) {
                    assertTrue("$theme/$layout $s", StickerLayout.penetration(box, cell) <= StickerLayout.MaxCellOverlap)
                }
                assertEquals("$theme/$layout $s covers the footer text", 0f, StickerLayout.penetration(box, text), 0f)
            }
        }
    }

    @Test
    fun placementIsDeterministic() {
        val first = StickerLayout.place(FrameTheme.Strawberry, BoothLayout.Grid).toList()
        assertEquals(first, StickerLayout.place(FrameTheme.Strawberry, BoothLayout.Grid))
    }

    @Test
    fun decoratedThemesGetStickersAndPlainOnesDont() {
        for (theme in FrameTheme.entries) for (layout in BoothLayout.entries) {
            val count = StickerLayout.place(theme, layout).size
            when (theme.decoration) {
                Decoration.None, Decoration.Sprockets -> assertEquals(0, count)
                else -> assertTrue("$theme/$layout has only $count stickers", count >= 8)
            }
        }
    }

    @Test
    fun penetrationMeasuresTheShallowerOverlap() {
        val cell = SlotRect(100f, 100f, 500f, 500f)
        assertEquals(0f, StickerLayout.penetration(SlotRect(0f, 0f, 50f, 50f), cell), 0f)
        assertEquals(10f, StickerLayout.penetration(SlotRect(60f, 200f, 50f, 50f), cell), 0f)
    }
}
