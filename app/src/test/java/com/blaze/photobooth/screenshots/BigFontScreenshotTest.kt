package com.blaze.photobooth.screenshots

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.ui.feature.album.AlbumScreen
import com.blaze.photobooth.ui.feature.capture.CameraPermission
import com.blaze.photobooth.ui.feature.capture.CaptureScreen
import com.blaze.photobooth.ui.feature.capture.SequencePhase
import com.blaze.photobooth.ui.feature.detail.DetailScreen
import com.blaze.photobooth.ui.feature.frames.AddFrameScreen
import com.blaze.photobooth.ui.feature.frames.ManageFramesScreen
import com.blaze.photobooth.ui.feature.home.HomeScreen
import com.blaze.photobooth.ui.feature.home.SampleFilledHomeState
import com.blaze.photobooth.ui.feature.pick.PickScreen
import com.blaze.photobooth.ui.feature.pick.PickTab
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
 * README §6.7 and §13: every main screen at **200% font** (the largest Android offers), so clipped or overflowing
 * labels show up in review. The font scale is set through [LocalDensity] (the phone's own setting is never touched).
 * Reduce motion is on, so each PNG is the screen at rest. One class per screen (graphicsLayer gotcha, §15.4).
 */
abstract class BigFontShot {
    @get:Rule
    val compose = createComposeRule()

    protected val context get() = RuntimeEnvironment.getApplication()

    protected fun snap(name: String, dark: Boolean, atMillis: Long = 1_000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoothTheme(darkTheme = dark, reduceMotion = true) {
                BigFont { FakeImages(content) }
            }
        }
        compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

/** Renders [content] with the font scale at [scale] (2 = Android's largest, "200%"). */
@Composable
internal fun BigFont(scale: Float = 2f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = scale), content = content)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontHomeTest : BigFontShot() {
    @Test
    fun home() = snap("big_font_home_dark", dark = true) { HomeScreen(SampleFilledHomeState, onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontSetupLayoutTest : BigFontShot() {
    @Test
    fun setup() = snap("big_font_setup_layout_light", dark = false) { SetupScreen(SetupSamples.herFrame(context), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontSetupFrameTest : BigFontShot() {
    @Test
    fun setup() = snap("big_font_setup_frame_dark", dark = true) {
        SetupScreen(SetupSamples.herFrame(context), onEvent = {}, initialTab = SetupTab.Frame)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontSetupOptionsTest : BigFontShot() {
    @Test
    fun setup() = snap("big_font_setup_options_dark", dark = true) {
        SetupScreen(SetupSamples.theme(), onEvent = {}, initialTab = SetupTab.Options)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontCaptureTest : BigFontShot() {
    @Test
    fun capture() = snap("big_font_capture_dark", dark = true) {
        CaptureScreen(
            state = CaptureSamples.theme(SequencePhase.Countdown(3), taken = 2),
            permission = CameraPermission.Granted,
            onEvent = {},
            viewfinder = CaptureSamples.fakeViewfinder,
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontCapturePermissionTest : BigFontShot() {
    @Test
    fun capture() = snap("big_font_capture_permission_light", dark = false) {
        CaptureScreen(
            state = CaptureSamples.theme(SequencePhase.Idle),
            permission = CameraPermission.Blocked,
            onEvent = {},
            viewfinder = CaptureSamples.fakeViewfinder,
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontPickCutsTest : BigFontShot() {
    @Test
    fun pick() = snap("big_font_pick_cuts_dark", dark = true) { PickScreen(PickSamples.lilac(listOf(0, 3, null)), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontPickTextTest : BigFontShot() {
    @Test
    fun pick() = snap("big_font_pick_text_light", dark = false) {
        PickScreen(PickSamples.theme(listOf(0, 1, 2, 3), caption = "best day"), onEvent = {}, initialTab = PickTab.Text)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontResultTest : BigFontShot() {
    @Test
    fun result() = snap("big_font_result_dark", dark = true, atMillis = 1_500) { ResultScreen(ResultSamples.state(), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontAlbumTest : BigFontShot() {
    @Test
    fun album() = snap("big_font_album_light", dark = false) { AlbumScreen(AlbumSamples.album(), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontAlbumSelectTest : BigFontShot() {
    @Test
    fun album() = snap("big_font_album_select_dark", dark = true) { AlbumScreen(AlbumSamples.album(selection = setOf("s1", "s3")), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontDetailTest : BigFontShot() {
    @Test
    fun detail() = snap("big_font_detail_dark", dark = true) { DetailScreen(AlbumSamples.detail(), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontSettingsTest : BigFontShot() {
    @Test
    fun settings() = snap("big_font_settings_light", dark = false) {
        Surface(Modifier.wrapContentSize(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) {
            SettingsSheetContent(SettingsUiState(storage = StorageUsage(strips = 23, bytes = 48_200_000)), onEvent = {})
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontManageFramesTest : BigFontShot() {
    @Test
    fun manage() = snap("big_font_manage_frames_dark", dark = true) { ManageFramesScreen(FrameSamples.manage(context), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BigFontAddFrameTest : BigFontShot() {
    @Test
    fun addFrame() = snap("big_font_add_frame_light", dark = false) { AddFrameScreen(FrameSamples.confirm(tooSmall = true), onEvent = {}) }
}
