package com.blaze.photobooth.ui.feature.setup

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec

/**
 * Everything Setup shows (README §4.2). [preview] is null only until the first frame has loaded.
 * Bitmaps here are small (preview artwork ≤ 600 px wide, thumbnails ~300 px).
 */
@Immutable
data class SetupUiState(
    val config: BoothConfig = BoothConfig(),
    val frames: List<FrameCardState> = emptyList(),
    val preview: StripSpec? = null,
    val filterSample: Bitmap? = null,
) {
    val selectedFrame: FrameOption? get() = frames.firstOrNull { it.option.id == config.frameId }?.option

    /** True when an image frame is chosen: its windows set the cut count, so the Layout tab is locked. */
    val layoutSetByFrame: Boolean get() = selectedFrame is FrameOption.Artwork

    val cuts: Int
        get() = when (val frame = selectedFrame) {
            is FrameOption.Artwork -> frame.frame.slots.size
            else -> config.layout.cuts
        }

    /** Cuts plus bonus shots: what "Let's go! · N shots" counts. */
    val totalShots: Int get() = cuts + config.bonusShots

    val ready: Boolean get() = preview != null
}

/** A frame card in the carousel. [thumbnail] is the artwork thumb for image frames (null for themes, or while loading). */
@Immutable
data class FrameCardState(val option: FrameOption, val thumbnail: Bitmap? = null)

sealed interface SetupEvent {
    data class SelectLayout(val layout: BoothLayout) : SetupEvent
    data class SelectFrame(val id: FrameId) : SetupEvent
    data class SelectFilter(val filter: PhotoFilter) : SetupEvent
    data class SetTimer(val seconds: Int) : SetupEvent
    data class SetBonus(val shots: Int) : SetupEvent
    data class SetLens(val lens: Lens) : SetupEvent
    data class SetMirror(val mirror: Boolean) : SetupEvent
    data object AddFrame : SetupEvent
    data object Start : SetupEvent
    data object Back : SetupEvent
}
