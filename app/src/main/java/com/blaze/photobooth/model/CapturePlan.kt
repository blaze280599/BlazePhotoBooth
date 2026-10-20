package com.blaze.photobooth.model

/**
 * What one booth session shoots (README §4.3, §5.5.1): one shot per slot, then the bonus shots.
 * Shot *i* is framed like slot *i*; bonus shots use the first slot's shape. Pure, so it's unit-tested.
 */
data class CapturePlan(val slots: List<SlotRect>, val bonusShots: Int) {

    init {
        require(slots.isNotEmpty()) { "A strip needs at least one slot" }
        require(bonusShots >= 0) { "Negative bonus shots: $bonusShots" }
    }

    val cuts: Int get() = slots.size
    val totalShots: Int get() = cuts + bonusShots

    /** The slot shot [index] is framed for. */
    fun slotFor(index: Int): Int = if (index in slots.indices) index else 0

    /** Width ÷ height of the viewfinder and the stored photo for shot [index]. */
    fun aspectFor(index: Int): Float = slots[slotFor(index)].aspect

    fun isBonus(index: Int): Boolean = index >= cuts
}
