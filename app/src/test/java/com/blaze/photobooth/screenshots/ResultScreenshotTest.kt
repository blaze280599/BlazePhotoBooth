package com.blaze.photobooth.screenshots

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.ui.feature.result.ResultScreen
import com.blaze.photobooth.ui.feature.result.ResultUiState
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Result (README §4.5, §7 A9) with the clock frozen at moments of the reveal. The strip is her Lilac Pals printed
 * with generated photos. One class per moment (graphicsLayer, §15.4).
 */
internal object ResultSamples {
    fun state(): ResultUiState {
        val context = RuntimeEnvironment.getApplication()
        val frame = TestArt.herFrames(context).first { it.id == FrameId.asset("lilac-pals") }
        val spec = StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork)), filter = PhotoFilter.Peach)
        val strip = TestArt.render(context, spec, TestArt.photos(3), scale = 0.5f)
        return ResultUiState(strip = strip.asImageBitmap(), saved = true)
    }
}

abstract class ResultShot {
    @get:Rule
    val compose = createComposeRule()

    protected fun snap(name: String, atMillis: Long, dark: Boolean = true, reduceMotion: Boolean = false) {
        val state = ResultSamples.state()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoothTheme(darkTheme = dark, reduceMotion = reduceMotion) { ResultScreen(state = state, onEvent = {}) }
        }
        compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultMidPrintScreenshotTest : ResultShot() {
    /** About halfway through the stepped feed: the strip half out of the printer slot. */
    @Test
    fun midPrint() = snap("result_mid_print_dark", atMillis = 950)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultConfettiScreenshotTest : ResultShot() {
    /** Printed, mid-sway, confetti flying, the "Saved to your album ♡" chip in. */
    @Test
    fun confetti() = snap("result_confetti_dark", atMillis = 2_500)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultSettledScreenshotTest : ResultShot() {
    /** Reduce motion (§6.7): no feed, sway or confetti; the strip fades in. Light theme. */
    @Test
    fun settled() = snap("result_settled_light", atMillis = 1_500, dark = false, reduceMotion = true)
}
