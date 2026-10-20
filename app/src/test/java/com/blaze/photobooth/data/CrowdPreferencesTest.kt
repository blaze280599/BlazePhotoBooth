package com.blaze.photobooth.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blaze.photobooth.data.prefs.BoothPreferences
import com.blaze.photobooth.data.prefs.BoothSettings
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.Crowd
import com.blaze.photobooth.model.FrameId
import org.junit.Assert.assertEquals
import org.junit.Test

/** README §5.6.1 / §11.6: "Who's in the booth?" and the "Pose ideas" switch are saved, and v1.0.0 data still loads. */
class CrowdPreferencesTest {

    @Test
    fun justMeIsTheDefault() {
        assertEquals(Crowd.Solo, BoothConfig().crowd)
        assertEquals(Crowd.Solo, Crowd.Default)
    }

    @Test
    fun theCrowdRoundTrips() {
        for (crowd in Crowd.entries) {
            val prefs = mutablePreferencesOf()
            BoothPreferences.writeConfig(prefs, BoothConfig(crowd = crowd))
            assertEquals(crowd, BoothPreferences.readConfig(prefs)?.crowd)
            assertEquals(crowd.id, prefs[stringPreferencesKey("setup.crowd")])
        }
    }

    @Test
    fun aV1ConfigWithoutACrowdLoadsAsJustMeAndKeepsEverythingElse() {
        // Exactly the keys v1.0.0 saved.
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("setup.layout") to "grid",
            stringPreferencesKey("setup.frame") to "asset:lilac-pals",
            stringPreferencesKey("setup.filter") to "milky",
            intPreferencesKey("setup.timer_sec") to 3,
            intPreferencesKey("setup.bonus_shots") to 4,
            stringPreferencesKey("setup.lens") to "back",
            booleanPreferencesKey("setup.mirror") to false,
            stringPreferencesKey("setup.back_flash") to "auto",
        )
        val read = BoothPreferences.readConfig(prefs)
        assertEquals(Crowd.Solo, read?.crowd)
        assertEquals(BoothLayout.Grid, read?.layout)
        assertEquals(FrameId.asset("lilac-pals"), read?.frameId)
        assertEquals(4, read?.bonusShots)
        prefs[stringPreferencesKey("setup.crowd")] = "a-hundred-people"
        assertEquals(Crowd.Solo, BoothPreferences.readConfig(prefs)?.crowd)
        prefs[stringPreferencesKey("setup.crowd")] = "squad"
        assertEquals(Crowd.Squad, BoothPreferences.readConfig(prefs)?.crowd)
    }

    @Test
    fun poseIdeasAreOnByDefaultAlsoForV1SettingsAndRoundTrip() {
        assertEquals(true, BoothSettings().poseIdeas)
        val v1 = mutablePreferencesOf(booleanPreferencesKey("settings.sounds") to false)
        assertEquals(true, BoothPreferences.readSettings(v1).poseIdeas)
        assertEquals(false, BoothPreferences.readSettings(v1).sounds)
        val prefs = mutablePreferencesOf()
        BoothPreferences.writeSettings(prefs, BoothSettings(poseIdeas = false))
        assertEquals(false, prefs[booleanPreferencesKey("settings.pose_ideas")])
        assertEquals(BoothSettings(poseIdeas = false), BoothPreferences.readSettings(prefs))
    }
}
