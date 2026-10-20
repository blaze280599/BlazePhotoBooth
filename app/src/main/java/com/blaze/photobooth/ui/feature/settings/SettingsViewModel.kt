package com.blaze.photobooth.ui.feature.settings

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.export.StorageStats
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.data.prefs.BoothPreferences
import com.blaze.photobooth.data.prefs.BoothSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The Settings sheet (README §4.8): the switches as stored, and the album's size (null while it's counted). */
@Immutable
data class SettingsUiState(val settings: BoothSettings = BoothSettings(), val storage: StorageUsage? = null)

sealed interface SettingsEvent {
    data class SetSounds(val on: Boolean) : SettingsEvent
    data class SetHaptics(val on: Boolean) : SettingsEvent
    data class SetScreenFlash(val on: Boolean) : SettingsEvent
    data class SetSaveMirrored(val on: Boolean) : SettingsEvent
    data class SetAutoSave(val on: Boolean) : SettingsEvent
}

/**
 * Reads and writes the Settings switches ([BoothPreferences.updateSettings]) and measures *Storage used*: the live
 * strip count and every byte under `files/sessions/`, walked on [io] each time the sheet opens.
 */
class SettingsViewModel(
    private val preferences: BoothPreferences,
    private val stripCount: Flow<Int>,
    private val sessionsDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { preferences.settings.collect { s -> state.update { it.copy(settings = s) } } }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            preferences.updateSettings { s ->
                when (event) {
                    is SettingsEvent.SetSounds -> s.copy(sounds = event.on)
                    is SettingsEvent.SetHaptics -> s.copy(haptics = event.on)
                    is SettingsEvent.SetScreenFlash -> s.copy(screenFlash = event.on)
                    is SettingsEvent.SetSaveMirrored -> s.copy(saveMirrored = event.on)
                    is SettingsEvent.SetAutoSave -> s.copy(autoSaveStrip = event.on)
                }
            }
        }
    }

    /** Counts the album again (the sheet calls this every time it opens). */
    fun refreshStorage() {
        viewModelScope.launch {
            val usage = try {
                val strips = stripCount.first()
                StorageUsage(strips, withContext(io) { StorageStats.bytesIn(sessionsDir) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Couldn't measure storage", e)
                return@launch
            }
            state.update { it.copy(storage = usage) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                val c = app.container
                SettingsViewModel(c.boothPreferences, c.sessionRepository.count, c.sessionRepository.files.sessionsDir)
            }
        }
    }
}
