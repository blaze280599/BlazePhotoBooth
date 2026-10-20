package com.blaze.photobooth.screenshots

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.ui.components.BlobShapeSamples
import com.blaze.photobooth.ui.components.SquishyButtonSamples
import com.blaze.photobooth.ui.feature.home.HomeScreen
import com.blaze.photobooth.ui.feature.home.HomeUiState
import com.blaze.photobooth.ui.feature.home.SampleFilledHomeState
import com.blaze.photobooth.ui.feature.placeholder.PlaceholderSample
import com.blaze.photobooth.ui.feature.settings.SettingsSheetContent
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
    fun squishyButtons() = snap("squishy_buttons") {
        BoothTheme(darkTheme = false, reduceMotion = false) { SquishyButtonSamples() }
    }

    @Test
    fun blobShapes() = snap("blob_shapes") {
        BoothTheme(darkTheme = false, reduceMotion = false) { BlobShapeSamples() }
    }

    @Test
    fun placeholderDark() = snap("placeholder_setup_dark") {
        BoothTheme(darkTheme = true, reduceMotion = false) { PlaceholderSample() }
    }

    @Test
    fun settingsSheetDark() = snap("settings_sheet_dark") {
        BoothTheme(darkTheme = true, reduceMotion = false) {
            Surface(
                modifier = Modifier.wrapContentSize(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = SheetShape,
            ) { SettingsSheetContent() }
        }
    }
}

/** Pixel 9: 1080×2424 px at 420 dpi. */
private const val Pixel9Qualifiers = "w411dp-h923dp-port-420dpi"
