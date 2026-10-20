package com.blaze.photobooth.ui.feature.capture

import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.camera.CameraController
import com.blaze.photobooth.camera.ShotGeometry
import com.blaze.photobooth.camera.ShotProcessor
import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.data.prefs.BoothPreferences
import com.blaze.photobooth.data.prefs.BoothSettings
import com.blaze.photobooth.model.BackFlash
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.CapturePlan
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.FrameColors
import com.blaze.photobooth.render.LayoutGeometry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Capture (README §4.3). Picks up the draft Setup started (or one being continued from Home), runs the
 * automatic [CaptureSequencer], and turns each photo into a stored shot (§9.4) recorded in the draft
 * (§11.4). The route binds [camera] to the screen's lifecycle and plays the [effects].
 *
 * With a [retakeIndex] (Pick's "retake", §4.4) it shoots just that one shot again, replacing it in the draft,
 * and leaving never discards the session.
 */
class CaptureViewModel(
    private val draftStore: BoothDraftStore,
    private val preferences: BoothPreferences,
    private val frameRepository: FrameRepository,
    val camera: CameraController,
    private val processor: ShotProcessor,
    private val freeBytes: () -> Long,
    private val retakeIndex: Int? = null,
) : ViewModel() {

    private val state = MutableStateFlow(CaptureUiState(retakeIndex = retakeIndex))
    val uiState: StateFlow<CaptureUiState> = state.asStateFlow()

    private val effectChannel = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects: Flow<CaptureEffect> = effectChannel.receiveAsFlow()

    /** Sound, haptics and screen flash switches (defaults until M8). */
    val settings: StateFlow<BoothSettings> =
        preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, BoothSettings())

    private var config = BoothConfig()
    private var plan: CapturePlan? = null
    private var sequencer: CaptureSequencer? = null
    private var storageAcknowledged = false
    private var flightKey = 0

    init {
        viewModelScope.launch { camera.status.collect { s -> state.update { it.copy(camera = s) } } }
        viewModelScope.launch { load() }
        viewModelScope.launch { settings.collect { s -> state.update { it.copy(poseIdeas = s.poseIdeas) } } }
        viewModelScope.launch {
            val lenses = camera.availableLenses()
            state.update { it.copy(lenses = lenses) }
        }
    }

    fun onEvent(event: CaptureEvent) {
        when (event) {
            CaptureEvent.Shutter -> shutter()
            CaptureEvent.StartAnyway -> {
                storageAcknowledged = true
                state.update { it.copy(showStorageWarning = false) }
                sequencer?.start()
            }
            CaptureEvent.DismissStorageWarning -> state.update { it.copy(showStorageWarning = false) }
            CaptureEvent.Flip -> flip()
            CaptureEvent.ToggleBackFlash -> toggleBackFlash()
            CaptureEvent.SwapPose -> swapPose()
            CaptureEvent.RetryCamera -> state.update { it.copy(bindAttempt = it.bindAttempt + 1) }
            CaptureEvent.Close, CaptureEvent.RequestPermission, CaptureEvent.OpenSettings -> Unit // the route's
        }
    }

    fun pause() {
        sequencer?.pause()
    }

    /** The app went to the background mid-sequence (§13): pause, and resume with a fresh countdown on return. */
    fun onBackground() {
        sequencer?.onBackground()
    }

    fun onForeground() {
        sequencer?.onForeground()
    }

    /**
     * Leaving the booth throws the session away (after the route confirmed it when shots would be lost).
     * Leaving a retake just goes back to Pick: the old shot stays.
     */
    fun leave(onDone: () -> Unit) {
        sequencer?.pause()
        if (retakeIndex != null) {
            onDone()
            return
        }
        viewModelScope.launch {
            draftStore.discard()
            onDone()
        }
    }

    private fun shutter() {
        val s = state.value
        if (!s.loaded) return
        if (asksAboutStorage(running = s.running, taken = s.taken, lowStorage = s.lowStorage, acknowledged = storageAcknowledged)) {
            state.update { it.copy(showStorageWarning = true) }
            return
        }
        sequencer?.toggle()
    }

    private fun flip() {
        if (!state.value.canFlip) return
        val lens = if (config.lens == Lens.Front) Lens.Back else Lens.Front
        config = config.copy(lens = lens)
        state.update { it.copy(lens = lens) }
        viewModelScope.launch {
            draftStore.updateConfig(config)
            // Remember the flip for next time too, without touching the rest of the saved Setup.
            preferences.config.first()?.let { preferences.save(it.copy(lens = lens)) }
        }
    }

    /** §13: the back LED flash, Off ↔ Auto. Applied to the bound camera at once, and remembered like Setup's row. */
    private fun toggleBackFlash() {
        val flash = if (config.backFlash == BackFlash.Off) BackFlash.Auto else BackFlash.Off
        config = config.copy(backFlash = flash)
        state.update { it.copy(backFlash = flash) }
        camera.setBackFlash(flash)
        viewModelScope.launch {
            draftStore.updateConfig(config)
            preferences.config.first()?.let { preferences.save(it.copy(backFlash = flash)) }
        }
    }

    /** §5.6.1: swaps the pose on the card, saved in the draft at once (so a retake or a restart shows it). */
    private fun swapPose() {
        val s = state.value
        val index = s.poseIndex ?: return
        if (!s.canSwapPose) return
        viewModelScope.launch {
            val draft = draftStore.swapPose(index) ?: return@launch
            state.update { it.copy(posePlan = draft.posePlan) }
        }
    }

    private suspend fun load() {
        val retake = retakeIndex
        val existing = draftStore.current()
        if (retake != null && existing == null) {
            // Nothing to retake into (the draft was discarded elsewhere): just go back.
            effectChannel.send(CaptureEffect.Finished)
            return
        }
        val draft = existing ?: startFromPreferences()
        config = draft.config.toConfig()
        // Hidden frames too: she may have hidden this one in Manage frames since the draft began.
        val option = frameRepository.find(config.frameId)
        val slots = (option as? FrameOption.Artwork)?.frame?.slots ?: LayoutGeometry.cells(config.layout)
        val p = CapturePlan(slots, bonusShots = (draft.totalShots - slots.size).coerceAtLeast(0))
        plan = p
        // A retake shoots one shot: sequence position 0 is shot [retake].
        val shotFor: (Int) -> Int = if (retake != null) { _ -> retake } else { i -> i }
        val total = if (retake != null) 1 else p.totalShots
        val taken = if (retake != null) 0 else draft.taken
        val thumbs = if (retake != null) emptyMap() else draft.shots.associate { it.index to loadThumbnail(it.index) }
        state.update {
            it.copy(
                loaded = true,
                cuts = p.cuts,
                timerSec = config.timerSec,
                aspect = p.aspectFor(shotFor(taken)),
                frameColor = frameColor(option),
                filter = config.filter,
                lens = config.lens,
                mirror = config.mirror,
                backFlash = config.backFlash,
                tray = List(total) { i -> TrayShot(p.aspectFor(shotFor(i)), thumbs[i]) },
                lowStorage = isLowStorage(withContext(Dispatchers.IO) { freeBytes() }),
                posePlan = draft.posePlan,
            )
        }
        val seq = CaptureSequencer(
            scope = viewModelScope,
            timerSec = config.timerSec,
            total = total,
            taken = taken,
            capture = { i -> capture(shotFor(i), trayIndex = i) },
            onTick = { effectChannel.trySend(CaptureEffect.Tick(it)) },
        )
        sequencer = seq
        seq.states.collect { s ->
            state.update { it.copy(sequence = s, aspect = p.aspectFor(shotFor(s.taken.coerceAtMost(total - 1)))) }
            if (s.phase == SequencePhase.Done) effectChannel.send(CaptureEffect.Finished)
        }
    }

    /** Capture opened without a draft (e.g. restored after the app was killed before Setup's draft landed). */
    private suspend fun startFromPreferences(): BoothDraft {
        val saved = preferences.config.first() ?: BoothConfig()
        val options = frameRepository.options.first()
        val option = options.firstOrNull { it.id == saved.frameId } ?: options.firstOrNull()
        val c = saved.copy(frameId = option?.id ?: saved.frameId)
        val cuts = (option as? FrameOption.Artwork)?.frame?.slots?.size ?: c.layout.cuts
        return draftStore.begin(c, cuts + c.bonusShots)
    }

    /** Takes shot [index] (the draft's numbering) into tray slot [trayIndex]. */
    private suspend fun capture(index: Int, trayIndex: Int): Boolean {
        val p = plan ?: return false
        return try {
            check(camera.awaitReady()) { "Camera isn't ready" }
            val image = camera.takePicture(
                onStarted = {
                    effectChannel.trySend(CaptureEffect.Shutter)
                    state.update { it.copy(flashCount = it.flashCount + 1) }
                },
            )
            val saved = processor.save(image, index, p.aspectFor(index), ShotGeometry.shouldMirror(config.lens, config.mirror))
            draftStore.addShot(saved.shot)
            val thumb = saved.thumbnail.asImageBitmap()
            flightKey++
            state.update { s ->
                s.copy(
                    tray = s.tray.mapIndexed { i, t -> if (i == trayIndex) t.copy(thumbnail = thumb) else t },
                    flight = ShotFlight(trayIndex, flightKey),
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The draft keeps every shot taken so far; resuming retries this one (§13).
            Log.e(Tag, "Shot $index failed", e)
            effectChannel.trySend(CaptureEffect.SaveFailed)
            false
        }
    }

    private suspend fun loadThumbnail(index: Int) = withContext(Dispatchers.IO) {
        try {
            val file = draftStore.shotFile(index)
            BitmapOps.forScreen(BitmapOps.decode(open = { file.inputStream() }, maxWidth = ShotProcessor.ThumbPx)).asImageBitmap()
        } catch (e: Exception) {
            Log.w(Tag, "No thumbnail for shot $index", e)
            null
        }
    }

    private suspend fun frameColor(option: FrameOption?): Int = when (option) {
        is FrameOption.Theme -> FrameColors.of(option.theme)
        is FrameOption.Artwork -> try {
            val thumb = frameRepository.thumbnail(option.frame)
            withContext(Dispatchers.Default) {
                val pixels = IntArray(thumb.width * thumb.height)
                thumb.getPixels(pixels, 0, thumb.width, 0, 0, thumb.width, thumb.height)
                FrameColors.average(pixels)
            }
        } catch (e: Exception) {
            Log.w(Tag, "No colour for ${option.id}", e)
            CaptureUiState.DefaultFrameColor
        }
        null -> CaptureUiState.DefaultFrameColor
    }

    override fun onCleared() {
        camera.release()
    }

    companion object {
        private const val Tag = "CaptureViewModel"

        /** Free space for new shots, counting cache Android would clear for us. */
        private fun freeBytes(context: android.content.Context): Long = try {
            val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
            storage.getAllocatableBytes(storage.getUuidForPath(context.filesDir))
        } catch (e: java.io.IOException) {
            Log.w(Tag, "Couldn't read free space", e)
            Long.MAX_VALUE
        }

        /** Below this, warn before starting (§13). */
        const val LowStorageBytes = 50L * 1024 * 1024

        /** §13: under 50 MB of room for new shots. */
        fun isLowStorage(freeBytes: Long): Boolean = freeBytes < LowStorageBytes

        /**
         * §13: the shutter asks first ("Your phone is almost full…") only before the very first shot of a session,
         * when space is low and she hasn't already said "Start anyway". Pure.
         */
        fun asksAboutStorage(running: Boolean, taken: Int, lowStorage: Boolean, acknowledged: Boolean): Boolean =
            !running && taken == 0 && lowStorage && !acknowledged

        /** [retakeIndex]: shoot only that shot again (Pick's retake, §4.4); null = the whole session. */
        fun factory(retakeIndex: Int?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                val c = app.container
                CaptureViewModel(
                    draftStore = c.draftStore,
                    preferences = c.boothPreferences,
                    frameRepository = c.frameRepository,
                    camera = CameraController(app),
                    processor = ShotProcessor(c.draftStore),
                    freeBytes = { freeBytes(app) },
                    retakeIndex = retakeIndex,
                )
            }
        }
    }
}
