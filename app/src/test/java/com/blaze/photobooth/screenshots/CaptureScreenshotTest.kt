package com.blaze.photobooth.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.camera.CameraStatus
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.CapturePlan
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.render.FrameColors
import com.blaze.photobooth.render.LayoutGeometry
import com.blaze.photobooth.ui.feature.capture.CameraPermission
import com.blaze.photobooth.ui.feature.capture.CaptureScreen
import com.blaze.photobooth.ui.feature.capture.CaptureUiState
import com.blaze.photobooth.ui.feature.capture.SequencePhase
import com.blaze.photobooth.ui.feature.capture.SequenceState
import com.blaze.photobooth.ui.feature.capture.TrayShot
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capture (README §4.3) as JVM screenshots, with a fake viewfinder (a numbered pastel "photo", no camera).
 * The screen uses graphicsLayer (shutter squish, countdown pop), which Robolectric draws blank after
 * another Compose test in the same JVM (§15.4), so every state has its own one-test class.
 */
internal object CaptureSamples {

    private val classic = CapturePlan(LayoutGeometry.cells(BoothLayout.Classic), bonusShots = 2)

    /** Strawberry Milk classic strip, 6 shots, front camera, [taken] already in the tray. */
    fun theme(phase: SequencePhase, taken: Int = 0, flashCount: Int = 0): CaptureUiState {
        val thumbs = List(taken) { TestArt.photo(it + 1, 320, 224).asImageBitmap() }
        return CaptureUiState(
            loaded = true,
            cuts = classic.cuts,
            sequence = SequenceState(phase = phase, taken = taken, total = classic.totalShots),
            timerSec = 5,
            aspect = classic.aspectFor(taken),
            frameColor = FrameColors.of(FrameTheme.Strawberry),
            lens = Lens.Front,
            lenses = setOf(Lens.Front, Lens.Back),
            camera = CameraStatus.Ready,
            tray = List(classic.totalShots) { i -> TrayShot(classic.aspectFor(i), thumbs.getOrNull(i)) },
            flashCount = flashCount,
        )
    }

    /** Her Lilac Pals (3 windows) + 2 bonus, mid-sequence. */
    fun lilac(phase: SequencePhase, taken: Int): CaptureUiState {
        val frame = TestArt.herFrames(RuntimeEnvironment.getApplication()).first { it.id == FrameId.asset("lilac-pals") }
        val plan = CapturePlan(frame.slots, bonusShots = 2)
        val thumbs = List(taken) { TestArt.photo(it + 1, 320, 260).asImageBitmap() }
        return theme(phase, 0).copy(
            cuts = plan.cuts,
            sequence = SequenceState(phase = phase, taken = taken, total = plan.totalShots),
            aspect = plan.aspectFor(taken),
            frameColor = 0xFFC9B6EE.toInt(),
            tray = List(plan.totalShots) { i -> TrayShot(plan.aspectFor(i), thumbs.getOrNull(i)) },
        )
    }

    /** A soft pastel "scene" standing in for the camera. */
    val fakeViewfinder: @Composable (androidx.compose.ui.Modifier) -> Unit = { modifier ->
        Box(
            modifier.background(
                Brush.linearGradient(listOf(Color(0xFFB8E3F5), Color(0xFFF6D6E8), Color(0xFFFFF1C9))),
            ),
        )
    }
}

/** Shared by the one-test classes below. */
abstract class CaptureShot {
    @get:Rule
    val compose = createComposeRule()

    protected fun snap(
        name: String,
        state: CaptureUiState,
        permission: CameraPermission = CameraPermission.Granted,
        dark: Boolean = true,
        reduceMotion: Boolean = false,
        /** Freeze the clock and capture this many ms in (for mid-animation moments). */
        atMillis: Long? = null,
    ) {
        if (atMillis != null) compose.mainClock.autoAdvance = false
        compose.setContent {
            BoothTheme(darkTheme = dark, reduceMotion = reduceMotion) {
                CaptureScreen(state = state, permission = permission, onEvent = {}, viewfinder = CaptureSamples.fakeViewfinder)
            }
        }
        if (atMillis != null) compose.mainClock.advanceTimeBy(atMillis)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureIdleScreenshotTest : CaptureShot() {
    @Test
    fun idle() = snap("capture_idle_dark", CaptureSamples.theme(SequencePhase.Idle))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureCountdownScreenshotTest : CaptureShot() {
    @Test
    fun countdown() = snap("capture_countdown_dark", CaptureSamples.theme(SequencePhase.Countdown(3), taken = 1))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureLastSecondScreenshotTest : CaptureShot() {
    @Test
    fun lastSecondLight() = snap("capture_last_second_light", CaptureSamples.theme(SequencePhase.Countdown(1), taken = 2), dark = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureFlashScreenshotTest : CaptureShot() {
    /** 60 ms into the 220 ms white flash. */
    @Test
    fun flash() = snap("capture_flash_dark", CaptureSamples.theme(SequencePhase.Capturing, taken = 2, flashCount = 1), atMillis = 60)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureTrayScreenshotTest : CaptureShot() {
    /** Her 3-window frame: three cuts in the tray, the beat after shot 3, the next (bonus) slot glowing. */
    @Test
    fun trayPartlyFilled() = snap("capture_tray_lilac_dark", CaptureSamples.lilac(SequencePhase.Beat, taken = 3))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CapturePermissionScreenshotTest : CaptureShot() {
    @Test
    fun permission() = snap(
        "capture_permission_dark",
        CaptureSamples.theme(SequencePhase.Idle).copy(camera = CameraStatus.Starting),
        permission = CameraPermission.NeedsRequest,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CapturePermissionBlockedScreenshotTest : CaptureShot() {
    @Test
    fun blocked() = snap(
        "capture_permission_blocked_light",
        CaptureSamples.theme(SequencePhase.Idle).copy(camera = CameraStatus.Starting),
        permission = CameraPermission.Blocked,
        dark = false,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class CaptureErrorScreenshotTest : CaptureShot() {
    @Test
    fun error() = snap(
        "capture_error_dark",
        CaptureSamples.theme(SequencePhase.Idle, taken = 1).copy(camera = CameraStatus.Failed(CameraStatus.Reason.InUse)),
    )
}
