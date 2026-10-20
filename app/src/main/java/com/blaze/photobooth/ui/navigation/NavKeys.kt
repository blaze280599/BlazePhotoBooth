package com.blaze.photobooth.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination in the app (README §8.3). Keys are serializable so the back stack survives process death.

@Serializable data object Home : NavKey

@Serializable data object Setup : NavKey

@Serializable data object Capture : NavKey

@Serializable data class Pick(val editSessionId: String? = null) : NavKey

@Serializable data class Result(val sessionId: String) : NavKey

@Serializable data object Album : NavKey

@Serializable data class Detail(val sessionId: String) : NavKey
