package com.blaze.photobooth.ui.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/** README §12: pre-prompt card first, then the dialog, then "Open settings" once Android stops asking. */
class CameraPermissionTest {

    @Test
    fun grantedWins() {
        assertEquals(CameraPermission.Granted, CameraPermission.of(granted = true, requested = false, showRationale = false))
        assertEquals(CameraPermission.Granted, CameraPermission.of(granted = true, requested = true, showRationale = true))
    }

    @Test
    fun firstVisitShowsThePrePrompt() {
        assertEquals(CameraPermission.NeedsRequest, CameraPermission.of(granted = false, requested = false, showRationale = true))
    }

    @Test
    fun oneDenialCanTryAgain() {
        assertEquals(CameraPermission.Denied, CameraPermission.of(granted = false, requested = true, showRationale = true))
    }

    @Test
    fun deniedForGoodOffersSettings() {
        assertEquals(CameraPermission.Blocked, CameraPermission.of(granted = false, requested = true, showRationale = false))
    }
}
