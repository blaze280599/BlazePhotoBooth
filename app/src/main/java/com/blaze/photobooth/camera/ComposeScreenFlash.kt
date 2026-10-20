package com.blaze.photobooth.camera

import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import androidx.camera.core.ImageCapture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The front camera's "flash" (README §9.5): CameraX calls [apply] just before a photo, and the Capture
 * screen turns fully white ([visible]) at maximum brightness to light the face. [clear] puts both back.
 */
class ComposeScreenFlash(private val window: Window) : ImageCapture.ScreenFlash {

    /** Draw a full-screen white overlay while this is true. */
    var visible by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var savedBrightness: Float? = null

    override fun apply(expirationTimeMillis: Long, listener: ImageCapture.ScreenFlashListener) {
        main.post {
            visible = true
            val attrs = window.attributes
            if (savedBrightness == null) savedBrightness = attrs.screenBrightness
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = attrs
            // Give the white frame time to reach the panel (and the backlight to ramp) before exposing.
            main.postDelayed({ listener.onCompleted() }, SettleMillis)
        }
    }

    override fun clear() {
        main.post { restore() }
    }

    /** Also called when the screen goes away mid-flash. */
    fun restore() {
        visible = false
        val saved = savedBrightness ?: return
        val attrs = window.attributes
        attrs.screenBrightness = saved
        window.attributes = attrs
        savedBrightness = null
    }

    private companion object {
        const val SettleMillis = 150L
    }
}
