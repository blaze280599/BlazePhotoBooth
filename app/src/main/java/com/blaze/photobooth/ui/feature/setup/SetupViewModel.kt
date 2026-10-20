package com.blaze.photobooth.ui.feature.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.data.prefs.BoothPreferences
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.SampleArt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Setup (README §4.2). Restores the last [BoothConfig] from DataStore, keeps it in memory so every
 * choice updates the preview instantly, and saves each change straight away.
 *
 * If the saved frame no longer exists or is hidden (say, an imported frame was deleted), or on the very first
 * launch, the first frame in the catalog is chosen: her first frame ♡. A frame she has just imported is selected.
 */
class SetupViewModel(
    private val frameRepository: FrameRepository,
    private val preferences: BoothPreferences,
    private val draftStore: BoothDraftStore,
    private val today: () -> LocalDate = LocalDate::now,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val config = MutableStateFlow<BoothConfig?>(null)
    private var options: List<FrameOption> = emptyList()

    private val state = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            var known: Set<FrameId>? = null
            frameRepository.options.collect { latest ->
                options = latest
                state.update { s ->
                    s.copy(frames = latest.map { o -> s.frames.firstOrNull { it.option.id == o.id } ?: FrameCardState(o) })
                }
                // First launch: Settings' "Save mirrored selfies" may already have been chosen (§4.8).
                val mirror = preferences.settings.first().saveMirrored
                val resolved = resolve(config.value ?: preferences.config.first(), latest, mirror)
                // Back from "+ Add frame" (§4.9): the frame she just imported is the one she wants.
                val imported = known?.let { newlyImported(it, latest) }
                config.value = if (imported != null) resolved.copy(frameId = imported).also { preferences.save(it) } else resolved
                known = latest.map { it.id }.toSet()
                latest.filterIsInstance<FrameOption.Artwork>().forEach { loadThumbnail(it) }
            }
        }
        viewModelScope.launch {
            config.filterNotNull().collectLatest { c ->
                state.update { it.copy(config = c) }
                buildPreview(c)?.let { spec -> state.update { it.copy(preview = spec) } }
            }
        }
        viewModelScope.launch {
            val sample = withContext(compute) { SampleArt.filterSample(FilterSamplePx) }
            state.update { it.copy(filterSample = sample) }
        }
    }

    fun onEvent(event: SetupEvent) {
        val current = config.value ?: return
        val next = when (event) {
            is SetupEvent.SelectLayout -> current.copy(layout = event.layout)
            is SetupEvent.SelectFrame -> current.copy(frameId = event.id)
            is SetupEvent.SelectFilter -> current.copy(filter = event.filter)
            is SetupEvent.SetTimer -> current.copy(timerSec = event.seconds)
            is SetupEvent.SetBonus -> current.copy(bonusShots = event.shots)
            is SetupEvent.SetLens -> current.copy(lens = event.lens)
            is SetupEvent.SetMirror -> current.copy(mirror = event.mirror)
            is SetupEvent.SetBackFlash -> current.copy(backFlash = event.flash)
            is SetupEvent.SetCrowd -> current.copy(crowd = event.crowd)
            SetupEvent.AddFrame, SetupEvent.Start, SetupEvent.Back -> return
        }
        if (next == current) return
        config.value = next
        viewModelScope.launch { preferences.save(next) }
    }

    private var starting = false

    /** "Let's go!": start a fresh draft (§11.4) with these choices, then go to Capture. */
    fun startSession(onReady: () -> Unit) {
        val s = state.value
        if (!s.ready || starting) return
        starting = true
        viewModelScope.launch {
            try {
                draftStore.begin(s.config, s.totalShots)
                onReady()
            } finally {
                starting = false
            }
        }
    }

    private suspend fun buildPreview(c: BoothConfig): StripSpec? {
        val frame = when (val option = options.firstOrNull { it.id == c.frameId }) {
            is FrameOption.Theme -> FrameSource.Theme(option.theme)
            is FrameOption.Artwork -> try {
                FrameSource.Image(option.frame, frameRepository.artwork(option.frame, maxWidth = BitmapOps.PreviewMaxPx))
            } catch (e: Exception) {
                Log.e("SetupViewModel", "Couldn't load ${option.id}", e)
                FrameSource.Theme(FrameTheme.Default)
            }
            null -> return null
        }
        return StripSpec(frame = frame, layout = c.layout, filter = c.filter, date = today(), preview = true)
    }

    private fun loadThumbnail(option: FrameOption.Artwork) {
        if (state.value.frames.any { it.option.id == option.id && it.thumbnail != null }) return
        viewModelScope.launch {
            val thumb = try {
                frameRepository.thumbnail(option.frame)
            } catch (e: Exception) {
                Log.w("SetupViewModel", "No thumbnail for ${option.id}", e)
                return@launch
            }
            state.update { s ->
                s.copy(frames = s.frames.map { if (it.option.id == option.id) it.copy(thumbnail = thumb) else it })
            }
        }
    }

    companion object {
        private const val FilterSamplePx = 240

        /**
         * The config Setup opens with (§4.2): the saved one, or a first-launch default on her first frame. If the saved
         * frame is gone or hidden (Manage frames, §4.8), the first frame in the carousel takes its place. Pure.
         */
        fun resolve(saved: BoothConfig?, options: List<FrameOption>, mirror: Boolean): BoothConfig {
            val first = options.firstOrNull()?.id ?: BoothConfig().frameId
            val base = saved ?: BoothConfig(frameId = first, mirror = mirror)
            return if (options.any { it.id == base.frameId }) base else base.copy(frameId = first)
        }

        /** An imported frame in [latest] that wasn't in [known] (the newest, if several). Pure. */
        fun newlyImported(known: Set<FrameId>, latest: List<FrameOption>): FrameId? =
            latest.map { it.id }.lastOrNull { it.kind == FrameId.Kind.Custom && it !in known }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                SetupViewModel(app.container.frameRepository, app.container.boothPreferences, app.container.draftStore)
            }
        }
    }
}
