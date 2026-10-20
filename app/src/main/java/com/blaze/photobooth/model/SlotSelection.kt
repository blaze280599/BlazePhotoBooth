package com.blaze.photobooth.model

/**
 * Which shot sits in each strip slot on Pick (README §4.4). [slots] has one entry per cut: a shot index,
 * or null for an empty slot. A shot is in at most one slot. Pure and immutable, so it's unit-tested.
 */
data class SlotSelection(val slots: List<Int?>) {

    val cuts: Int get() = slots.size
    val complete: Boolean get() = slots.isNotEmpty() && slots.none { it == null }
    val filled: Int get() = slots.count { it != null }

    /** The first empty slot, or null when every slot is filled. */
    val nextEmpty: Int? get() = slots.indexOfFirst { it == null }.takeIf { it >= 0 }

    /** The slot [shot] is in, or null. */
    fun slotOf(shot: Int): Int? = slots.indexOf(shot).takeIf { it >= 0 }

    /**
     * Tap on a tray shot: a placed shot comes out of its slot; otherwise it fills the next empty slot.
     * With every slot full, nothing changes ([place] returns this same selection).
     */
    fun toggle(shot: Int): SlotSelection = if (slotOf(shot) != null) removeShot(shot) else place(shot)

    fun place(shot: Int): SlotSelection {
        if (slotOf(shot) != null) return this
        val slot = nextEmpty ?: return this
        return SlotSelection(slots.toMutableList().also { it[slot] = shot })
    }

    /** Tap on a filled slot (or its badge): empty it. */
    fun clearSlot(slot: Int): SlotSelection {
        if (slot !in slots.indices || slots[slot] == null) return this
        return SlotSelection(slots.toMutableList().also { it[slot] = null })
    }

    fun removeShot(shot: Int): SlotSelection = slotOf(shot)?.let(::clearSlot) ?: this

    /** Shots from [shots] that aren't in any slot: the unused bonus cuts. */
    fun unused(shots: List<Int>): List<Int> = shots.filter { slotOf(it) == null }

    /**
     * A different frame was chosen with [cuts] slots: picks that still fit keep their places, and any
     * empty slots are topped up from the unused [shots] in order, so the strip stays printable.
     */
    fun resize(cuts: Int, shots: List<Int>): SlotSelection {
        val kept = List(cuts) { slots.getOrNull(it) }
        return SlotSelection(kept).fill(shots)
    }

    /** Fills every empty slot, in order, with unused [shots] in order. */
    fun fill(shots: List<Int>): SlotSelection {
        var next = this
        for (shot in unused(shots)) {
            if (next.complete) break
            next = next.place(shot)
        }
        return next
    }

    companion object {
        /** On arrival (§4.4): shots 1…cuts in order. Fewer shots than cuts leave the rest empty. */
        fun autoFill(cuts: Int, shots: List<Int>): SlotSelection = SlotSelection(List(cuts) { null }).fill(shots.sorted())

        /**
         * A saved selection read back from the draft: unknown shots and duplicates are dropped, the size is
         * fixed to [cuts]. An empty or unusable list means "never picked", so it's auto-filled.
         */
        fun restore(saved: List<Int?>, cuts: Int, shots: List<Int>): SlotSelection {
            if (saved.none { it != null }) return autoFill(cuts, shots)
            val seen = mutableSetOf<Int>()
            val clean = List(cuts) { i -> saved.getOrNull(i)?.takeIf { it in shots && seen.add(it) } }
            return SlotSelection(clean)
        }
    }
}
