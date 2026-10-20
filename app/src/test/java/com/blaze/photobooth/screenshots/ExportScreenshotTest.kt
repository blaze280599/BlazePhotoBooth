package com.blaze.photobooth.screenshots

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.R
import com.blaze.photobooth.data.export.AutoSaveStatus
import com.blaze.photobooth.data.export.ExportError
import com.blaze.photobooth.ui.feature.album.AlbumScreen
import com.blaze.photobooth.ui.feature.detail.DetailScreen
import com.blaze.photobooth.ui.feature.detail.DetailTab
import com.blaze.photobooth.ui.feature.export.ExportActionState
import com.blaze.photobooth.ui.feature.export.ExportActions
import com.blaze.photobooth.ui.feature.export.ExportCopy
import com.blaze.photobooth.ui.feature.export.ExportKey
import com.blaze.photobooth.ui.feature.export.ExportMessage
import com.blaze.photobooth.ui.feature.export.ExportPhase
import com.blaze.photobooth.ui.feature.export.SavedWhat
import com.blaze.photobooth.ui.feature.result.ResultScreen
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Save to Photos and Share (README §4.5–§4.7, §7 A12, §13): the live buttons, the ✓ with its snackbar, Retry after an
 * error, batch progress, and the album's *Save to Photos*. The snackbar text comes from the real [ExportCopy]. One
 * class per state (graphicsLayer gotcha, §15.4).
 */
abstract class ExportShot {
    @get:Rule
    val compose = createComposeRule()

    protected fun snap(
        name: String,
        dark: Boolean = true,
        reduceMotion: Boolean = true,
        atMillis: Long = 1_500,
        message: ExportMessage? = null,
        content: @Composable (SnackbarHostState) -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoothTheme(darkTheme = dark, reduceMotion = reduceMotion) {
                FakeImages {
                    val host = remember { SnackbarHostState() }
                    if (message != null) {
                        val text = ExportCopy.text(LocalResources.current, message)
                        val retry = (message as? ExportMessage.Failed)?.retry?.let { LocalResources.current.getString(R.string.export_retry) }
                        LaunchedEffect(Unit) {
                            host.showSnackbar(text, actionLabel = retry, withDismissAction = retry != null, duration = SnackbarDuration.Indefinite)
                        }
                    }
                    content(host)
                }
            }
        }
        compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    protected fun actions(vararg states: Pair<ExportKey, ExportActionState>, busy: Boolean = false) = ExportActions(mapOf(*states), busy)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultActionsScreenshotTest : ExportShot() {
    /** Printed: Save strip, Save photos and Share are live (no more "Soon"). */
    @Test
    fun actions() = snap("result_actions_dark") { host ->
        ResultScreen(ResultSamples.state().copy(cuts = 3), onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultSavedScreenshotTest : ExportShot() {
    /** Save strip turned into a ✓, "Strip saved to Photos" slides in. */
    @Test
    fun saved() = snap("result_saved_dark", message = ExportMessage.Saved(SavedWhat.Strip, 1)) { host ->
        val state = ResultSamples.state().copy(cuts = 3, actions = actions(ExportKey.Strip to ExportActionState.Done))
        ResultScreen(state, onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultAutoSavedScreenshotTest : ExportShot() {
    /** Settings' auto-save was on (§4.8): the note under the chip, and Save strip is already ✓. Light theme. */
    @Test
    fun autoSaved() = snap("result_auto_saved_light", dark = false) { host ->
        val state = ResultSamples.state().copy(
            cuts = 3,
            autoSave = AutoSaveStatus.Saved,
            actions = actions(ExportKey.Strip to ExportActionState.Done),
        )
        ResultScreen(state, onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class ResultErrorScreenshotTest : ExportShot() {
    /** §13: saving failed; the snackbar offers Retry and the buttons are ready again. */
    @Test
    fun error() = snap("result_error_light", dark = false, message = ExportMessage.Failed(ExportError.Failed, saved = 0, retry = {})) { host ->
        ResultScreen(ResultSamples.state().copy(cuts = 3), onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailActionsScreenshotTest : ExportShot() {
    /** The Photos tab: Save strip, Save photo (the cut in view), Save all and Share, all live. */
    @Test
    fun actions() = snap("detail_actions_dark", reduceMotion = false, atMillis = 1_000) { host ->
        DetailScreen(AlbumSamples.detail(), onEvent = {}, initialTab = DetailTab.Photos, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailSavedScreenshotTest : ExportShot() {
    /** Save photo on cut 1: its ✓, and "Photo saved to Photos". */
    @Test
    fun saved() = snap("detail_saved_dark", reduceMotion = false, atMillis = 1_000, message = ExportMessage.Saved(SavedWhat.Photos, 1)) { host ->
        val state = AlbumSamples.detail().let { it.copy(actions = actions(ExportKey.Photo(it.cuts.first().shotIndex) to ExportActionState.Done)) }
        DetailScreen(state, onEvent = {}, initialTab = DetailTab.Photos, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailSavingScreenshotTest : ExportShot() {
    /** Save all mid-batch (§13): "2 of 3" under a spinner; the other buttons wait. */
    @Test
    fun saving() = snap("detail_saving_dark", reduceMotion = false, atMillis = 1_000) { host ->
        val state = AlbumSamples.detail().copy(
            actions = actions(ExportKey.AllPhotos to ExportActionState(ExportPhase.Busy, done = 1, total = 3), busy = true),
        )
        DetailScreen(state, onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailMissingScreenshotTest : ExportShot() {
    /** §13: a file is gone, so the error is friendly and offers no Retry. Light theme, Strip tab. */
    @Test
    fun missing() = snap(
        "detail_missing_light",
        dark = false,
        reduceMotion = false,
        atMillis = 1_000,
        message = ExportMessage.Failed(ExportError.MissingFile, saved = 0, retry = null),
    ) { host ->
        DetailScreen(AlbumSamples.detail(), onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumSaveSelectedScreenshotTest : ExportShot() {
    /** Multi-select *Save to Photos* (§4.6), one strip after another: "Saving 2 of 2…". */
    @Test
    fun saving() = snap("album_select_save_dark", reduceMotion = false, atMillis = 1_000) { host ->
        val state = AlbumSamples.album(selection = setOf("s1", "s3")).copy(
            actions = actions(ExportKey.Selection to ExportActionState(ExportPhase.Busy, done = 1, total = 2), busy = true),
        )
        AlbumScreen(state, onEvent = {}, snackbarHostState = host)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumSavedSelectedScreenshotTest : ExportShot() {
    /** Done: the ✓, and "2 strips saved to Photos". */
    @Test
    fun saved() = snap("album_select_saved_dark", reduceMotion = false, atMillis = 1_000, message = ExportMessage.Saved(SavedWhat.Strips, 2)) { host ->
        val state = AlbumSamples.album(selection = setOf("s1", "s3")).copy(actions = actions(ExportKey.Selection to ExportActionState.Done))
        AlbumScreen(state, onEvent = {}, snackbarHostState = host)
    }
}
