package com.blaze.photobooth.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.ui.feature.frames.AddFrameScreen
import com.blaze.photobooth.ui.feature.frames.AddFrameUiState
import com.blaze.photobooth.ui.feature.frames.ManageFramesScreen
import com.blaze.photobooth.ui.feature.setup.SetupScreen
import com.blaze.photobooth.ui.feature.setup.SetupTab
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Her frames (README §4.8, §4.9): the finding animation, Confirm with the numbered windows, naming, the slot editor,
 * the heart pop, Manage frames, and Setup with an imported frame. Frames are code-drawn ([FrameSamples]). One class per
 * state (graphicsLayer gotcha, §15.4).
 */
abstract class FramesShot {
    @get:Rule
    val compose = createComposeRule()

    protected val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    protected fun snap(name: String, dark: Boolean = true, reduceMotion: Boolean = true, atMillis: Long = 1_000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { BoothTheme(darkTheme = dark, reduceMotion = reduceMotion) { content() } }
        compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    protected fun addFrame(name: String, state: AddFrameUiState, dark: Boolean = true, reduceMotion: Boolean = true, atMillis: Long = 1_000) =
        snap(name, dark, reduceMotion, atMillis) { AddFrameScreen(state, onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AddFrameFindingScreenshotTest : FramesShot() {
    /** "Finding the photo windows… ✨": the picture dimmed, the sparkly band half-way down. */
    @Test
    fun finding() = addFrame("add_frame_finding_dark", FrameSamples.finding(), reduceMotion = false, atMillis = 700)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AddFrameConfirmScreenshotTest : FramesShot() {
    /** A her-style 4-window strip: windows outlined and numbered, placeholder photos peeking through. */
    @Test
    fun confirm() = addFrame("add_frame_confirm_dark", FrameSamples.confirm())
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AddFrameNamingScreenshotTest : FramesShot() {
    /** Looks right → the name ("Frame 1") and Save; a small picture shows the gentle blurry warning. */
    @Test
    fun naming() = addFrame("add_frame_naming_small_light", FrameSamples.confirm(naming = true, tooSmall = true), dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AddFrameEditorScreenshotTest : FramesShot() {
    /** The slot editor: window 2 selected (handles), snapped to the centre line (guide). */
    @Test
    fun editor() = addFrame("add_frame_editor_dark", FrameSamples.editor())
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AddFrameEditorNoWindowsScreenshotTest : FramesShot() {
    /** Nothing found (§13): straight to the editor with one default window. */
    @Test
    fun editorNoWindows() = addFrame("add_frame_editor_none_light", FrameSamples.editorNoWindows(), dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AddFrameSavedScreenshotTest : FramesShot() {
    /** The heart pop after Save, mid-bounce. */
    @Test
    fun saved() = addFrame("add_frame_saved_dark", FrameSamples.saved(), reduceMotion = false, atMillis = 400)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ManageFramesLightScreenshotTest : FramesShot() {
    @Test
    fun manageLight() = snap("manage_frames_light", dark = false) { ManageFramesScreen(FrameSamples.manage(context), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ManageFramesDarkScreenshotTest : FramesShot() {
    @Test
    fun manageDark() = snap("manage_frames_dark") { ManageFramesScreen(FrameSamples.manage(context), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class SetupImportedFrameScreenshotTest : FramesShot() {
    /** Setup's Frame tab: her imported frame selected, after her bundled ones in "Her frames ♡". */
    @Test
    fun setupImported() = snap("setup_frame_imported_dark") {
        SetupScreen(FrameSamples.setupImported(context), onEvent = {}, initialTab = SetupTab.Frame)
    }
}
