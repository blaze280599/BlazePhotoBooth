package com.blaze.photobooth.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination in the app (README §8.3). Keys are serializable so the back stack survives process death.

@Serializable data object Home : NavKey

@Serializable data object Setup : NavKey

/** The import flow for a new image frame (§4.9), from Setup's "+ Add frame" card or Manage frames. */
@Serializable data object AddFrame : NavKey

/** Manage frames (§4.8), from the Settings sheet. */
@Serializable data object ManageFrames : NavKey

/** The booth. With [retakeIndex], it shoots only that one shot again and returns to Pick (§4.4). */
@Serializable data class Capture(val retakeIndex: Int? = null) : NavKey

@Serializable data class Pick(val editSessionId: String? = null) : NavKey

@Serializable data class Result(val sessionId: String) : NavKey

@Serializable data object Album : NavKey

@Serializable data class Detail(val sessionId: String) : NavKey
