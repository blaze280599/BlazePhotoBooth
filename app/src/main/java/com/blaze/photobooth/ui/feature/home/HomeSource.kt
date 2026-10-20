package com.blaze.photobooth.ui.feature.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * What Home needs from the data layer. M1 ships [EmptyHomeSource]; the album (M5) and the
 * draft store (M3) provide a real one through `AppContainer.homeSource`, and Home lights up
 * its badge, strip peek and draft card without UI changes.
 */
interface HomeSource {
    val albumCount: Flow<Int>
    val recentStrip: Flow<RecentStrip?>
    val draft: Flow<DraftSummary?>
    suspend fun discardDraft()
}

/** No strips, no draft: Home's data-driven extras stay hidden. */
object EmptyHomeSource : HomeSource {
    override val albumCount: Flow<Int> = flowOf(0)
    override val recentStrip: Flow<RecentStrip?> = flowOf(null)
    override val draft: Flow<DraftSummary?> = flowOf(null)
    override suspend fun discardDraft() = Unit
}
