package com.blaze.photobooth.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.boothDataStore: DataStore<Preferences> by preferencesDataStore(name = "booth")

/**
 * The Settings-sheet switches that M3 already obeys (README §4.8, §6.5, §9.5). Nothing writes them
 * yet: until M8 wires the sheet to [BoothPreferences.saveSettings], every switch reads its default.
 */
data class BoothSettings(
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    /** Front-camera screen flash (§9.5). On by default until M8 adds the switch (and maybe "auto"). */
    val screenFlash: Boolean = true,
)

/**
 * The last Setup choices (README §4.2, §11.6), saved the moment they change, plus the Settings
 * switches ([BoothSettings]).
 */
class BoothPreferences(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.boothDataStore)

    /** The saved config, or null on first launch (nothing chosen yet). */
    val config: Flow<BoothConfig?> = store.data.map(::readConfig)

    suspend fun save(config: BoothConfig) {
        store.edit { writeConfig(it, config) }
    }

    /** Sound, haptics and screen flash. Defaults until M8's Settings sheet writes them. */
    val settings: Flow<BoothSettings> = store.data.map(::readSettings)

    suspend fun saveSettings(settings: BoothSettings) {
        store.edit { writeSettings(it, settings) }
    }

    companion object {
        private val Layout = stringPreferencesKey("setup.layout")
        private val Frame = stringPreferencesKey("setup.frame")
        private val Filter = stringPreferencesKey("setup.filter")
        private val Timer = intPreferencesKey("setup.timer_sec")
        private val Bonus = intPreferencesKey("setup.bonus_shots")
        private val LensKey = stringPreferencesKey("setup.lens")
        private val Mirror = booleanPreferencesKey("setup.mirror")
        private val Sounds = booleanPreferencesKey("settings.sounds")
        private val HapticsKey = booleanPreferencesKey("settings.haptics")
        private val ScreenFlash = booleanPreferencesKey("settings.screen_flash")

        fun readSettings(prefs: Preferences): BoothSettings {
            val d = BoothSettings()
            return BoothSettings(
                sounds = prefs[Sounds] ?: d.sounds,
                haptics = prefs[HapticsKey] ?: d.haptics,
                screenFlash = prefs[ScreenFlash] ?: d.screenFlash,
            )
        }

        fun writeSettings(prefs: MutablePreferences, settings: BoothSettings) {
            prefs[Sounds] = settings.sounds
            prefs[HapticsKey] = settings.haptics
            prefs[ScreenFlash] = settings.screenFlash
        }

        /**
         * Pure mapping, unit-tested. Unknown or out-of-range values (say, from an older version)
         * fall back to the defaults one field at a time instead of discarding everything.
         * Whether the saved frame still exists is checked later, against the frame catalog.
         */
        fun readConfig(prefs: Preferences): BoothConfig? {
            if (prefs[Frame] == null && prefs[Layout] == null) return null
            val defaults = BoothConfig()
            return BoothConfig(
                layout = BoothLayout.fromId(prefs[Layout]) ?: defaults.layout,
                frameId = prefs[Frame]?.let(::FrameId)?.takeIf { it.kind != null } ?: defaults.frameId,
                filter = PhotoFilter.fromId(prefs[Filter]) ?: defaults.filter,
                timerSec = prefs[Timer]?.takeIf { it in BoothConfig.TimerChoices } ?: defaults.timerSec,
                bonusShots = prefs[Bonus]?.takeIf { it in BoothConfig.BonusChoices } ?: defaults.bonusShots,
                lens = Lens.fromId(prefs[LensKey]) ?: defaults.lens,
                mirror = prefs[Mirror] ?: defaults.mirror,
            )
        }

        fun writeConfig(prefs: MutablePreferences, config: BoothConfig) {
            prefs[Layout] = config.layout.id
            prefs[Frame] = config.frameId.value
            prefs[Filter] = config.filter.id
            prefs[Timer] = config.timerSec
            prefs[Bonus] = config.bonusShots
            prefs[LensKey] = config.lens.id
            prefs[Mirror] = config.mirror
        }
    }
}
