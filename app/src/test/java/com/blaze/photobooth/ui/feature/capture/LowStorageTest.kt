package com.blaze.photobooth.ui.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §13: with less than 50 MB free, the shutter asks before the first shot; never mid-session. */
class LowStorageTest {

    private val mb = 1024L * 1024

    @Test
    fun under50MbIsLow() {
        assertEquals(50 * mb, CaptureViewModel.LowStorageBytes)
        assertTrue(CaptureViewModel.isLowStorage(0))
        assertTrue(CaptureViewModel.isLowStorage(50 * mb - 1))
        assertFalse(CaptureViewModel.isLowStorage(50 * mb))
        // The free-space reader falls back to "plenty" when it can't tell.
        assertFalse(CaptureViewModel.isLowStorage(Long.MAX_VALUE))
    }

    @Test
    fun itAsksOnlyBeforeTheFirstShot() {
        assertTrue(CaptureViewModel.asksAboutStorage(running = false, taken = 0, lowStorage = true, acknowledged = false))
        // Plenty of room, a session already under way, or she already said "Start anyway": no question.
        assertFalse(CaptureViewModel.asksAboutStorage(running = false, taken = 0, lowStorage = false, acknowledged = false))
        assertFalse(CaptureViewModel.asksAboutStorage(running = false, taken = 2, lowStorage = true, acknowledged = false))
        assertFalse(CaptureViewModel.asksAboutStorage(running = true, taken = 0, lowStorage = true, acknowledged = false))
        assertFalse(CaptureViewModel.asksAboutStorage(running = false, taken = 0, lowStorage = true, acknowledged = true))
    }
}
