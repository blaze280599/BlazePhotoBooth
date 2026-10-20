package com.blaze.photobooth.data.files

import com.blaze.photobooth.model.BackFlash
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.Crowd
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import kotlinx.serialization.Serializable

/**
 * The in-progress booth session, mirrored to `files/sessions/_draft/draft.json` (README §11.1, §11.4).
 * Shot files are `shot_{index}.jpg` next to it; their paths are derived, never stored (§11.2).
 * [selection], [caption] and [showDate] are Pick's (M4): [selection] maps slot → shot index (null = empty).
 * [posePlan] (M10, §5.6) is one pose id per shot; drafts from before M10 have none and get one on load
 * ([BoothDraftStore], from [createdAt], so it's the same plan every time until a swap is saved).
 */
@Serializable
data class BoothDraft(
    val version: Int = CurrentVersion,
    val createdAt: Long,
    val config: DraftConfig,
    val totalShots: Int,
    val shots: List<DraftShot> = emptyList(),
    val selection: List<Int?> = emptyList(),
    val caption: String? = null,
    val showDate: Boolean = true,
    val posePlan: List<String> = emptyList(),
) {
    /** The strip's cut count, as Setup planned it (cuts + bonus = [totalShots]). */
    val cuts: Int get() = (totalShots - config.bonusShots).coerceIn(1, totalShots.coerceAtLeast(1))

    /** The pose planned for shot [index] (§5.6), or null without one. */
    fun poseFor(index: Int): String? = posePlan.getOrNull(index)

    val taken: Int get() = shots.size
    val complete: Boolean get() = shots.size >= totalShots

    /** The first shot index not taken yet, or null when the draft is complete. */
    val nextIndex: Int? get() = (0 until totalShots).firstOrNull { i -> shots.none { it.index == i } }

    companion object {
        const val CurrentVersion = 1
    }
}

/**
 * One stored shot (§9.4): its sequence index and the stored JPEG's size. [takenAt] (epoch millis) is
 * stamped by [BoothDraftStore.addShot], so a retake of the same index is a different value (Pick reloads it).
 */
@Serializable
data class DraftShot(val index: Int, val width: Int, val height: Int, val takenAt: Long = 0)

/**
 * [BoothConfig] as stable string ids (the same ones DataStore and the `sessions` table use),
 * so renaming a Kotlin enum never breaks a saved draft.
 */
@Serializable
data class DraftConfig(
    val layout: String,
    val frame: String,
    val filter: String,
    val timerSec: Int,
    val bonusShots: Int,
    val lens: String,
    val mirror: Boolean,
    val backFlash: String = BackFlash.Default.id,
    val crowd: String = Crowd.Default.id,
) {
    /** Unknown values fall back to the defaults one field at a time, like the preferences do. */
    fun toConfig(): BoothConfig {
        val d = BoothConfig()
        return BoothConfig(
            layout = BoothLayout.fromId(layout) ?: d.layout,
            frameId = FrameId(frame).takeIf { it.kind != null } ?: d.frameId,
            filter = PhotoFilter.fromId(filter) ?: d.filter,
            timerSec = timerSec.takeIf { it in BoothConfig.TimerChoices } ?: d.timerSec,
            bonusShots = bonusShots.takeIf { it in BoothConfig.BonusChoices } ?: d.bonusShots,
            lens = Lens.fromId(lens) ?: d.lens,
            mirror = mirror,
            backFlash = BackFlash.fromId(backFlash) ?: d.backFlash,
            crowd = Crowd.fromId(crowd) ?: d.crowd,
        )
    }

    companion object {
        fun from(c: BoothConfig) = DraftConfig(
            layout = c.layout.id,
            frame = c.frameId.value,
            filter = c.filter.id,
            timerSec = c.timerSec,
            bonusShots = c.bonusShots,
            lens = c.lens.id,
            mirror = c.mirror,
            backFlash = c.backFlash.id,
            crowd = c.crowd.id,
        )
    }
}
