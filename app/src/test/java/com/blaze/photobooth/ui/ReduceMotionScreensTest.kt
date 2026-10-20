package com.blaze.photobooth.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.screenshots.PickSamples
import com.blaze.photobooth.screenshots.Pixel9Qualifiers
import com.blaze.photobooth.screenshots.ResultSamples
import com.blaze.photobooth.ui.feature.pick.PickFlight
import com.blaze.photobooth.ui.feature.pick.PickScreen
import com.blaze.photobooth.ui.feature.result.ResultScreen
import com.blaze.photobooth.ui.theme.BoothTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * README §6.7: with reduce motion, motion that takes time is skipped and the state shows at once (a cross-fade at
 * most). Each test checks a moment where the normal app would still be animating.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Pixel9Qualifiers)
class ReduceMotionScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun result(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent { BoothTheme(darkTheme = true, reduceMotion = reduceMotion) { ResultScreen(ResultSamples.state(), onEvent = {}) } }
        compose.mainClock.advanceTimeBy(600)
    }

    @Test
    fun resultSkipsTheFeedWithReduceMotion() {
        result(reduceMotion = true)
        // The strip only fades in (400 ms): it's already printed.
        compose.onNodeWithText("Here it is! ✿").assertExists()
    }

    @Test
    fun resultFeedsTheStripOutNormally() {
        result(reduceMotion = false)
        // Eight 230 ms feeds: still printing at 0.6 s.
        compose.onNodeWithText("Printing your memories…").assertExists()
    }

    private fun pickFlight(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        // Shot 4 (index 3) is on its way into slot 2.
        val state = PickSamples.lilac(listOf(0, 3, null))
        compose.setContent {
            BoothTheme(darkTheme = true, reduceMotion = reduceMotion) {
                PickScreen(state, onEvent = {}, flight = PickFlight(shot = 3, slot = 1, key = 1))
            }
        }
        compose.mainClock.advanceTimeBy(80)
    }

    @Test
    fun pickPlacesAShotAtOnceWithReduceMotion() {
        pickFlight(reduceMotion = true)
        compose.onNodeWithContentDescription("Slot 2 of 3, filled").assertExists()
    }

    @Test
    fun pickFliesTheShotInNormally() {
        pickFlight(reduceMotion = false)
        // While it flies, its slot waits empty.
        compose.onNodeWithContentDescription("Slot 2 of 3, empty").assertExists()
    }
}
