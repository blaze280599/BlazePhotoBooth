package com.blaze.photobooth.ui.feature.home

import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * What Home needs from the data layer. M1 shipped [EmptyHomeSource]; M3 lit up the draft card and M5 the badge
 * and strip peek with [BoothHomeSource], without UI changes.
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

/**
 * The real source: the unfinished session from [BoothDraftStore] (§11.4), and the album's count and newest strip
 * from [SessionRepository] (§4.1).
 */
class BoothHomeSource(
    private val draftStore: BoothDraftStore,
    sessions: SessionRepository,
    io: CoroutineDispatcher = Dispatchers.IO,
) : HomeSource {
    override val albumCount: Flow<Int> = sessions.count

    override val recentStrip: Flow<RecentStrip?> = sessions.latest
        .map { it?.let { session -> recentStripOf(session, sessions.files) } }
        .distinctUntilChanged()
        .flowOn(io)

    override val draft: Flow<DraftSummary?> = draftStore.draft.map { d ->
        d?.let { DraftSummary(shotCount = it.taken, complete = it.complete) }
    }

    override suspend fun discardDraft() = draftStore.discard()

    companion object {
        /** The peek shows the thumbnail (or the strip when the thumbnail is lost). Touches the disk. */
        fun recentStripOf(session: SessionEntity, files: SessionFileStore) =
            files.thumbOrStrip(session.id).let { file ->
                val present = file.isFile
                RecentStrip(sessionId = session.id, thumbnail = file.takeIf { present }, version = if (present) file.lastModified() else 0)
            }
    }
}
