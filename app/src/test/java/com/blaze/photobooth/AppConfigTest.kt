package com.blaze.photobooth

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * README §13 and M9: the activity is portrait-locked, and a configuration change (dark mode switched on while the app
 * is open, simulated with Robolectric's qualifiers rather than the phone's settings) recreates it on the same screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h923dp-port-notnight-420dpi")
class AppConfigTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun portraitOnlyAndDarkModeKeepsHerPlace() {
        // §13 "Device rotated": portrait-locked, so photos and layouts never turn sideways.
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)

        // Home's ambient motion never settles, so the clock is driven by hand.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Start ✿").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Make your strip").assertExists()

        // Dark mode switches on: the activity is recreated, the back stack is restored from saved state.
        RuntimeEnvironment.setQualifiers("+night")
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(2_000)
        compose.activityRule.scenario.onActivity { activity ->
            val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            assertEquals(Configuration.UI_MODE_NIGHT_YES, night)
        }
        compose.onNodeWithText("Make your strip").assertExists()
        compose.onNodeWithContentDescription("Back").assertExists()
    }
}
