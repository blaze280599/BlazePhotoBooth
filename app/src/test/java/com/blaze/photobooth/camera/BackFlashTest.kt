package com.blaze.photobooth.camera

import androidx.camera.core.ImageCapture
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.files.DraftConfig
import com.blaze.photobooth.model.BackFlash
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.Lens
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** README §13: the back LED flash is Off unless she picks Auto; the front camera never uses it. */
@RunWith(AndroidJUnit4::class)
class BackFlashTest {

    @Test
    fun backCameraFollowsTheChoice() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, CameraController.flashMode(Lens.Back, BackFlash.Off, screenFlashAvailable = true))
        assertEquals(ImageCapture.FLASH_MODE_AUTO, CameraController.flashMode(Lens.Back, BackFlash.Auto, screenFlashAvailable = false))
    }

    @Test
    fun theDefaultNeverFiresTheLed() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, CameraController.flashMode(Lens.Back, BoothConfig().backFlash, screenFlashAvailable = false))
    }

    @Test
    fun frontCameraUsesTheScreenFlashWhateverTheBackChoice() {
        for (choice in BackFlash.entries) {
            assertEquals(ImageCapture.FLASH_MODE_SCREEN, CameraController.flashMode(Lens.Front, choice, screenFlashAvailable = true))
            assertEquals(ImageCapture.FLASH_MODE_OFF, CameraController.flashMode(Lens.Front, choice, screenFlashAvailable = false))
        }
    }

    @Test
    fun theDraftKeepsTheChoiceAndOldDraftsMeanOff() {
        val auto = BoothConfig(lens = Lens.Back, backFlash = BackFlash.Auto)
        assertEquals(auto, DraftConfig.from(auto).toConfig())
        // A draft written by M3 has no backFlash field: it reads as the default.
        val old = DraftConfig("classic", "theme:strawberry", "original", 5, 2, "back", true)
        assertEquals(BackFlash.Off, old.toConfig().backFlash)
        assertEquals(BackFlash.Off, old.copy(backFlash = "torch").toConfig().backFlash)
    }
}
