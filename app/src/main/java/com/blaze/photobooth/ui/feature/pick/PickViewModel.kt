package com.blaze.photobooth.ui.feature.pick

import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.SlotSelection
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.StripExporter
import com.blaze.photobooth.ui.feature.setup.FrameCardState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Pick (README §4.4): the draft's shots, which one goes in each slot, and the quick edits (frame, filter,
 * caption, date). Every change is written back to the draft (§11.4). **Print ✿** renders the strip at full
 * size and saves the session ([SessionRepository.finalizeDraft]); Result shows it.
 */
class PickViewModel(
    private val draftStore: BoothDraftStore,
    private val frameRepository: FrameRepository,
    private val sessions: SessionRepository,
    private val exporter: StripExporter,
    private val today: () -> LocalDate = LocalDate::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow(PickUiState())
    val uiState: StateFlow<PickUiState> = state.asStateFlow()

    private val effectChannel = Channel<PickEffect>(Channel.BUFFERED)
    val effects: Flow<PickEffect> = effectChannel.receiveAsFlow()

    private var config = BoothConfig()
    private var options: List<FrameOption> = emptyList()
    private var totalShots = 0
    private var previewJob: Job? = null
    private var peekJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: PickEvent) {
        when (event) {
            is PickEvent.TapShot -> tapShot(event.index)
            is PickEvent.TapSlot -> select(state.value.selection.clearSlot(event.slot)) { effectChannel.trySend(PickEffect.Cleared) }
            is PickEvent.Peek -> peek(event.index)
            PickEvent.DismissPeek -> {
                peekJob?.cancel()
                state.update { it.copy(peek = null, peekImage = null) }
            }
            is PickEvent.SelectFrame -> selectFrame(event.id)
            is PickEvent.SelectFilter -> changeConfig(config.copy(filter = event.filter))
            is PickEvent.SetCaption -> {
                val caption = event.text.replace('\n', ' ').take(MaxCaption)
                state.update { it.copy(caption = caption) }
                persistPick()
                rebuildPreview()
            }
            is PickEvent.SetShowDate -> {
                state.update { it.copy(showDate = event.show) }
                persistPick()
                rebuildPreview()
            }
            PickEvent.Print -> print()
            is PickEvent.Retake, PickEvent.Close, PickEvent.Back -> Unit // the route's
        }
    }

    /** "Retake everything?" → yes: a fresh draft with the same choices, then Capture from shot 1 (§3). */
    fun retakeAll(onReady: () -> Unit) {
        viewModelScope.launch {
            draftStore.begin(config, totalShots)
            onReady()
        }
    }

    /** "Discard session?" → yes. */
    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            draftStore.discard()
            onDone()
        }
    }

    private suspend fun load() {
        options = frameRepository.options.first()
        draftStore.draft.collect { draft ->
            if (draft == null) {
                if (!state.value.printing) state.update { it.copy(missing = true) }
                return@collect
            }
            if (!state.value.loaded) firstLoad(draft) else refreshShots(draft)
        }
    }

    private suspend fun firstLoad(draft: BoothDraft) {
        config = draft.config.toConfig()
        totalShots = draft.totalShots
        val shots = loadShots(draft, emptyList())
        val cuts = cutsFor(config)
        val selection = SlotSelection.restore(draft.selection, cuts, shots.map { it.index })
        state.update {
            it.copy(
                loaded = true,
                shots = shots,
                cuts = cuts,
                selection = selection,
                frames = frameCards(shots.size),
                frameId = config.frameId,
                filter = config.filter,
                caption = draft.caption.orEmpty(),
                showDate = draft.showDate,
            )
        }
        if (selection.slots != draft.selection) persistPick()
        rebuildPreview()
        loadFrameThumbnails()
    }

    /** A retake came back (§4.4): reload the shots whose file changed. */
    private suspend fun refreshShots(draft: BoothDraft) {
        val current = state.value.shots
        if (draft.shots.size == current.size && draft.shots.all { s -> current.any { it.index == s.index && it.version == s.takenAt } }) return
        val shots = loadShots(draft, current)
        state.update { s -> s.copy(shots = shots, peek = null, peekImage = null) }
    }

    private suspend fun loadShots(draft: BoothDraft, reuse: List<PickShot>): List<PickShot> {
        val cuts = cutsFor(draft.config.toConfig())
        return draft.shots.mapNotNull { shot ->
            reuse.firstOrNull { it.index == shot.index && it.version == shot.takenAt }?.let { return@mapNotNull it }
            try {
                val file = draftStore.shotFile(shot.index)
                val bitmap = withContext(io) { decodePreview(file) }
                PickShot(shot.index, bitmap, bitmap.asImageBitmap(), bonus = shot.index >= cuts, version = shot.takenAt)
            } catch (e: Exception) {
                Log.w(Tag, "Couldn't load shot ${shot.index}", e)
                null
            }
        }
    }

    private fun tapShot(index: Int) {
        val before = state.value.selection
        val after = before.toggle(index)
        if (after == before) {
            effectChannel.trySend(PickEffect.Rejected)
            return
        }
        select(after) {
            val slot = after.slotOf(index)
            effectChannel.trySend(if (slot != null) PickEffect.Placed(index, slot) else PickEffect.Cleared)
        }
    }

    private inline fun select(selection: SlotSelection, then: () -> Unit) {
        if (selection == state.value.selection) return
        state.update { it.copy(selection = selection) }
        persistPick()
        then()
    }

    private fun selectFrame(id: FrameId) {
        if (id == config.frameId) return
        val next = config.copy(frameId = id)
        val cuts = cutsFor(next)
        state.update { s ->
            s.copy(
                frameId = id,
                cuts = cuts,
                selection = s.selection.resize(cuts, s.shots.map { it.index }),
                shots = s.shots.map { it.copy(bonus = it.index >= cuts) },
            )
        }
        persistPick()
        changeConfig(next)
    }

    /** Frame and filter changes are this session's only: Setup keeps its own saved choices (§4.4). */
    private fun changeConfig(next: BoothConfig) {
        if (next == config) return
        config = next
        state.update { it.copy(filter = next.filter, frameId = next.frameId) }
        viewModelScope.launch { draftStore.updateConfig(next) }
        rebuildPreview()
    }

    private fun persistPick() {
        val s = state.value
        viewModelScope.launch { draftStore.updatePick(s.selection.slots, s.caption.trim().ifEmpty { null }, s.showDate) }
    }

    private fun rebuildPreview() {
        val c = config
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val frame = frameSource(c, preview = true)
            state.update { s -> s.copy(preview = specFor(frame, c, s, preview = true)) }
        }
    }

    private fun specFor(frame: FrameSource, c: BoothConfig, s: PickUiState, preview: Boolean): StripSpec {
        val text = when (frame) {
            is FrameSource.Image -> frame.frame.textArea != null
            is FrameSource.Theme -> true
        }
        return StripSpec(
            frame = frame,
            layout = c.layout,
            filter = c.filter,
            caption = s.caption.trim().takeIf { text && it.isNotEmpty() },
            date = today().takeIf { text && s.showDate },
            preview = preview,
        )
    }

    private suspend fun frameSource(c: BoothConfig, preview: Boolean): FrameSource =
        when (val option = options.firstOrNull { it.id == c.frameId }) {
            is FrameOption.Artwork -> FrameSource.Image(
                option.frame,
                frameRepository.artwork(option.frame, maxWidth = if (preview) BitmapOps.PreviewMaxPx else null),
            )
            is FrameOption.Theme -> FrameSource.Theme(option.theme)
            null -> FrameSource.Theme(FrameTheme.fromId(c.frameId.localId) ?: FrameTheme.Default)
        }

    private fun cutsFor(c: BoothConfig): Int =
        (options.firstOrNull { it.id == c.frameId } as? FrameOption.Artwork)?.frame?.slots?.size ?: c.layout.cuts

    /** Frames the session can fill: no more slots than shots (a theme keeps the session's layout). */
    private fun frameCards(shotCount: Int): List<FrameCardState> = options
        .filter { o ->
            val cuts = when (o) {
                is FrameOption.Artwork -> o.frame.slots.size
                is FrameOption.Theme -> config.layout.cuts
            }
            cuts <= shotCount
        }
        .map { FrameCardState(it) }

    private fun loadFrameThumbnails() {
        state.value.frames.map { it.option }.filterIsInstance<FrameOption.Artwork>().forEach { option ->
            viewModelScope.launch {
                val thumb = try {
                    frameRepository.thumbnail(option.frame)
                } catch (e: Exception) {
                    Log.w(Tag, "No thumbnail for ${option.id}", e)
                    return@launch
                }
                state.update { s -> s.copy(frames = s.frames.map { if (it.option.id == option.id) it.copy(thumbnail = thumb) else it }) }
            }
        }
    }

    private fun peek(index: Int) {
        val shot = state.value.shots.firstOrNull { it.index == index } ?: return
        state.update { it.copy(peek = shot, peekImage = null) }
        peekJob?.cancel()
        peekJob = viewModelScope.launch {
            val big = try {
                withContext(io) { BitmapOps.decode(open = { draftStore.shotFile(index).inputStream() }, maxWidth = PeekMaxPx) }
            } catch (e: Exception) {
                Log.w(Tag, "Couldn't open shot $index", e)
                return@launch
            }
            state.update { s -> if (s.peek?.index == index) s.copy(peekImage = big.asImageBitmap()) else s }
        }
    }

    private fun print() {
        val s = state.value
        if (s.printing) return
        if (!s.canPrint) {
            effectChannel.trySend(PickEffect.Rejected)
            return
        }
        state.update { it.copy(printing = true) }
        val c = config
        viewModelScope.launch {
            try {
                draftStore.updatePick(s.selection.slots, s.caption.trim().ifEmpty { null }, s.showDate)
                draftStore.updateConfig(c)
                val spec = specFor(frameSource(c, preview = false), c, s, preview = false)
                val id = sessions.finalizeDraft { _, dir ->
                    val photos = s.selection.slots.map { i -> i?.let { File(dir, SessionFileStore.shotName(it)) } }
                    exporter.export(spec, photos, dir)
                }
                effectChannel.send(PickEffect.Printed(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Print failed", e)
                state.update { it.copy(printing = false) }
                effectChannel.send(PickEffect.PrintFailed)
            }
        }
    }

    companion object {
        private const val Tag = "PickViewModel"
        const val MaxCaption = 24
        private const val PeekMaxPx = 1600

        /** Shots are drawn into the preview and the tray at ≤ 600 px (§10). */
        fun decodePreview(file: File): android.graphics.Bitmap {
            val decoded = BitmapOps.decode(open = { file.inputStream() }, maxWidth = BitmapOps.PreviewMaxPx * 2)
            return BitmapOps.downscale(decoded, BitmapOps.PreviewMaxPx).also { if (it !== decoded) decoded.recycle() }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                val c = app.container
                PickViewModel(c.draftStore, c.frameRepository, c.sessionRepository, c.stripExporter)
            }
        }
    }
}
