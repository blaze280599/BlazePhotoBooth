package com.blaze.photobooth.core

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The booth's haptics (README §6.5), played through the view so they honour the system
 * "touch feedback" setting. [enabled] is the in-app Haptics switch
 * ([com.blaze.photobooth.data.prefs.BoothSettings.haptics]; its toggle joins in M8).
 */
class BoothHaptics(private val view: View) {

    var enabled: Boolean = true

    /** Selecting a chip, card or slot. */
    fun segmentTick() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        perform(constant)
    }

    /** One countdown second. */
    fun clockTick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** A photo was taken; also the ♡ favourite. */
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)

    /** An invalid action, e.g. Print with an empty slot. */
    fun reject() = perform(HapticFeedbackConstants.REJECT)

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberBoothHaptics(): BoothHaptics {
    val view = LocalView.current
    return remember(view) { BoothHaptics(view) }
}
