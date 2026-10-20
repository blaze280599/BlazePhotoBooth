package com.blaze.photobooth.data.files

import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import kotlinx.serialization.Serializable

/**
 * The in-progress booth session, mirrored to `files/sessions/_draft/draft.json` (README §11.1, §11.4).
 * Shot files are `shot_{index}.jpg` next to it; their paths are derived, never stored (§11.2).
 * [selection] and [caption] are Pick's (M4): slot → shot index.
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
) {
    val taken: Int get() = shots.size
    val complete: Boolean get() = shots.size >= totalShots

    /** The first shot index not taken yet, or null when the draft is complete. */
    val nextIndex: Int? get() = (0 until totalShots).firstOrNull { i -> shots.none { it.index == i } }

    companion object {
        const val CurrentVersion = 1
    }
}

/** One stored shot (§9.4): its sequence index and the stored JPEG's size. */
@Serializable
data class DraftShot(val index: Int, val width: Int, val height: Int)

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
        )
    }
}
