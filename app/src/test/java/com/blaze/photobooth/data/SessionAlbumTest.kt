package com.blaze.photobooth.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * README §4.6–4.7, §11.2: favourites, soft delete with one Undo per batch, the startup purge (rows and folders),
 * and Edit's save path (staging folder, one transaction, crash recovery), on an in-memory Room and temp folders.
 */
@RunWith(AndroidJUnit4::class)
class SessionAlbumTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BoothDatabase
    private lateinit var sessionsDir: File
    private lateinit var repo: SessionRepository
    private var now = 100_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoothDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionsDir = tmp.newFolder("sessions")
        repo = SessionRepository(
            db.sessions(),
            SessionFileStore(sessionsDir),
            BoothDraftStore(sessionsDir, io = Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            clock = { now },
        )
    }

    @After
    fun close() = db.close()

    /** A saved classic session with [shots] shots (the first 4 in the strip) and its files on disk. */
    private suspend fun saved(id: String, created: Long, shots: Int = 6) {
        val dir = repo.files.dir(id).apply { mkdirs() }
        repeat(shots) { File(dir, SessionFileStore.shotName(it)).writeText("shot $it") }
        repo.files.strip(id).writeText("strip of $id")
        repo.files.thumb(id).writeText("thumb of $id")
        db.sessions().insert(
            SessionEntity(id, created, created, "classic", "theme:strawberry", "milky", caption = "hi", showDate = true),
            List(shots) { ShotEntity(id, it, slotIndex = it.takeIf { i -> i < 4 }, width = 2400, height = 1680) },
        )
    }

    @Test
    fun softDeleteHidesThenUndoBringsTheWholeBatchBack() = runTest {
        saved("a", 1_000)
        saved("b", 2_000)
        saved("c", 3_000)

        val batch = checkNotNull(repo.softDelete(listOf("a", "c")))
        assertEquals(listOf("b"), repo.sessions.first().map { it.id })
        assertEquals(1, repo.count.first())
        assertEquals(batch, repo.pendingUndo.value)
        assertTrue("files stay until the purge", repo.files.dir("a").isDirectory)

        repo.restore(batch)
        assertEquals(listOf("c", "b", "a"), repo.sessions.first().map { it.id })
        assertNull(repo.pendingUndo.value)
        assertNull(repo.softDelete(emptyList()))
    }

    @Test
    fun theLatestStripSkipsDeletedOnes() = runTest {
        saved("old", 1_000)
        saved("new", 2_000)
        assertEquals("new", repo.latest.first()?.id)
        repo.softDelete(listOf("new"))
        assertEquals("old", repo.latest.first()?.id)
        repo.softDelete(listOf("old"))
        assertNull(repo.latest.first())
    }

    @Test
    fun aDismissedUndoOnlyClearsItsOwnBatch() = runTest {
        saved("a", 1_000)
        saved("b", 2_000)
        val first = checkNotNull(repo.softDelete(listOf("a")))
        val second = checkNotNull(repo.softDelete(listOf("b")))
        repo.dismissUndo(first)
        assertEquals(second, repo.pendingUndo.value)
        repo.dismissUndo(second)
        assertNull(repo.pendingUndo.value)
    }

    @Test
    fun thePurgeRemovesOldSoftDeletesRowsAndFoldersButNotRecentOnes() = runTest {
        saved("old", 1_000)
        saved("recent", 2_000)
        saved("kept", 3_000)
        now = 100_000
        repo.softDelete(listOf("old"))
        now = 108_000
        repo.softDelete(listOf("recent"))

        // The next start, 3 s after the second delete: only the first is more than 10 s old.
        now = 111_000
        assertEquals(1, repo.purgeDeleted())
        assertNull(repo.session("old"))
        assertTrue(db.sessions().shots("old").isEmpty())
        assertFalse(repo.files.dir("old").exists())
        assertTrue(repo.files.dir("recent").isDirectory)
        assertTrue(repo.files.dir("kept").isDirectory)

        // And the start after that.
        now = 200_000
        assertEquals(1, repo.purgeDeleted())
        assertFalse(repo.files.dir("recent").exists())
        assertEquals(listOf("kept"), db.sessions().allIds())
        assertEquals(0, repo.purgeDeleted())
    }

    @Test
    fun aRowWhoseFolderIsAlreadyGoneIsStillPurged() = runTest {
        saved("a", 1_000)
        repo.softDelete(listOf("a"))
        repo.files.dir("a").deleteRecursively() // as if the app died between the two steps
        now += SessionRepository.PurgeAfterMillis + 1
        assertEquals(1, repo.purgeDeleted())
        assertNull(repo.session("a"))
    }

    @Test
    fun favouritesAreSaved() = runTest {
        saved("a", 1_000)
        repo.setFavorite("a", true)
        assertEquals(true, repo.session("a")?.isFavorite)
        repo.setFavorite("a", false)
        assertEquals(false, repo.session("a")?.isFavorite)
    }

    // ---- Edit (§4.7) ----

    private val edited = SessionRepository.editFor(
        BoothConfig(layout = BoothLayout.Trio, frameId = FrameId.theme(FrameTheme.Cloud), filter = PhotoFilter.Mono),
        selection = listOf(5, 0, 2),
        shots = (0 until 6).toList(),
        caption = "  new day ",
        showDate = false,
    )

    @Test
    fun editForMapsEveryShotToItsSlot() {
        assertEquals("trio", edited.layoutId)
        assertEquals("theme:cloud", edited.frameId)
        assertEquals("mono", edited.filterId)
        assertEquals("new day", edited.caption)
        assertEquals(mapOf(0 to 1, 1 to null, 2 to 2, 3 to null, 4 to null, 5 to 0), edited.slots)
        val her = SessionRepository.editFor(BoothConfig(frameId = FrameId.asset("lilac-pals")), listOf(0, 1, 2), listOf(0, 1, 2), null, true)
        assertEquals(SessionEntity.ImageLayoutId, her.layoutId)
    }

    @Test
    fun aSavedEditReplacesTheFilesAndUpdatesTheRows() = runTest {
        saved("a", 1_000)
        now = 50_000
        var rendered: File? = null
        repo.saveEdit("a", edited) { dir ->
            rendered = dir
            File(dir, SessionFileStore.StripName).writeText("new strip")
            File(dir, SessionFileStore.ThumbName).writeText("new thumb")
        }

        assertEquals("rendered next to the session, not over it", repo.files.dir("a"), rendered?.parentFile)
        assertEquals("new strip", repo.files.strip("a").readText())
        assertEquals("new thumb", repo.files.thumb("a").readText())
        assertTrue("no staging folder left", repo.files.editDirs("a").isEmpty())
        val row = checkNotNull(repo.session("a"))
        assertEquals(listOf("trio", "theme:cloud", "mono", "new day"), listOf(row.layoutId, row.frameId, row.filterId, row.caption))
        assertEquals(false, row.showDate)
        assertEquals(50_000L, row.updatedAt)
        assertEquals(1_000L, row.createdAt)
        assertEquals(listOf(1, null, 2, null, null, 0), repo.shots("a").map { it.slotIndex })
        for (i in 0 until 6) assertEquals("every original is kept", "shot $i", repo.files.shot("a", i).readText())
    }

    @Test
    fun aFailedRenderChangesNothing() = runTest {
        saved("a", 1_000)
        try {
            repo.saveEdit("a", edited) { error("out of memory") }
            fail("Expected the render to fail")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals("strip of a", repo.files.strip("a").readText())
        assertTrue(repo.files.editDirs("a").isEmpty())
        val row = checkNotNull(repo.session("a"))
        assertEquals("classic", row.layoutId)
        assertEquals(1_000L, row.updatedAt)
        assertEquals(listOf(0, 1, 2, 3, null, null), repo.shots("a").map { it.slotIndex })
    }

    @Test
    fun anEditThatLandedBeforeACrashIsFinishedAndOneThatDidntIsDropped() = runTest {
        saved("landed", 1_000)
        saved("lost", 2_000)
        // "landed": the rows carry stamp 7000, the new files are still in the staging folder.
        db.sessions().applyEdit(checkNotNull(repo.session("landed")).copy(layoutId = "trio", updatedAt = 7_000), mapOf(0 to null))
        repo.files.editDir("landed", 7_000).apply {
            mkdirs()
            File(this, SessionFileStore.StripName).writeText("edited strip")
            File(this, SessionFileStore.ThumbName).writeText("edited thumb")
        }
        // "lost": a staging folder from an edit whose rows never changed.
        repo.files.editDir("lost", 9_000).apply {
            mkdirs()
            File(this, SessionFileStore.StripName).writeText("half")
        }

        assertEquals(1, repo.finishEdits())
        assertEquals("edited strip", repo.files.strip("landed").readText())
        assertEquals("edited thumb", repo.files.thumb("landed").readText())
        assertEquals("strip of lost", repo.files.strip("lost").readText())
        assertTrue(repo.files.editDirs("landed").isEmpty())
        assertTrue(repo.files.editDirs("lost").isEmpty())
    }

    @Test
    fun aLostThumbnailIsMadeAgainAndTheAlbumShowsTheStripMeanwhile() = runTest {
        saved("a", 1_000)
        saved("b", 2_000)
        repo.files.thumb("a").delete()
        assertEquals(repo.files.strip("a"), repo.files.thumbOrStrip("a"))
        assertEquals(repo.files.thumb("b"), repo.files.thumbOrStrip("b"))

        val made = mutableListOf<String>()
        assertEquals(1, repo.repairThumbs { strip, thumb -> made += strip.parentFile!!.name; thumb.writeText("fresh") })
        assertEquals(listOf("a"), made)
        assertEquals("fresh", repo.files.thumb("a").readText())

        repo.files.strip("b").delete()
        assertEquals("Detail falls back to the thumbnail", repo.files.thumb("b"), repo.files.stripOrThumb("b"))
    }

    @Test
    fun startupRunsEveryCheck() = runTest {
        saved("gone", 1_000)
        repo.softDelete(listOf("gone"))
        now += SessionRepository.PurgeAfterMillis + 1
        saved("thumbless", 2_000)
        repo.files.thumb("thumbless").delete()

        repo.startup { _, thumb -> thumb.writeText("fresh") }
        assertNull(repo.session("gone"))
        assertTrue(repo.files.thumb("thumbless").isFile)
    }
}
