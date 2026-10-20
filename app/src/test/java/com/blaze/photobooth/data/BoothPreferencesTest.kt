package com.blaze.photobooth.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blaze.photobooth.data.prefs.BoothPreferences
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** README §4.2 / §11.6: the last Setup choices survive a restart exactly, and bad values degrade gently. */
class BoothPreferencesTest {

    @Test
    fun defaultsMatchTheSpec() {
        val c = BoothConfig()
        assertEquals(5, c.timerSec)
        assertEquals(2, c.bonusShots)
        assertEquals(Lens.Front, c.lens)
        assertEquals(true, c.mirror)
        assertEquals(BoothLayout.Classic, c.layout)
        assertEquals(PhotoFilter.Original, c.filter)
    }

    @Test
    fun firstLaunchHasNoSavedConfig() {
        assertNull(BoothPreferences.readConfig(mutablePreferencesOf()))
    }

    @Test
    fun roundTripsEveryField() {
        val config = BoothConfig(
            layout = BoothLayout.Six,
            frameId = FrameId.asset("lilac-pals"),
            filter = PhotoFilter.Milky,
            timerSec = 10,
            bonusShots = 4,
            lens = Lens.Back,
            mirror = false,
        )
        val prefs = mutablePreferencesOf()
        BoothPreferences.writeConfig(prefs, config)
        assertEquals(config, BoothPreferences.readConfig(prefs))
    }

    @Test
    fun unknownValuesFallBackFieldByField() {
        val prefs = mutablePreferencesOf()
        BoothPreferences.writeConfig(prefs, BoothConfig(layout = BoothLayout.Grid, filter = PhotoFilter.Peach))
        prefs[stringPreferencesKey("setup.filter")] = "sepia-from-the-future"
        prefs[intPreferencesKey("setup.timer_sec")] = 7
        prefs[stringPreferencesKey("setup.frame")] = "garbage"
        val read = BoothPreferences.readConfig(prefs)
        assertEquals(BoothLayout.Grid, read?.layout)
        assertEquals(PhotoFilter.Original, read?.filter)
        assertEquals(BoothConfig.DefaultTimer, read?.timerSec)
        assertEquals(BoothConfig().frameId, read?.frameId)
    }

    @Test
    fun aMissingFrameIdStaysAsSavedForTheCatalogToResolve() {
        // Whether "custom:gone" still exists is decided against the frame catalog, not here.
        val prefs = mutablePreferencesOf()
        BoothPreferences.writeConfig(prefs, BoothConfig(frameId = FrameId.custom("gone")))
        assertEquals(FrameId.custom("gone"), BoothPreferences.readConfig(prefs)?.frameId)
    }
}
