package com.blaze.photobooth.screenshots

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.ui.feature.capture.CameraPermission
import com.blaze.photobooth.ui.feature.capture.CaptureScreen
import com.blaze.photobooth.ui.feature.capture.SequencePhase
import com.blaze.photobooth.ui.feature.detail.DetailScreen
import com.blaze.photobooth.ui.feature.detail.DetailTab
import com.blaze.photobooth.ui.feature.home.HomeScreen
import com.blaze.photobooth.ui.feature.home.SampleFilledHomeState
import com.blaze.photobooth.ui.feature.result.ResultScreen
import com.blaze.photobooth.ui.feature.settings.SettingsSheetContent
import com.blaze.photobooth.ui.feature.settings.SettingsUiState
import com.blaze.photobooth.ui.feature.setup.SetupScreen
import com.blaze.photobooth.ui.feature.setup.SetupTab
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M8: the light/dark twins that were missing (§6.1), and the reduce-motion states (§6.7): the same screens with
 * ambient motion stopped (the ✓ sticker upright, the countdown's number without its pop, the strip faded in
 * instead of fed out). One class per shot (graphicsLayer gotcha, §15.4).
 */
abstract class SchemeShot {
    @get:Rule
    val compose = createComposeRule()

    protected val context get() = RuntimeEnvironment.getApplication()

    protected fun snap(name: String, dark: Boolean, reduceMotion: Boolean, atMillis: Long = 1_000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { BoothTheme(darkTheme = dark, reduceMotion = reduceMotion) { FakeImages(content) } }
        compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class SettingsLightScreenshotTest : SchemeShot() {
    @Test
    fun settings() = snap("settings_sheet_light", dark = false, reduceMotion = false) {
        Surface(Modifier.wrapContentSize(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) {
            SettingsSheetContent(SettingsUiState(storage = StorageUsage(strips = 23, bytes = 48_200_000)), onEvent = {})
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailLightScreenshotTest : SchemeShot() {
    @Test
    fun detail() = snap("detail_photos_light", dark = false, reduceMotion = false) {
        DetailScreen(AlbumSamples.detail(), onEvent = {}, initialTab = DetailTab.Photos)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class HomeFilledDarkScreenshotTest : SchemeShot() {
    /** The draft card in dark mode: its Continue button uses the darker strawberry (§6.7). */
    @Test
    fun home() = snap("home_filled_dark", dark = true, reduceMotion = false) { HomeScreen(SampleFilledHomeState, onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class SetupFrameReducedScreenshotTest : SchemeShot() {
    /** Reduce motion: the ✓ sticker sits upright (no wobble) and nothing tilts. */
    @Test
    fun setup() = snap("setup_frame_reduce_motion_dark", dark = true, reduceMotion = true) {
        SetupScreen(SetupSamples.herFrame(context), onEvent = {}, initialTab = SetupTab.Frame)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureCountdownReducedScreenshotTest : SchemeShot() {
    /** Reduce motion, 400 ms into the last second: the "1" is simply there (no pop), and the viewfinder doesn't wiggle. */
    @Test
    fun capture() = snap("capture_last_second_reduce_motion_light", dark = false, reduceMotion = true, atMillis = 400) {
        CaptureScreen(
            state = CaptureSamples.theme(SequencePhase.Countdown(1), taken = 2),
            permission = CameraPermission.Granted,
            onEvent = {},
            viewfinder = CaptureSamples.fakeViewfinder,
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultReducedScreenshotTest : SchemeShot() {
    /** Reduce motion, 300 ms in: the strip fading in in place (no feed, sway or confetti). Compare result_mid_print_dark. */
    @Test
    fun result() = snap("result_reduce_motion_dark", dark = true, reduceMotion = true, atMillis = 300) {
        ResultScreen(ResultSamples.state(), onEvent = {})
    }
}
