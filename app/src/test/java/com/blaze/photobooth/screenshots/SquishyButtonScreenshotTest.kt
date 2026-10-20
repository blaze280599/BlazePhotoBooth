package com.blaze.photobooth.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.ui.components.SquishyButtonSamples
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The squishy buttons on their own (README §7 A3). They live in their own class, so their own JVM
 * (`forkEvery = 1`): Robolectric draws their graphicsLayer blank after other Compose tests.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class SquishyButtonScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun squishyButtons() {
        compose.setContent { BoothTheme(darkTheme = false, reduceMotion = false) { SquishyButtonSamples() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/squishy_buttons.png")
    }
}
