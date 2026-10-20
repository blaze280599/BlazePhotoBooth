package com.blaze.photobooth.ui.feature.home

import com.blaze.photobooth.data.files.BoothDraftStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What Home needs from the data layer. M1 shipped [EmptyHomeSource]; M3 lights up the draft card
 * with [BoothHomeSource], and the album (M5) adds the badge and strip peek, without UI changes.
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

/** The real source: the unfinished session from [BoothDraftStore] (§11.4). The album joins in M5. */
class BoothHomeSource(private val draftStore: BoothDraftStore) : HomeSource {
    override val albumCount: Flow<Int> = flowOf(0)
    override val recentStrip: Flow<RecentStrip?> = flowOf(null)
    override val draft: Flow<DraftSummary?> = draftStore.draft.map { d ->
        d?.let { DraftSummary(shotCount = it.taken, complete = it.complete) }
    }

    override suspend fun discardDraft() = draftStore.discard()
}
