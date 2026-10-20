package com.blaze.photobooth.ui.feature.home

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.BoothConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

/** README §4.1: Home's badge counts the album, the peek is the newest strip's thumbnail, and the draft card is the draft. */
@RunWith(AndroidJUnit4::class)
class HomeSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BoothDatabase
    private lateinit var repo: SessionRepository
    private lateinit var draftStore: BoothDraftStore
    private lateinit var source: BoothHomeSource

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoothDatabase::class.java).allowMainThreadQueries().build()
        val dir = tmp.newFolder("sessions")
        draftStore = BoothDraftStore(dir, io = Dispatchers.Unconfined)
        repo = SessionRepository(db.sessions(), SessionFileStore(dir), draftStore, io = Dispatchers.Unconfined)
        source = BoothHomeSource(draftStore, repo, io = Dispatchers.Unconfined)
    }

    @After
    fun close() = db.close()

    private suspend fun saved(id: String, created: Long, thumb: Boolean = true) {
        repo.files.dir(id).mkdirs()
        repo.files.strip(id).writeText("strip")
        if (thumb) repo.files.thumb(id).writeText("thumb")
        db.sessions().insert(SessionEntity(id, created, created, "classic", "theme:milk", "original", null, true), emptyList())
    }

    @Test
    fun anEmptyAlbumShowsNoBadgeAndNoPeek() = runTest {
        assertEquals(0, source.albumCount.first())
        assertNull(source.recentStrip.first())
        assertNull(source.draft.first())
    }

    @Test
    fun theBadgeCountsLiveStripsAndThePeekIsTheNewest() = runTest {
        saved("a", 1_000)
        saved("b", 3_000)
        saved("c", 2_000)
        assertEquals(3, source.albumCount.first())
        val peek = checkNotNull(source.recentStrip.first())
        assertEquals("b", peek.sessionId)
        assertEquals(repo.files.thumb("b"), peek.thumbnail)
        assertEquals(repo.files.thumb("b").lastModified(), peek.version)

        repo.softDelete(listOf("b"))
        assertEquals(2, source.albumCount.first())
        assertEquals("c", source.recentStrip.first()?.sessionId)
    }

    @Test
    fun aLostThumbnailPeeksWithTheStrip() = runTest {
        saved("a", 1_000, thumb = false)
        assertEquals(repo.files.strip("a"), source.recentStrip.first()?.thumbnail)
        File(repo.files.dir("a"), SessionFileStore.StripName).delete()
        assertNull("nothing to show, but the peek still opens Detail", source.recentStrip.first()?.thumbnail)
    }

    @Test
    fun theDraftCardFollowsTheDraft() = runTest {
        draftStore.begin(BoothConfig(), totalShots = 6)
        assertEquals(DraftSummary(shotCount = 0, complete = false), source.draft.first())
    }
}
