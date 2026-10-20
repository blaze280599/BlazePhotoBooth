package com.blaze.photobooth.screenshots

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.data.prefs.BoothSettings
import com.blaze.photobooth.model.Crowd
import com.blaze.photobooth.ui.feature.capture.CameraPermission
import com.blaze.photobooth.ui.feature.capture.CaptureScreen
import com.blaze.photobooth.ui.feature.capture.CaptureUiState
import com.blaze.photobooth.ui.feature.capture.SequencePhase
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M10's pose card (README §5.6.1) on the fake viewfinder: *First up* before the first shot (classic strip, the
 * tightest landscape viewfinder), the smaller card during a countdown (her Lilac Pals, Two of us, a long name with
 * its Korean name), and *Next: …* during the "Nice!" beat. Each in light, dark, and dark at 200% font.
 * One class per PNG (graphicsLayer gotcha, §15.4).
 */
internal object PoseSamples {
    val firstUp: CaptureUiState get() = CaptureSamples.theme(SequencePhase.Idle)

    /** Lilac Pals, shot 3 of 5 (her last window): "One heart together, half each · 반쪽 하트". */
    val countdown: CaptureUiState get() = CaptureSamples.lilac(SequencePhase.Countdown(4), taken = 2)

    /** The beat after shot 3 on the classic strip: "Next: Finger heart · 손하트". */
    val next: CaptureUiState get() = CaptureSamples.theme(SequencePhase.Beat, taken = 3)
}

abstract class PoseShot {
    @get:Rule
    val compose = createComposeRule()

    protected fun snap(name: String, state: CaptureUiState, dark: Boolean, bigFont: Boolean = false) {
        compose.setContent {
            BoothTheme(darkTheme = dark) {
                val screen = @androidx.compose.runtime.Composable {
                    CaptureScreen(state = state, permission = CameraPermission.Granted, onEvent = {}, viewfinder = CaptureSamples.fakeViewfinder)
                }
                if (bigFont) BigFont { screen() } else screen()
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseFirstUpLightTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_first_up_light", PoseSamples.firstUp, dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseFirstUpDarkTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_first_up_dark", PoseSamples.firstUp, dark = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseFirstUpBigFontTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_first_up_big_font_dark", PoseSamples.firstUp, dark = true, bigFont = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseCountdownLightTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_countdown_light", PoseSamples.countdown, dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseCountdownDarkTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_countdown_dark", PoseSamples.countdown, dark = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseCountdownBigFontTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_countdown_big_font_dark", PoseSamples.countdown, dark = true, bigFont = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseNextLightTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_next_light", PoseSamples.next, dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseNextDarkTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_next_dark", PoseSamples.next, dark = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseNextBigFontTest : PoseShot() {
    @Test
    fun shot() = snap("capture_pose_next_big_font_dark", PoseSamples.next, dark = true, bigFont = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PoseIdeasOffTest : PoseShot() {
    /** Settings → Pose ideas off (§5.6.1): just the countdown, as before M10. */
    @Test
    fun shot() = snap("capture_pose_off_dark", PoseSamples.countdown.copy(poseIdeas = false), dark = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class SetupCrowdLightTest : SchemeShot() {
    /** Setup → Options with "Two of us" chosen (§5.6.1): the row on top, and the 👥 icon on "Let's go!". */
    @Test
    fun shot() = snap("setup_options_crowd_light", dark = false, reduceMotion = true) {
        val state = SetupSamples.theme()
        SetupScreen(state.copy(config = state.config.copy(crowd = Crowd.Duo)), onEvent = {}, initialTab = SetupTab.Options)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class SettingsPoseIdeasOffTest : SchemeShot() {
    /** Settings with the "Pose ideas" switch turned off (§5.6.1). */
    @Test
    fun shot() = snap("settings_pose_ideas_off_dark", dark = true, reduceMotion = true) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape, modifier = Modifier.wrapContentSize()) {
            SettingsSheetContent(
                SettingsUiState(settings = BoothSettings(poseIdeas = false), storage = StorageUsage(strips = 23, bytes = 48_200_000)),
                onEvent = {},
            )
        }
    }
}
