package com.blaze.photobooth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §4.4: tap to fill the next slot, tap again to clear, auto-fill on arrival, bonus shots stay spare. */
class SlotSelectionTest {

    private val sixShots = listOf(0, 1, 2, 3, 4, 5)

    @Test
    fun autoFillPlacesShotsOneToCutsInOrder() {
        val s = SlotSelection.autoFill(cuts = 4, shots = sixShots)
        assertEquals(listOf(0, 1, 2, 3), s.slots)
        assertTrue(s.complete)
        assertEquals(listOf(4, 5), s.unused(sixShots))
    }

    @Test
    fun autoFillWithFewerShotsThanCutsLeavesTheRestEmpty() {
        val s = SlotSelection.autoFill(cuts = 4, shots = listOf(2, 0))
        assertEquals(listOf(0, 2, null, null), s.slots)
        assertFalse(s.complete)
        assertEquals(2, s.nextEmpty)
    }

    @Test
    fun tapFillsTheNextEmptySlot() {
        val s = SlotSelection(listOf(0, null, 2, null)).toggle(5)
        assertEquals(listOf(0, 5, 2, null), s.slots)
        assertEquals(1, s.slotOf(5))
        assertEquals(listOf(0, 5, 2, 4), s.toggle(4).slots)
    }

    @Test
    fun tappingAPlacedShotTakesItOut() {
        val s = SlotSelection(listOf(0, 1, 2, 3)).toggle(1)
        assertEquals(listOf(0, null, 2, 3), s.slots)
        assertNull(s.slotOf(1))
    }

    @Test
    fun withEverySlotFullATapChangesNothing() {
        val full = SlotSelection(listOf(0, 1, 2))
        assertSame(full, full.toggle(4))
        assertSame(full, full.place(4))
    }

    @Test
    fun clearingASlotEmptiesOnlyThatSlot() {
        val s = SlotSelection(listOf(3, 1, 2)).clearSlot(0)
        assertEquals(listOf(null, 1, 2), s.slots)
        // Already empty or out of range: unchanged.
        assertSame(s, s.clearSlot(0))
        assertSame(s, s.clearSlot(7))
        // The freed slot is the next one filled.
        assertEquals(listOf(5, 1, 2), s.toggle(5).slots)
    }

    @Test
    fun aShotIsNeverInTwoSlots() {
        val s = SlotSelection(listOf(0, null, null)).place(0)
        assertEquals(listOf(0, null, null), s.slots)
    }

    @Test
    fun bonusShotsSwapInForAClearedCut() {
        // Classic strip, 6 shots: drop cut 2, put bonus shot 5 in its place.
        val s = SlotSelection.autoFill(4, sixShots).clearSlot(1).toggle(5)
        assertEquals(listOf(0, 5, 2, 3), s.slots)
        assertEquals(listOf(1, 4), s.unused(sixShots))
    }

    @Test
    fun resizeKeepsPicksThatFitAndTopsUpTheRest() {
        // Her 3-window frame → a 4-cut theme: the 4th slot gets the first unused shot.
        val grown = SlotSelection(listOf(4, 0, 2)).resize(4, listOf(0, 1, 2, 3, 4))
        assertEquals(listOf(4, 0, 2, 1), grown.slots)
        // And back to 3: the last pick drops out.
        assertEquals(listOf(4, 0, 2), grown.resize(3, listOf(0, 1, 2, 3, 4)).slots)
    }

    @Test
    fun restoreCleansASavedSelection() {
        val shots = listOf(0, 1, 2, 3, 4)
        // Never picked → auto-fill.
        assertEquals(listOf(0, 1, 2), SlotSelection.restore(emptyList(), 3, shots).slots)
        // Unknown shots and duplicates are dropped; the size follows the cuts.
        assertEquals(listOf(4, null, null), SlotSelection.restore(listOf(4, 4, 9, 1), 3, shots).slots)
        assertEquals(listOf(2, 1, null, null), SlotSelection.restore(listOf(2, 1), 4, shots).slots)
    }
}
