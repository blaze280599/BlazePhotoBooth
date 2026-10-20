package com.blaze.photobooth.ui.feature.frames

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.R
import com.blaze.photobooth.data.frames.CustomFrameStore
import com.blaze.photobooth.data.frames.FrameImporter
import com.blaze.photobooth.data.frames.ImportedFrame
import com.blaze.photobooth.model.EditorWindow
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.WindowEditor
import com.blaze.photobooth.model.WindowHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddFrameEffect {
    /** Leave the flow; nothing was saved. */
    data object Exit : AddFrameEffect

    /** Open the Photo Picker again. */
    data object LaunchPicker : AddFrameEffect
}

/**
 * The import flow (README §4.9): the picked image is decoded and the detector runs while "Finding the photo windows…"
 * plays; then Confirm (Looks right → name → Save) or the slot editor. Nothing is written until Save.
 */
class AddFrameViewModel(
    private val importer: FrameImporter,
    private val defaultName: suspend () -> String,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val state = MutableStateFlow(AddFrameUiState())
    val uiState: StateFlow<AddFrameUiState> = state.asStateFlow()

    private val effectChannel = Channel<AddFrameEffect>(Channel.BUFFERED)
    val effects: Flow<AddFrameEffect> = effectChannel.receiveAsFlow()

    private var frame: ImportedFrame? = null
    private var geometry: WindowEditor? = null
    private var openJob: Job? = null
    private var nextId = 0

    private var dragId: Int? = null
    private var dragHandle = WindowHandle.Body
    private var dragStart: SlotRect? = null

    /** The Photo Picker returned [uri]. */
    fun open(uri: Uri) {
        if (openJob?.isActive == true) return
        state.value = AddFrameUiState(step = AddFrameStep.Finding)
        openJob = viewModelScope.launch {
            val started = now()
            val f = try {
                state.update { it.copy(artwork = importer.quickPreview(uri)) }
                importer.open(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(Tag, "Couldn't open the picked image", e)
                state.update { it.copy(step = AddFrameStep.Failed) }
                return@launch
            }
            frame = f
            geometry = WindowEditor(f.width, f.height)
            val a = f.analysis
            val result = if (a.found) importer.cutPreview(f, a.windows) else null
            // Let the sparkles play for a moment, even when detection is instant.
            delay((MinFindingMillis - (now() - started)).coerceAtLeast(0))
            state.update {
                it.copy(
                    artwork = f.preview,
                    result = result,
                    frameWidth = f.width,
                    frameHeight = f.height,
                    windows = a.windows,
                    found = a.found,
                    tooSmall = a.tooSmall,
                    sourceWidth = f.sourceWidth,
                )
            }
            if (a.found) {
                state.update { it.copy(step = AddFrameStep.Confirm) }
            } else {
                // No windows (§13): straight to the editor with one default window, selected so its handles show.
                openEditor(a.windows, fromStart = true, selectFirst = true)
            }
        }
    }

    fun onEvent(event: AddFrameEvent) {
        when (event) {
            AddFrameEvent.Close -> effectChannel.trySend(AddFrameEffect.Exit)
            AddFrameEvent.PickAnother -> {
                state.value = AddFrameUiState()
                effectChannel.trySend(AddFrameEffect.LaunchPicker)
            }
            AddFrameEvent.LooksRight -> viewModelScope.launch {
                val name = state.value.name.ifBlank { defaultName() }
                state.update { it.copy(naming = true, name = name, saveFailed = false) }
            }
            AddFrameEvent.Adjust -> openEditor(state.value.windows, fromStart = false, selectFirst = false)
            is AddFrameEvent.SetName -> state.update { it.copy(name = event.name.replace('\n', ' ').take(CustomFrameStore.MaxName)) }
            AddFrameEvent.CancelNaming -> state.update { it.copy(naming = false, saveFailed = false) }
            AddFrameEvent.Save -> save()
            AddFrameEvent.EditorCancel -> {
                if (state.value.editor?.fromStart == true) {
                    effectChannel.trySend(AddFrameEffect.Exit)
                } else {
                    state.update { it.copy(step = AddFrameStep.Confirm, editor = null) }
                }
            }
            AddFrameEvent.EditorDone -> finishEditing()
            AddFrameEvent.AddWindow -> addWindow()
            AddFrameEvent.DeleteWindow -> editWindows { windows, selected ->
                windows.filterNot { it.id == selected } to null
            }
            is AddFrameEvent.Select -> updateEditor { it.copy(selected = event.id, note = null) }
            is AddFrameEvent.DragStart -> {
                val w = state.value.editor?.windows?.firstOrNull { it.id == event.id } ?: return
                dragId = w.id
                dragHandle = event.handle
                dragStart = w.rect
                updateEditor { it.copy(selected = w.id, note = null) }
            }
            is AddFrameEvent.Drag -> drag(event.dx, event.dy, event.snap)
            AddFrameEvent.DragEnd -> {
                dragId = null
                dragStart = null
                updateEditor { it.copy(guides = emptyList()) }
            }
            is AddFrameEvent.Nudge -> {
                val g = geometry ?: return
                editWindows(select = event.id) { windows, _ ->
                    windows.map { if (it.id == event.id) it.copy(rect = g.nudge(it.rect, event.nudge)) else it } to event.id
                }
            }
        }
    }

    private fun openEditor(rects: List<SlotRect>, fromStart: Boolean, selectFirst: Boolean) {
        val g = geometry ?: return
        val windows = rects.map { EditorWindow(nextId++, it) }
        val editor = EditorUiState(
            windows = g.ordered(windows),
            selected = windows.firstOrNull()?.id?.takeIf { selectFirst },
            problem = g.problem(windows),
            fromStart = fromStart,
        )
        state.update { it.copy(step = AddFrameStep.Editor, editor = editor) }
    }

    private fun drag(dx: Float, dy: Float, snap: Float) {
        val g = geometry ?: return
        val id = dragId ?: return
        val start = dragStart ?: return
        val editor = state.value.editor ?: return
        val others = editor.windows.filter { it.id != id }.map { it.rect }
        val moved = g.drag(others, start, dragHandle, dx, dy, snap)
        val windows = editor.windows.map { if (it.id == id) it.copy(rect = moved.rect) else it }
        updateEditor { it.copy(windows = g.ordered(windows), guides = moved.guides, problem = g.problem(windows)) }
    }

    private fun addWindow() {
        val g = geometry ?: return
        val editor = state.value.editor ?: return
        if (!editor.canAdd) {
            updateEditor { it.copy(note = EditorNote.Full) }
            return
        }
        val rect = g.add(editor.windows)
        if (rect == null) {
            updateEditor { it.copy(note = EditorNote.NoRoom) }
            return
        }
        val id = nextId++
        editWindows(select = id) { windows, _ -> (windows + EditorWindow(id, rect)) to id }
    }

    /** Changes the windows ([change] returns them and the new selection), renumbering and re-checking them. */
    private fun editWindows(select: Int? = null, change: (List<EditorWindow>, Int?) -> Pair<List<EditorWindow>, Int?>) {
        val g = geometry ?: return
        val editor = state.value.editor ?: return
        val (windows, selected) = change(editor.windows, editor.selected ?: select)
        updateEditor { it.copy(windows = g.ordered(windows), selected = selected, problem = g.problem(windows), note = null) }
    }

    private fun updateEditor(change: (EditorUiState) -> EditorUiState) {
        state.update { s -> s.editor?.let { s.copy(editor = change(it)) } ?: s }
    }

    private fun finishEditing() {
        val f = frame ?: return
        val editor = state.value.editor ?: return
        if (!editor.canFinish) return
        val rects = editor.windows.map { it.rect }
        viewModelScope.launch {
            val result = importer.cutPreview(f, rects)
            state.update { it.copy(step = AddFrameStep.Confirm, editor = null, windows = rects, found = false, result = result) }
        }
    }

    private fun save() {
        val f = frame ?: return
        val s = state.value
        if (s.step != AddFrameStep.Confirm) return
        state.update { it.copy(step = AddFrameStep.Saving, saveFailed = false) }
        viewModelScope.launch {
            try {
                val name = s.name.trim().ifEmpty { defaultName() }
                val id = importer.save(f, s.windows, name)
                state.update { it.copy(step = AddFrameStep.Saved, savedId = id, name = name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Couldn't save the frame", e)
                state.update { it.copy(step = AddFrameStep.Confirm, naming = true, saveFailed = true) }
            }
        }
    }

    companion object {
        private const val Tag = "AddFrame"

        /** The finding animation plays at least this long. */
        const val MinFindingMillis = 1_400L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                val c = app.container
                AddFrameViewModel(
                    importer = c.frameImporter,
                    defaultName = {
                        CustomFrameStore.defaultName(c.customFrames.names()) { n -> app.getString(R.string.frames_default_name, n) }
                    },
                )
            }
        }
    }
}
