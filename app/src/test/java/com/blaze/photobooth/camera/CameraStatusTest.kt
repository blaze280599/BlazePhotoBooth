package com.blaze.photobooth.camera

import androidx.camera.core.CameraState
import org.junit.Assert.assertEquals
import org.junit.Test

/** README §13: the camera fails to bind or another app is using it → a friendly card with Retry. */
class CameraStatusTest {

    private fun state(type: CameraState.Type, error: Int? = null) =
        CameraState.create(type, error?.let { CameraState.StateError.create(it) })

    private val inUse = CameraStatus.Failed(CameraStatus.Reason.InUse)

    @Test
    fun anOpenCameraIsReady() {
        assertEquals(CameraStatus.Ready, CameraController.statusFor(state(CameraState.Type.OPEN), CameraStatus.Starting))
    }

    @Test
    fun anotherAppUsingTheCameraSaysSo() {
        for (code in listOf(CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE)) {
            assertEquals(inUse, CameraController.statusFor(state(CameraState.Type.PENDING_OPEN, code), CameraStatus.Ready))
        }
    }

    @Test
    fun aDisabledOrBrokenCameraShowsTheErrorCard() {
        assertEquals(
            CameraStatus.Failed(CameraStatus.Reason.Disabled),
            CameraController.statusFor(state(CameraState.Type.CLOSED, CameraState.ERROR_CAMERA_DISABLED), CameraStatus.Ready),
        )
        assertEquals(
            CameraStatus.Failed(CameraStatus.Reason.Other),
            CameraController.statusFor(state(CameraState.Type.CLOSED, CameraState.ERROR_CAMERA_FATAL_ERROR), CameraStatus.Ready),
        )
    }

    @Test
    fun theErrorStaysWhileTheCameraClosesAfterIt() {
        assertEquals(inUse, CameraController.statusFor(state(CameraState.Type.CLOSING), inUse))
        assertEquals(inUse, CameraController.statusFor(state(CameraState.Type.CLOSED), inUse))
        // Without an error, closing or opening is just "starting".
        assertEquals(CameraStatus.Starting, CameraController.statusFor(state(CameraState.Type.OPENING), CameraStatus.Ready))
    }

    @Test
    fun aRecoverableErrorWhileOpeningDoesNotShowTheCard() {
        // ERROR_STREAM_CONFIG is critical; ERROR_OTHER_RECOVERABLE_ERROR is a hiccup CameraX retries by itself.
        assertEquals(
            CameraStatus.Starting,
            CameraController.statusFor(state(CameraState.Type.OPENING, CameraState.ERROR_OTHER_RECOVERABLE_ERROR), CameraStatus.Starting),
        )
    }
}
