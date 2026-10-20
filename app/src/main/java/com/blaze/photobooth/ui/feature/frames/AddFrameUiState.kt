package com.blaze.photobooth.ui.feature.frames

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.blaze.photobooth.model.EditorWindow
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.SnapGuide
import com.blaze.photobooth.model.WindowHandle
import com.blaze.photobooth.model.WindowNudge
import com.blaze.photobooth.model.WindowProblem

/** Where the import flow is (README §4.9). */
enum class AddFrameStep {
    /** The system Photo Picker is open. */
    Picking,

    /** "Finding the photo windows… ✨" while the detector runs. */
    Finding,

    /** The frame with its numbered windows: Looks right / Adjust, then the name. */
    Confirm,

    /** The slot editor. */
    Editor,

    /** Writing `frame.png`, `thumb.png` and the row. */
    Saving,

    /** The heart pop, then back to where she came from. */
    Saved,

    /** The picked file couldn't be read as an image. */
    Failed,
}

/**
 * Everything the import flow shows. Rects are in the frame's own pixels ([frameWidth] × [frameHeight]); bitmaps are
 * preview-sized (≤ 1600 px long edge).
 */
@Immutable
data class AddFrameUiState(
    val step: AddFrameStep = AddFrameStep.Picking,
    /** The picked image as it is (Finding, the editor). */
    val artwork: Bitmap? = null,
    /** The image with the windows see-through (Confirm). */
    val result: Bitmap? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** The confirmed windows, in strip order. */
    val windows: List<SlotRect> = emptyList(),
    /** True if the detector found [windows]; false once she has placed or adjusted them herself. */
    val found: Boolean = false,
    /** The picked file is under 600 px wide (§4.9 step 5). */
    val tooSmall: Boolean = false,
    val sourceWidth: Int = 0,
    val editor: EditorUiState? = null,
    /** Confirm's bottom panel asks for the name. */
    val naming: Boolean = false,
    val name: String = "",
    val saveFailed: Boolean = false,
    val savedId: FrameId? = null,
)

/** The slot editor (§4.9 step 4). [windows] are in strip order: window `i` shows number `i + 1`. */
@Immutable
data class EditorUiState(
    val windows: List<EditorWindow>,
    val selected: Int? = null,
    val guides: List<SnapGuide> = emptyList(),
    val problem: WindowProblem? = null,
    /** No windows were found, so the editor opened straight away and cancelling leaves the flow (§13). */
    val fromStart: Boolean = false,
    /** A one-off note: no room for another window, or already 8. */
    val note: EditorNote? = null,
) {
    val canAdd: Boolean get() = windows.size < com.blaze.photobooth.model.WindowEditor.MaxWindows
    val canDelete: Boolean get() = selected != null
    val canFinish: Boolean get() = problem == null
}

enum class EditorNote { NoRoom, Full }

sealed interface AddFrameEvent {
    data object Close : AddFrameEvent
    data object PickAnother : AddFrameEvent
    data object LooksRight : AddFrameEvent
    data object Adjust : AddFrameEvent
    data class SetName(val name: String) : AddFrameEvent
    data object CancelNaming : AddFrameEvent
    data object Save : AddFrameEvent

    // The slot editor.
    data object EditorCancel : AddFrameEvent
    data object EditorDone : AddFrameEvent
    data object AddWindow : AddFrameEvent
    data object DeleteWindow : AddFrameEvent
    data class Select(val id: Int?) : AddFrameEvent

    /** A finger went down on window [id]'s body or corner and started dragging. */
    data class DragStart(val id: Int, val handle: WindowHandle) : AddFrameEvent

    /** The total movement since [DragStart], and the snap distance, in frame pixels. */
    data class Drag(val dx: Float, val dy: Float, val snap: Float) : AddFrameEvent
    data object DragEnd : AddFrameEvent
    data class Nudge(val id: Int, val nudge: WindowNudge) : AddFrameEvent
}
