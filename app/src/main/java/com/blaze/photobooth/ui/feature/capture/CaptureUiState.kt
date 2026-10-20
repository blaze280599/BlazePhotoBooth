package com.blaze.photobooth.ui.feature.capture

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.blaze.photobooth.camera.CameraStatus
import com.blaze.photobooth.model.BackFlash
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.Pose
import com.blaze.photobooth.model.Poses

/**
 * Everything Capture shows (README §4.3). [tray] has one entry per planned shot; [aspect] is the
 * current shot's slot aspect (width ÷ height), which the viewfinder is cropped to.
 * With [retakeIndex] set, Capture shoots only that one shot again (Pick's "retake", §4.4): the tray has one slot.
 */
@Immutable
data class CaptureUiState(
    val loaded: Boolean = false,
    val cuts: Int = 0,
    val sequence: SequenceState = SequenceState(),
    val timerSec: Int = BoothConfig.DefaultTimer,
    val aspect: Float = 4f / 3f,
    /** ARGB of the chosen frame, drawn around the viewfinder. */
    val frameColor: Int = DefaultFrameColor,
    val filter: PhotoFilter = PhotoFilter.Original,
    val lens: Lens = Lens.Front,
    val mirror: Boolean = true,
    /** The back camera's LED flash (§13), toggled from the top bar while the back camera is on. */
    val backFlash: BackFlash = BackFlash.Default,
    val retakeIndex: Int? = null,
    val lenses: Set<Lens> = emptySet(),
    val camera: CameraStatus = CameraStatus.Starting,
    val tray: List<TrayShot> = emptyList(),
    /** The newest shot flying into the tray (§7 A6), or null. */
    val flight: ShotFlight? = null,
    /** Bumped on every shutter; each bump plays the white flash (§7 A6). */
    val flashCount: Int = 0,
    /** < 50 MB free (§13): the shutter asks first. */
    val lowStorage: Boolean = false,
    val showStorageWarning: Boolean = false,
    /** Bumped by Retry so the camera binds again. */
    val bindAttempt: Int = 0,
    /** The session's pose plan (§5.6): one pose id per shot, from the draft. */
    val posePlan: List<String> = emptyList(),
    /** Settings → Pose ideas (§5.6.1): off means no pose card. */
    val poseIdeas: Boolean = true,
) {
    val taken: Int get() = sequence.taken
    val total: Int get() = sequence.total
    val phase: SequencePhase get() = sequence.phase
    val running: Boolean get() = sequence.running

    /** The shot being taken next, 1-based, for the "2 / 6" pill. */
    val shotNumber: Int get() = (taken + 1).coerceAtMost(total.coerceAtLeast(1))

    /** Bonus shots (beyond the strip's cuts) get a little ✦ in the pill. */
    val isBonus: Boolean get() = retakeIndex == null && taken in cuts until total

    /** The flash toggle shows only for the back camera (the front one has the screen flash, §9.5). */
    val showFlashToggle: Boolean get() = lens == Lens.Back && loaded

    /** Flip only while nothing is counting down or capturing (§4.3), and only if there is another camera. */
    val canFlip: Boolean get() = !running && phase != SequencePhase.Done && lenses.size > 1

    val cameraFailed: Boolean get() = camera is CameraStatus.Failed

    /**
     * The shot whose pose the card shows (§5.6.1): the next one to take (during the beat that's already the next
     * shot, for *"Next: …"*), or the retaken shot. Null once there's nothing left to take.
     */
    val poseIndex: Int?
        get() = when {
            !loaded || phase == SequencePhase.Done -> null
            retakeIndex != null -> retakeIndex.takeIf { taken == 0 }
            else -> taken.takeIf { it < total }
        }

    /** The pose the card shows, or null for no card (Pose ideas off, or no shot left). */
    val shownPose: Pose? get() = if (poseIdeas) poseIndex?.let { Poses.find(posePlan.getOrNull(it)) } else null

    /** Which card (§5.6.1): big before a countdown, smaller during it, *"Next: …"* during the beat. */
    val poseCardKind: PoseCardKind?
        get() = when (phase) {
            SequencePhase.Idle -> if (taken == 0 && retakeIndex == null) PoseCardKind.FirstUp else PoseCardKind.UpNext
            SequencePhase.Paused -> PoseCardKind.UpNext
            is SequencePhase.Countdown, SequencePhase.Capturing -> PoseCardKind.Now
            SequencePhase.Beat -> PoseCardKind.Next
            SequencePhase.Done -> null
        }

    /** Tap-to-swap works on any card except while the photo is being taken. */
    val canSwapPose: Boolean get() = shownPose != null && phase != SequencePhase.Capturing

    companion object {
        const val DefaultFrameColor = 0xFFFFD6E3.toInt()
    }
}

/** One tray slot: its slot aspect and, once taken, a small thumbnail (unfiltered; the screen applies the filter). */
@Immutable
data class TrayShot(val aspect: Float, val thumbnail: ImageBitmap? = null)

/** The thumbnail of shot [index] arcing from the viewfinder into its tray slot; [key] restarts the flight. */
@Immutable
data class ShotFlight(val index: Int, val key: Int)

sealed interface CaptureEvent {
    /** The big button: start, pause or resume. */
    data object Shutter : CaptureEvent
    data object Flip : CaptureEvent

    /** The top bar's back-flash toggle: Off ↔ Auto (§13). */
    data object ToggleBackFlash : CaptureEvent

    /** Tap on the pose card (§5.6.1): another unused pose for the shot it shows. */
    data object SwapPose : CaptureEvent
    data object RetryCamera : CaptureEvent
    data object StartAnyway : CaptureEvent
    data object DismissStorageWarning : CaptureEvent

    /** ✕ or back. The route asks first when shots would be lost. */
    data object Close : CaptureEvent

    /** The pre-prompt card's button: show the system dialog (§12). */
    data object RequestPermission : CaptureEvent

    /** Permission was permanently denied: open the app's page in system Settings. */
    data object OpenSettings : CaptureEvent
}

/** One-shot things the route turns into sound, haptics, snackbars and navigation. */
sealed interface CaptureEffect {
    data class Tick(val secondsLeft: Int) : CaptureEffect
    data object Shutter : CaptureEffect
    data object SaveFailed : CaptureEffect
    data object Finished : CaptureEffect
}
