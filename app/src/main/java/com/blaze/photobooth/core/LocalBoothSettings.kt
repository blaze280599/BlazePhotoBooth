package com.blaze.photobooth.core

import androidx.compose.runtime.compositionLocalOf
import com.blaze.photobooth.data.prefs.BoothSettings

/**
 * The Settings sheet's switches (README §4.8), live from DataStore, for every screen: [rememberBoothHaptics] obeys
 * `haptics` everywhere. Provided by `MainActivity`; previews and screenshot tests get the defaults.
 */
val LocalBoothSettings = compositionLocalOf { BoothSettings() }
