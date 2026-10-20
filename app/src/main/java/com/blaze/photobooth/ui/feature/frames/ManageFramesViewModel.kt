package com.blaze.photobooth.ui.feature.frames

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.frames.CustomFrameStore
import com.blaze.photobooth.data.frames.FrameDeletion
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.data.frames.ManagedFrame
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.ImageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One frame in Manage frames (§4.8). [thumbnail] is the 300 px swatch, null while it loads. */
@Immutable
data class ManagedFrameRow(val frame: ImageFrame, val bundled: Boolean, val hidden: Boolean, val thumbnail: Bitmap? = null) {
    val id: FrameId get() = frame.id
    val name: String get() = frame.name
    val windows: Int get() = frame.slots.size
}

/** Delete's question: [strips] saved strips use the frame (0 = it goes for good). */
@Immutable
data class DeleteQuestion(val row: ManagedFrameRow, val strips: Int)

@Immutable
data class ManageFramesUiState(
    val bundled: List<ManagedFrameRow> = emptyList(),
    val imported: List<ManagedFrameRow> = emptyList(),
    val loaded: Boolean = false,
    /** The frame being renamed, and the text so far. */
    val renaming: ManagedFrameRow? = null,
    val renameText: String = "",
    val deleting: DeleteQuestion? = null,
)

sealed interface ManageFramesEvent {
    data object Back : ManageFramesEvent
    data object Add : ManageFramesEvent
    data class SetHidden(val id: FrameId, val hidden: Boolean) : ManageFramesEvent
    data class Move(val id: FrameId, val by: Int) : ManageFramesEvent
    data class Rename(val id: FrameId) : ManageFramesEvent
    data class SetRenameText(val text: String) : ManageFramesEvent
    data object ConfirmRename : ManageFramesEvent
    data object CancelRename : ManageFramesEvent
    data class Delete(val id: FrameId) : ManageFramesEvent
    data object ConfirmDelete : ManageFramesEvent
    data object CancelDelete : ManageFramesEvent
}

sealed interface ManageFramesMessage {
    data class Deleted(val name: String) : ManageFramesMessage
    data class KeptHidden(val name: String) : ManageFramesMessage
}

/**
 * Manage frames (README §4.8): her bundled frames (hide only) and her imported ones (rename, reorder, hide, delete).
 * Deleting a frame that saved strips use hides it instead (§11.2).
 */
class ManageFramesViewModel(
    private val frames: FrameRepository,
    private val custom: CustomFrameStore,
    private val setBundledHidden: suspend (FrameId, Boolean) -> Unit,
    private val stripsUsing: suspend (FrameId) -> Int,
) : ViewModel() {

    private val state = MutableStateFlow(ManageFramesUiState())
    val uiState: StateFlow<ManageFramesUiState> = state.asStateFlow()

    private val messageChannel = Channel<ManageFramesMessage>(Channel.BUFFERED)
    val messages: Flow<ManageFramesMessage> = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            frames.managed.collect { list -> show(list) }
        }
    }

    private fun show(list: List<ManagedFrame>) {
        state.update { s ->
            val known = (s.bundled + s.imported).associateBy { it.id }
            fun row(m: ManagedFrame) = ManagedFrameRow(m.frame, m.bundled, m.hidden, known[m.frame.id]?.thumbnail)
            s.copy(bundled = list.filter { it.bundled }.map(::row), imported = list.filterNot { it.bundled }.map(::row), loaded = true)
        }
        list.forEach { m ->
            if ((state.value.bundled + state.value.imported).any { it.id == m.frame.id && it.thumbnail != null }) return@forEach
            viewModelScope.launch {
                val thumb = try {
                    frames.thumbnail(m.frame)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(Tag, "No thumbnail for ${m.frame.id}", e)
                    return@launch
                }
                state.update { s ->
                    fun List<ManagedFrameRow>.withThumb() = map { if (it.id == m.frame.id) it.copy(thumbnail = thumb) else it }
                    s.copy(bundled = s.bundled.withThumb(), imported = s.imported.withThumb())
                }
            }
        }
    }

    fun onEvent(event: ManageFramesEvent) {
        when (event) {
            ManageFramesEvent.Back, ManageFramesEvent.Add -> Unit // the route's
            is ManageFramesEvent.SetHidden -> launch {
                if (event.id.kind == FrameId.Kind.Asset) setBundledHidden(event.id, event.hidden) else custom.setHidden(event.id, event.hidden)
            }
            is ManageFramesEvent.Move -> launch { custom.move(event.id, event.by) }
            is ManageFramesEvent.Rename -> {
                val row = state.value.imported.firstOrNull { it.id == event.id } ?: return
                state.update { it.copy(renaming = row, renameText = row.name) }
            }
            is ManageFramesEvent.SetRenameText ->
                state.update { it.copy(renameText = event.text.replace('\n', ' ').take(CustomFrameStore.MaxName)) }
            ManageFramesEvent.ConfirmRename -> {
                val row = state.value.renaming ?: return
                val name = state.value.renameText.trim()
                state.update { it.copy(renaming = null) }
                if (name.isNotEmpty() && name != row.name) launch { custom.rename(row.id, name) }
            }
            ManageFramesEvent.CancelRename -> state.update { it.copy(renaming = null) }
            is ManageFramesEvent.Delete -> {
                val row = state.value.imported.firstOrNull { it.id == event.id } ?: return
                launch { state.update { it.copy(deleting = DeleteQuestion(row, stripsUsing(row.id))) } }
            }
            ManageFramesEvent.ConfirmDelete -> {
                val question = state.value.deleting ?: return
                state.update { it.copy(deleting = null) }
                launch {
                    when (custom.delete(question.row.id)) {
                        FrameDeletion.Deleted -> messageChannel.send(ManageFramesMessage.Deleted(question.row.name))
                        is FrameDeletion.KeptHidden -> messageChannel.send(ManageFramesMessage.KeptHidden(question.row.name))
                        FrameDeletion.NotFound -> Unit
                    }
                }
            }
            ManageFramesEvent.CancelDelete -> state.update { it.copy(deleting = null) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Frame change failed", e)
            }
        }
    }

    companion object {
        private const val Tag = "ManageFrames"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (checkNotNull(this[APPLICATION_KEY]) as BoothApp).container
                val sessions = c.database.sessions()
                ManageFramesViewModel(
                    frames = c.frameRepository,
                    custom = c.customFrames,
                    setBundledHidden = c.boothPreferences::setBundledFrameHidden,
                    stripsUsing = { sessions.countUsing(it.value) },
                )
            }
        }
    }
}
