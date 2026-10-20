package com.blaze.photobooth.render

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.screenshots.Pixel9Qualifiers
import com.blaze.photobooth.screenshots.TestArt
import com.blaze.photobooth.ui.components.StripPreview
import com.blaze.photobooth.ui.theme.BoothTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * README §13: dark mode changes the app's UI, never a frame theme, so a strip looks the same everywhere (and the
 * preview keeps matching the export).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class FrameDarkModeTest {

    @get:Rule
    val compose = createComposeRule()

    private val spec = StripSpec(
        FrameSource.Theme(FrameTheme.Strawberry),
        BoothLayout.Classic,
        caption = "best day",
        date = LocalDate.of(2026, 10, 20),
        preview = true,
    )

    @Test
    fun thePreviewIsTheSameInLightAndDarkTheme() {
        var dark by mutableStateOf(false)
        compose.setContent {
            BoothTheme(darkTheme = dark, reduceMotion = true) {
                // The screen behind it does change: only the strip must not.
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    StripPreview(spec, Modifier.height(320.dp).testTag("strip"))
                }
            }
        }
        val light = compose.onNodeWithTag("strip").captureToImage()
        dark = true
        compose.waitForIdle()
        val night = compose.onNodeWithTag("strip").captureToImage()
        assertTrue("the strip changed with dark mode", samePixels(light, night))
    }

    @Test
    fun theRendererIgnoresTheNightConfiguration() {
        val app = RuntimeEnvironment.getApplication()
        val night = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            },
        )
        val photos = TestArt.photos(4)
        for (theme in FrameTheme.entries) {
            val themed = spec.copy(frame = FrameSource.Theme(theme))
            val day = StripRenderer(StripFonts(app.resources)).let { r -> TestArt.renderWith(r, themed, photos, 0.15f) }
            val nightly = StripRenderer(StripFonts(night.resources)).let { r -> TestArt.renderWith(r, themed, photos, 0.15f) }
            assertTrue("${theme.id} changed with dark mode", day.sameAs(nightly))
        }
    }

    private fun samePixels(a: ImageBitmap, b: ImageBitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        for (y in 0 until a.height) for (x in 0 until a.width) if (pa[x, y] != pb[x, y]) return false
        return true
    }
}
