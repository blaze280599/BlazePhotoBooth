package com.blaze.photobooth.model

enum class Lens(val id: String) {
    Front("front"),
    Back("back"),
    ;

    companion object {
        fun fromId(id: String?): Lens? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Everything chosen on Setup (README §4.2, §5.4). [layout] is kept even while an image frame is
 * selected (which sets its own cut count), so switching back to a theme restores it.
 */
data class BoothConfig(
    val layout: BoothLayout = BoothLayout.Default,
    val frameId: FrameId = FrameId.theme(FrameTheme.Default),
    val filter: PhotoFilter = PhotoFilter.Default,
    val timerSec: Int = DefaultTimer,
    val bonusShots: Int = DefaultBonus,
    val lens: Lens = Lens.Front,
    val mirror: Boolean = true,
) {
    companion object {
        val TimerChoices = listOf(3, 5, 10)
        val BonusChoices = listOf(0, 2, 4)
        const val DefaultTimer = 5
        const val DefaultBonus = 2
    }
}

/**
 * A frame's stable id, as stored in DataStore and the `sessions.frame_id` column (§11.2):
 * `theme:<id>` (built-in, §5.2), `asset:<id>` (bundled image frame, §5.5.6) or `custom:<uuid>` (imported, §4.9).
 */
@JvmInline
value class FrameId(val value: String) {

    val kind: Kind?
        get() = Kind.entries.firstOrNull { value.startsWith(it.prefix) && value.length > it.prefix.length }

    /** The id after the prefix, e.g. `strawberry` for `theme:strawberry`. */
    val localId: String get() = value.substringAfter(':')

    override fun toString(): String = value

    enum class Kind(val prefix: String) { Theme("theme:"), Asset("asset:"), Custom("custom:") }

    companion object {
        fun theme(theme: FrameTheme) = FrameId("theme:${theme.id}")
        fun asset(id: String) = FrameId("asset:$id")
        fun custom(uuid: String) = FrameId("custom:$uuid")
    }
}
