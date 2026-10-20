package com.blaze.photobooth.ui.feature.home

import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Everything Home shows that comes from data (README §4.1). With an empty album and no draft,
 * the badge, the recent-strip peek and the draft card all stay hidden.
 */
@Immutable
data class HomeUiState(
    val albumCount: Int = 0,
    val recentStrip: RecentStrip? = null,
    val draft: DraftSummary? = null,
)

/** The newest strip in the album, peeking out behind the Album button. */
@Immutable
data class RecentStrip(val sessionId: String, val thumbnail: File?)

/** An unfinished booth session that can be continued (§11.4). */
@Immutable
data class DraftSummary(val shotCount: Int)

sealed interface HomeEvent {
    data object Start : HomeEvent
    data object OpenAlbum : HomeEvent
    data object OpenSettings : HomeEvent
    data class OpenStrip(val sessionId: String) : HomeEvent
    data object ContinueDraft : HomeEvent
    data object DiscardDraft : HomeEvent
}
