package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.render.SyntheticFrames.Art
import com.blaze.photobooth.render.SyntheticFrames.Box
import com.blaze.photobooth.render.SyntheticFrames.Clear
import com.blaze.photobooth.render.SyntheticFrames.Pink
import com.blaze.photobooth.render.SyntheticFrames.White
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §4.9 step 4: windows she draws on an opaque frame are cut out; found ones keep their keyed look. */
class FrameCutoutTest {

    @Test
    fun aCutWindowIsClearInsideWithASoftInnerEdge() {
        val art = Art(400, 400)
        val rect = SlotRect(100f, 100f, 200f, 120f)
        FrameCutout.cut(art.px, art.w, art.h, rect, radius = 12f, feather = 2f)
        assertEquals("middle", 0, art.alphaAt(200, 160))
        // Pixel centres 0.5, 1.5, 2.5 px inside the left edge: 3/4, 1/4, then clear.
        assertEquals(191, art.alphaAt(100, 160))
        assertEquals(64, art.alphaAt(101, 160))
        assertEquals(0, art.alphaAt(102, 160))
        assertEquals("outside", 255, art.alphaAt(99, 160))
        // The rounded corner keeps the artwork in the rect's very corner.
        assertEquals(255, art.alphaAt(100, 100))
        // Colour is never changed, only alpha.
        assertEquals(Pink and 0xFFFFFF, art.px[160 * art.w + 200] and 0xFFFFFF)
    }

    @Test
    fun eachWindowGetsTheRightTreatment() {
        // Left: a transparent window. Right: a white box the detector keyed. Bottom: opaque, where she drew a new one.
        val art = Art(600, 1200)
        val open = Box(40, 40, 240, 300)
        val box = Box(320, 40, 240, 300)
        art.fill(open, Clear).fill(box, White)
        val keyed = SyntheticFrames.Art(600, 1200).also { k -> art.px.copyInto(k.px) }.fill(box, Clear)
        val drawn = SlotRect(100f, 600f, 400f, 300f)
        val actions = FrameCutout.plan(art.px, art.w, art.h, 1f, keyedSlots = listOf(box.slot), windows = listOf(open.slot, box.slot, drawn))
        assertEquals(
            listOf(FrameCutout.Action.AlreadyOpen(open.slot), FrameCutout.Action.KeepKeyed(box.slot), FrameCutout.Action.Cut(drawn)),
            actions,
        )
        val out = art.px.copyOf()
        FrameCutout.apply(out, keyed.px, art.w, art.h, 1f, actions, keyPad = 7f)
        fun a(x: Int, y: Int) = out[y * art.w + x] ushr 24
        assertEquals("transparent window stays open", 0, a(160, 190))
        assertEquals("keyed box is see-through", 0, a(440, 190))
        assertEquals("her new window is cut", 0, a(300, 750))
        assertEquals("the frame between stays", 255, a(300, 450))
    }

    @Test
    fun aMovedOrRemovedBoxGetsItsColourBack() {
        val art = Art(600, 1200)
        val box = Box(100, 100, 400, 300)
        art.fill(box, White)
        val keyed = Art(600, 1200).also { k -> art.px.copyInto(k.px) }.fill(box, Clear)
        // She dragged the found window down by 500 px.
        val moved = SlotRect(100f, 600f, 400f, 300f)
        val actions = FrameCutout.plan(art.px, art.w, art.h, 1f, listOf(box.slot), listOf(moved))
        assertEquals(listOf(FrameCutout.Action.Cut(moved)), actions)
        val out = art.px.copyOf()
        FrameCutout.apply(out, keyed.px, art.w, art.h, 1f, actions, keyPad = 7f)
        assertEquals("old place: the white box is back", White, out[250 * art.w + 300])
        assertEquals("new place: cut", 0, out[750 * art.w + 300] ushr 24)
        // Removed entirely: nothing to apply, the original is untouched.
        val none = FrameCutout.plan(art.px, art.w, art.h, 1f, listOf(box.slot), emptyList())
        assertTrue(none.isEmpty())
    }

    @Test
    fun thePreviewUsesTheSameRulesAtItsOwnScale() {
        // Full size 1200 × 2400, preview at half size: a full-size window maps to half-size pixels.
        val preview = Art(600, 1200)
        val window = SlotRect(200f, 400f, 800f, 600f)
        val actions = FrameCutout.plan(preview.px, preview.w, preview.h, 0.5f, emptyList(), listOf(window))
        FrameCutout.apply(preview.px, null, preview.w, preview.h, 0.5f, actions, keyPad = 8f)
        assertEquals(0, preview.alphaAt(300, 350))
        assertEquals(255, preview.alphaAt(90, 350))
        assertEquals(255, preview.alphaAt(300, 190))
    }
}
