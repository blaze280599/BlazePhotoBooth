package com.blaze.photobooth.ui.feature.pick

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.SlotSelection
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.ui.feature.setup.FrameCardState

/**
 * Everything Pick shows (README §4.4). [preview] is the live strip (preview-size artwork, caption and date
 * already in it); [shots] is the tray, in the order they were taken; [selection] says which shot sits in each
 * slot. Bitmaps here are preview-size (≤ 600 px).
 */
@Immutable
data class PickUiState(
    /** Edit mode (§4.7): a saved strip is being changed. Save instead of Print, no retakes. */
    val editing: Boolean = false,
    val loaded: Boolean = false,
    val preview: StripSpec? = null,
    val shots: List<PickShot> = emptyList(),
    val selection: SlotSelection = SlotSelection(emptyList()),
    /** Her frames and the themes that have no more slots than there are shots. */
    val frames: List<FrameCardState> = emptyList(),
    val frameId: FrameId? = null,
    val filter: PhotoFilter = PhotoFilter.Original,
    val caption: String = "",
    val showDate: Boolean = true,
    /** Shots 1…cuts are the strip; the rest are bonus shots (only a hint in the tray). */
    val cuts: Int = 0,
    val printing: Boolean = false,
    /** The shot being peeked at full screen (long-press), with a bigger decode once it's ready. */
    val peek: PickShot? = null,
    val peekImage: ImageBitmap? = null,
    /** The draft (or the saved session being edited) is gone: the route leaves. */
    val missing: Boolean = false,
) {
    /** The preview-size photo in each slot, for the renderer. */
    val slotPhotos: List<Bitmap?>
        get() = selection.slots.map { index -> index?.let { i -> shots.firstOrNull { it.index == i }?.bitmap } }

    val canPrint: Boolean get() = loaded && selection.complete && !printing

    /**
     * Caption and date print in a theme's footer or an image frame's `textArea` (§5.5.1). Her frames have no
     * text area, so the Text tab explains that instead.
     */
    val textPrints: Boolean
        get() = when (val frame = preview?.frame) {
            is FrameSource.Image -> frame.frame.textArea != null
            else -> true
        }
}

/**
 * One shot in the tray. [bitmap] is the stored shot downsampled to ≤ 600 px (unfiltered; the filter is applied
 * when drawn). [version] changes when the shot is retaken.
 */
@Immutable
data class PickShot(val index: Int, val bitmap: Bitmap, val image: ImageBitmap, val bonus: Boolean, val version: Long) {
    val aspect: Float get() = bitmap.width.toFloat() / bitmap.height
}

sealed interface PickEvent {
    /** Tap a tray shot: place it in the next empty slot, or take it out again. */
    data class TapShot(val index: Int) : PickEvent

    /** Tap a filled slot (or a shot's badge): empty that slot. */
    data class TapSlot(val slot: Int) : PickEvent

    /** Long-press a shot: peek at it full screen. */
    data class Peek(val index: Int) : PickEvent
    data object DismissPeek : PickEvent

    /** From the peek: shoot this one again (§4.4). */
    data class Retake(val index: Int) : PickEvent

    data class SelectFrame(val id: FrameId) : PickEvent
    data class SelectFilter(val filter: PhotoFilter) : PickEvent
    data class SetCaption(val text: String) : PickEvent
    data class SetShowDate(val show: Boolean) : PickEvent
    data object Print : PickEvent

    /** ✕: "Discard session?" */
    data object Close : PickEvent

    /** Back: "Retake everything?" (§3) */
    data object Back : PickEvent
}

/** One-shot things the route turns into haptics, snackbars and navigation. */
sealed interface PickEffect {
    /** A shot landed in a slot (§7 A8 starts the fly-in from the tray). */
    data class Placed(val shot: Int, val slot: Int) : PickEffect

    /** A slot was emptied, or a shot came out. */
    data object Cleared : PickEffect

    /** Print with empty slots, or a tap with every slot full: shake and `REJECT` (§6.5, §13). */
    data object Rejected : PickEffect
    data class Printed(val sessionId: String) : PickEffect
    data object PrintFailed : PickEffect
}
