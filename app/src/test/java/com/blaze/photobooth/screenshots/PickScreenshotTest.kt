package com.blaze.photobooth.screenshots

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.SlotSelection
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.ui.feature.pick.PickScreen
import com.blaze.photobooth.ui.feature.pick.PickShot
import com.blaze.photobooth.ui.feature.pick.PickTab
import com.blaze.photobooth.ui.feature.pick.PickUiState
import com.blaze.photobooth.ui.feature.setup.FrameCardState
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Pick (README §4.4) as JVM screenshots with generated pastel "photos" (no people). Pick uses graphicsLayer (the
 * pulsing "+", badges popping in), which Robolectric draws blank after another Compose test in the same JVM
 * (§15.4), so every state has its own one-test class.
 */
internal object PickSamples {
    private val date = LocalDate.of(2026, 10, 20)
    private val context get() = RuntimeEnvironment.getApplication()

    private fun shots(count: Int, cuts: Int, w: Int, h: Int) = List(count) { i ->
        val bitmap = TestArt.photo(i + 1, w, h)
        PickShot(i, bitmap, bitmap.asImageBitmap(), bonus = i >= cuts, version = 0)
    }

    private fun frames(): List<FrameCardState> {
        val her = TestArt.herFrames(context).map { FrameCardState(FrameOption.Artwork(it), TestArt.load(context, it.thumb)) }
        return her + FrameTheme.entries.map { FrameCardState(FrameOption.Theme(it)) }
    }

    /** Her Lilac Pals (3 windows) with 2 bonus shots; [picked] = which shot sits in each slot. */
    fun lilac(picked: List<Int?>, filter: PhotoFilter = PhotoFilter.Original): PickUiState {
        val frame = TestArt.herFrames(context).first { it.id == FrameId.asset("lilac-pals") }
        val art = TestArt.load(context, frame.artwork, maxWidth = BitmapOps.PreviewMaxPx)
        return PickUiState(
            loaded = true,
            preview = StripSpec(FrameSource.Image(frame, art), filter = filter, date = date, preview = true),
            shots = shots(5, 3, 600, 485),
            selection = SlotSelection(picked),
            frames = frames(),
            frameId = frame.id,
            filter = filter,
            cuts = 3,
        )
    }

    /** Strawberry Milk classic strip, 6 shots (4 + 2 bonus). */
    fun theme(picked: List<Int?>, caption: String = "", filter: PhotoFilter = PhotoFilter.Milky): PickUiState = PickUiState(
        loaded = true,
        preview = StripSpec(
            FrameSource.Theme(FrameTheme.Strawberry),
            BoothLayout.Classic,
            filter,
            caption = caption.ifEmpty { null },
            date = date,
            preview = true,
        ),
        shots = shots(6, 4, 600, 420),
        selection = SlotSelection(picked),
        frames = frames(),
        frameId = FrameId.theme(FrameTheme.Strawberry),
        filter = filter,
        caption = caption,
        cuts = 4,
    )
}

abstract class PickShotBase {
    @get:Rule
    val compose = createComposeRule()

    protected fun snap(
        name: String,
        state: PickUiState,
        dark: Boolean = true,
        tab: PickTab = PickTab.Cuts,
        rejectCount: Int = 0,
        atMillis: Long? = null,
    ) {
        if (atMillis != null) compose.mainClock.autoAdvance = false
        compose.setContent {
            BoothTheme(darkTheme = dark) {
                PickScreen(state = state, onEvent = {}, initialTab = tab, rejectCount = rejectCount)
            }
        }
        if (atMillis != null) compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickEmptyScreenshotTest : PickShotBase() {
    /** Nothing picked yet: dashed slots with a "+", Print asleep. */
    @Test
    fun empty() = snap("pick_empty_lilac_dark", PickSamples.lilac(listOf(null, null, null)))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickPartialScreenshotTest : PickShotBase() {
    /** Two of four slots filled (shot 5, a bonus, swapped in for shot 2). */
    @Test
    fun partial() = snap("pick_partial_theme_light", PickSamples.theme(listOf(0, 4, null, null)), dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickFullLilacScreenshotTest : PickShotBase() {
    /** Her 3-window frame, all filled, the bonus shots spare: ready to print. */
    @Test
    fun full() = snap("pick_full_lilac_dark", PickSamples.lilac(listOf(0, 3, 2), PhotoFilter.Peach))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickFullThemeScreenshotTest : PickShotBase() {
    /** A theme strip, full, with a caption and the date in the footer. */
    @Test
    fun full() = snap("pick_full_theme_dark", PickSamples.theme(listOf(0, 1, 5, 3), caption = "우리 둘 ♡ best day"))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickPrintDisabledScreenshotTest : PickShotBase() {
    /** Print tapped with an empty slot: 130 ms into the shake, the empty slot blushing. */
    @Test
    fun printDisabled() = snap(
        "pick_print_disabled_dark",
        PickSamples.lilac(listOf(0, null, 2)),
        rejectCount = 1,
        atMillis = 130,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickTextThemeScreenshotTest : PickShotBase() {
    /** Text tab on a theme: caption in Gaegu and the date switch. */
    @Test
    fun text() = snap("pick_text_theme_light", PickSamples.theme(listOf(0, 1, 2, 3), caption = "우리 둘 ♡ best day"), dark = false, tab = PickTab.Text)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickTextHerFrameScreenshotTest : PickShotBase() {
    /** Text tab on her frame: no text area, so a gentle hint instead of the fields. */
    @Test
    fun text() = snap("pick_text_her_frame_dark", PickSamples.lilac(listOf(0, 1, 2)), tab = PickTab.Text)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickFrameTabScreenshotTest : PickShotBase() {
    /** Frame tab: her frames first, then the themes. */
    @Test
    fun frames() = snap("pick_frame_tab_dark", PickSamples.lilac(listOf(0, 1, 2)), tab = PickTab.Frame)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickPeekScreenshotTest : PickShotBase() {
    /** Long-press peek on a bonus shot, with Retake. */
    @Test
    fun peek() {
        val state = PickSamples.lilac(listOf(0, 1, 2))
        snap("pick_peek_dark", state.copy(peek = state.shots[4]), atMillis = 400)
    }
}
