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
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.SlotSelection
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
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
import java.text.BreakIterator
import java.time.LocalDate

/**
 * Pick (README §4.4): the session's shots, which one goes in each slot, and the quick edits (frame, filter,
 * caption, date). The shots come from a [PickSource]: the draft of a session just shot (every change is written
 * back, §11.4, and **Print ✿** saves it to the album), or a saved session in Edit mode (§4.7: changes stay here
 * until **Save**, which re-renders the strip). Either way the strip is rendered at full size by the source.
 */
class PickViewModel(
    private val source: PickSource,
    private val frameRepository: FrameRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow(PickUiState(editing = source.editing))
    val uiState: StateFlow<PickUiState> = state.asStateFlow()

    private val effectChannel = Channel<PickEffect>(Channel.BUFFERED)
    val effects: Flow<PickEffect> = effectChannel.receiveAsFlow()

    private var config = BoothConfig()
    /** Every frame that can be drawn, hidden ones included (a saved strip's frame may be hidden now, §11.2). */
    private var options: List<FrameOption> = emptyList()

    /** The frames she hasn't hidden: what the Frame tab offers. */
    private var visible: List<FrameOption> = emptyList()
    private var totalShots = 0
    private var stampDate: LocalDate? = null
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
                val caption = cleanCaption(event.text)
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
            source.restart(config, totalShots)
            onReady()
        }
    }

    /** "Discard session?" → yes. */
    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            source.discard()
            onDone()
        }
    }

    private suspend fun load() {
        options = frameRepository.allOptions.first()
        visible = frameRepository.options.first()
        source.session.collect { session ->
            if (session == null) {
                if (!state.value.printing) state.update { it.copy(missing = true) }
                return@collect
            }
            if (!state.value.loaded) firstLoad(session) else refreshShots(session)
        }
    }

    private suspend fun firstLoad(session: PickSession) {
        totalShots = session.totalShots
        stampDate = session.stampDate
        config = session.config
        // A frame that no longer exists (an imported one deleted since): fall back to a theme that fits (§13).
        if (options.none { it.id == config.frameId }) {
            config = fallbackConfig(config, shotCount = session.shots.size, picked = session.selection.count { it != null })
        }
        val shots = loadShots(session, emptyList())
        val cuts = cutsFor(config)
        val selection = SlotSelection.restore(session.selection, cuts, shots.map { it.index })
        state.update {
            it.copy(
                loaded = true,
                shots = shots,
                cuts = cuts,
                selection = selection,
                frames = frameCards(shots.size),
                frameId = config.frameId,
                filter = config.filter,
                caption = session.caption.orEmpty(),
                showDate = session.showDate,
            )
        }
        if (selection.slots != session.selection) persistPick()
        rebuildPreview()
        loadFrameThumbnails()
    }

    /** A retake came back (§4.4): reload the shots whose file changed. */
    private suspend fun refreshShots(session: PickSession) {
        val current = state.value.shots
        if (session.shots.size == current.size && session.shots.all { s -> current.any { it.index == s.index && it.version == s.version } }) return
        val shots = loadShots(session, current)
        state.update { s -> s.copy(shots = shots, peek = null, peekImage = null) }
    }

    private suspend fun loadShots(session: PickSession, reuse: List<PickShot>): List<PickShot> {
        val cuts = cutsFor(config)
        return session.shots.mapNotNull { shot ->
            reuse.firstOrNull { it.index == shot.index && it.version == shot.version }?.let { return@mapNotNull it }
            try {
                val file = source.shotFile(shot.index)
                val bitmap = withContext(io) { BitmapOps.forScreen(decodePreview(file)) }
                PickShot(shot.index, bitmap, bitmap.asImageBitmap(), bonus = shot.index >= cuts, version = shot.version)
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
        viewModelScope.launch { source.saveConfig(next) }
        rebuildPreview()
    }

    private fun persistPick() {
        val s = state.value
        viewModelScope.launch { source.savePick(s.selection.slots, s.caption.trim().ifEmpty { null }, s.showDate) }
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
            // A new strip is stamped today; an edited one keeps the day it was made.
            date = (stampDate ?: today()).takeIf { text && s.showDate },
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

    /**
     * Frames the session can fill: no more slots than shots (a theme keeps the session's layout). A hidden frame the
     * session already uses stays on offer, first, so Edit can keep it.
     */
    private fun frameCards(shotCount: Int): List<FrameCardState> = visible
        .let { list ->
            val current = options.firstOrNull { it.id == config.frameId }
            if (current != null && list.none { it.id == current.id }) listOf(current) + list else list
        }
        .filter { o ->
            val cuts = when (o) {
                is FrameOption.Artwork -> o.frame.slots.size
                is FrameOption.Theme -> config.layout.cuts
            }
            cuts <= shotCount
        }
        .map { FrameCardState(it) }

    /**
     * The Frame tab's swatches, one after another and shown together (M9: five parallel decodes and five
     * recompositions fought the first frames of Pick and Edit for the CPU). The tab opens on Cuts, so nobody waits.
     */
    private fun loadFrameThumbnails() {
        val artwork = state.value.frames.map { it.option }.filterIsInstance<FrameOption.Artwork>()
        if (artwork.isEmpty()) return
        viewModelScope.launch {
            val thumbs = artwork.mapNotNull { option ->
                try {
                    option.id to frameRepository.thumbnail(option.frame)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(Tag, "No thumbnail for ${option.id}", e)
                    null
                }
            }.toMap()
            state.update { s -> s.copy(frames = s.frames.map { card -> thumbs[card.option.id]?.let { card.copy(thumbnail = it) } ?: card }) }
        }
    }

    private fun peek(index: Int) {
        val shot = state.value.shots.firstOrNull { it.index == index } ?: return
        state.update { it.copy(peek = shot, peekImage = null) }
        peekJob?.cancel()
        peekJob = viewModelScope.launch {
            val big = try {
                withContext(io) { BitmapOps.forScreen(BitmapOps.decode(open = { source.shotFile(index).inputStream() }, maxWidth = PeekMaxPx)) }
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
                val spec = specFor(frameSource(c, preview = false), c, s, preview = false)
                val id = source.commit(spec, PickResult(c, s.selection.slots, s.caption.trim().ifEmpty { null }, s.showDate))
                effectChannel.send(PickEffect.Printed(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, if (source.editing) "Saving the edit failed" else "Print failed", e)
                state.update { it.copy(printing = false) }
                effectChannel.send(PickEffect.PrintFailed)
            }
        }
    }

    companion object {
        private const val Tag = "PickViewModel"
        const val MaxCaption = 24
        private const val PeekMaxPx = 1600

        /**
         * §13: one line of at most [MaxCaption] characters (a pasted line break becomes a space). A longer one is cut
         * between whole characters, so an emoji (two or more chars, e.g. 🫶 or 👩‍❤️‍👨) is never split into a broken box.
         * Pure.
         */
        fun cleanCaption(text: String): String {
            val line = text.replace('\n', ' ')
            if (line.length <= MaxCaption) return line
            val boundaries = BreakIterator.getCharacterInstance().apply { setText(line) }
            var end = 0
            var next = boundaries.first()
            while (next != BreakIterator.DONE && next <= MaxCaption) {
                end = next
                next = boundaries.next()
            }
            return line.substring(0, end)
        }

        /** Shots are drawn into the preview and the tray at ≤ 600 px (§10). */
        fun decodePreview(file: File): android.graphics.Bitmap {
            val decoded = BitmapOps.decode(open = { file.inputStream() }, maxWidth = BitmapOps.PreviewMaxPx * 2)
            return BitmapOps.downscale(decoded, BitmapOps.PreviewMaxPx).also { if (it !== decoded) decoded.recycle() }
        }

        /**
         * The session's frame is gone: the default theme, on the layout with exactly [picked] cuts if there is one,
         * else the biggest layout that [shotCount] shots can fill. Pure.
         */
        fun fallbackConfig(config: BoothConfig, shotCount: Int, picked: Int): BoothConfig {
            val layout = BoothLayout.entries.firstOrNull { it.cuts == picked && it.cuts <= shotCount }
                ?: BoothLayout.entries.filter { it.cuts <= shotCount }.maxByOrNull { it.cuts }
                ?: BoothLayout.Solo
            return config.copy(frameId = FrameId.theme(FrameTheme.Default), layout = layout)
        }

        /** Pick for the draft ([editSessionId] = null), or Edit for a saved session (§4.7). */
        fun factory(editSessionId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                val c = app.container
                val source = if (editSessionId == null) {
                    DraftPickSource(c.draftStore, c.sessionRepository, c.stripExporter, c.autoSaver::onPrinted)
                } else {
                    SavedPickSource(editSessionId, c.sessionRepository, c.stripExporter)
                }
                PickViewModel(source, c.frameRepository)
            }
        }
    }
}
