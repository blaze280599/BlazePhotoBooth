package com.blaze.photobooth.screenshots

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.ui.components.BlobShapeSamples
import com.blaze.photobooth.ui.feature.home.HomeScreen
import com.blaze.photobooth.ui.feature.home.HomeUiState
import com.blaze.photobooth.ui.feature.home.SampleFilledHomeState
import com.blaze.photobooth.ui.feature.placeholder.PlaceholderSample
import com.blaze.photobooth.ui.feature.setup.SetupScreen
import com.blaze.photobooth.ui.feature.setup.SetupTab
import com.blaze.photobooth.ui.feature.settings.SettingsSheetContent
import com.blaze.photobooth.ui.feature.settings.SettingsUiState
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshots of the M1 design system (README §15.4). Rendered by Robolectric's native graphics
 * on a Pixel 9-sized screen (411×923 dp at 420 dpi).
 *
 * `./gradlew recordRoborazziDebug` writes PNGs to `app/src/test/screenshots/`;
 * `./gradlew verifyRoborazziDebug` fails if the UI drifts from them.
 */
@RunWith(AndroidJUnit4::class)
// Fixed order: in Robolectric, graphicsLayer samples (the blob spin) render blank after other Compose
// tests in the same JVM, so blobShapes runs first (alphabetically). Each test class gets its own JVM (build.gradle.kts).
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class BoothScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun homeLight() = snap("home_light") {
        BoothTheme(darkTheme = false, reduceMotion = false) { HomeScreen(HomeUiState(), onEvent = {}) }
    }

    @Test
    fun homeDark() = snap("home_dark") {
        BoothTheme(darkTheme = true, reduceMotion = false) { HomeScreen(HomeUiState(), onEvent = {}) }
    }

    @Test
    fun homeWithAlbumAndDraft() = snap("home_filled_light") {
        BoothTheme(darkTheme = false, reduceMotion = false) { HomeScreen(SampleFilledHomeState, onEvent = {}) }
    }

    @Test
    fun blobShapes() = snap("blob_shapes") {
        BoothTheme(darkTheme = false, reduceMotion = false) { BlobShapeSamples() }
    }

    @Test
    fun placeholderDark() = snap("placeholder_capture_dark") {
        BoothTheme(darkTheme = true, reduceMotion = false) { PlaceholderSample() }
    }

    @Test
    fun settingsSheetDark() = snap("settings_sheet_dark") {
        BoothTheme(darkTheme = true, reduceMotion = false) {
            Surface(
                modifier = Modifier.wrapContentSize(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = SheetShape,
            ) {
                // Real values as the phone shows them (§4.8): the switches as stored, the album counted.
                SettingsSheetContent(SettingsUiState(storage = StorageUsage(strips = 23, bytes = 48_200_000)), onEvent = {})
            }
        }
    }

    // ---- Setup (§4.2) ----

    @Test
    fun setupLight() = snap("setup_layout_light") {
        BoothTheme(darkTheme = false, reduceMotion = true) { SetupScreen(SetupSamples.theme(), onEvent = {}) }
    }

    @Test
    fun setupDark() = snap("setup_layout_dark") {
        BoothTheme(darkTheme = true, reduceMotion = true) { SetupScreen(SetupSamples.theme(), onEvent = {}) }
    }

    @Test
    fun setupFramesHerFrameDark() = snap("setup_frame_her_dark") {
        BoothTheme(darkTheme = true, reduceMotion = true) {
            SetupScreen(SetupSamples.herFrame(context()), onEvent = {}, initialTab = SetupTab.Frame)
        }
    }

    @Test
    fun setupFramesThemeLight() = snap("setup_frame_theme_light") {
        BoothTheme(darkTheme = false, reduceMotion = true) {
            SetupScreen(SetupSamples.theme(context()), onEvent = {}, initialTab = SetupTab.Frame)
        }
    }

    @Test
    fun setupSetByFrameDark() = snap("setup_layout_set_by_frame_dark") {
        BoothTheme(darkTheme = true, reduceMotion = true) { SetupScreen(SetupSamples.herFrame(context()), onEvent = {}) }
    }

    @Test
    fun setupFilterLight() = snap("setup_filter_light") {
        BoothTheme(darkTheme = false, reduceMotion = true) {
            SetupScreen(SetupSamples.theme(context(), filter = true), onEvent = {}, initialTab = SetupTab.Filter)
        }
    }

    @Test
    fun setupOptionsDark() = snap("setup_options_dark") {
        BoothTheme(darkTheme = true, reduceMotion = true) {
            SetupScreen(SetupSamples.theme(), onEvent = {}, initialTab = SetupTab.Options)
        }
    }

    /** Camera = Back swaps the mirror switch for the back-flash choice, Off by default (§13). */
    @Test
    fun setupOptionsBackDark() = snap("setup_options_back_dark") {
        BoothTheme(darkTheme = true, reduceMotion = true) {
            val state = SetupSamples.theme()
            SetupScreen(state.copy(config = state.config.copy(lens = Lens.Back)), onEvent = {}, initialTab = SetupTab.Options)
        }
    }

    private fun context() = org.robolectric.RuntimeEnvironment.getApplication()
}

/** Pixel 9: 1080×2424 px at 420 dpi. */
internal const val Pixel9Qualifiers = "w411dp-h923dp-port-420dpi"
