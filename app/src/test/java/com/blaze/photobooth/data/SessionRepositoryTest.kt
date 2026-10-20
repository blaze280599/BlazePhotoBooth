package com.blaze.photobooth.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.DraftShot
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
 * README §11.1: Print atomically renames `_draft` to `sessions/{uuid}` and inserts the rows; if the insert fails,
 * the rename is undone and the draft is untouched. Every shot is kept, bonus shots included.
 */
@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BoothDatabase
    private lateinit var sessionsDir: File
    private lateinit var draftStore: BoothDraftStore
    private lateinit var repo: SessionRepository
    private var nextId = "session-1"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoothDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionsDir = tmp.newFolder("sessions")
        draftStore = BoothDraftStore(sessionsDir, io = Dispatchers.Unconfined, clock = { 42L })
        repo = SessionRepository(
            db.sessions(),
            SessionFileStore(sessionsDir),
            draftStore,
            io = Dispatchers.Unconfined,
            clock = { 1_000L },
            newId = { nextId },
        )
    }

    @After
    fun close() = db.close()

    private val config = BoothConfig(
        layout = BoothLayout.Classic,
        frameId = FrameId.theme(FrameTheme.Cherry),
        filter = PhotoFilter.Film,
    )

    /** A finished classic session: 6 shots on disk, shots 5, 1, 2, 3 picked, caption and date set. */
    private suspend fun finishedDraft() {
        draftStore.begin(config, totalShots = 6)
        for (i in 0 until 6) {
            draftStore.shotFile(i).apply { parentFile?.mkdirs(); writeText("jpeg $i") }
            draftStore.addShot(DraftShot(i, 2400, 1680))
        }
        draftStore.updatePick(selection = listOf(5, 1, 2, 3), caption = "  우리 둘 ♡ ", showDate = true)
    }

    private val fakeRender: suspend (com.blaze.photobooth.data.files.BoothDraft, File) -> Unit = { _, dir ->
        File(dir, SessionFileStore.StripName).writeText("strip")
        File(dir, SessionFileStore.ThumbName).writeText("thumb")
    }

    @Test
    fun printMovesTheDraftIntoTheAlbumWithEveryShot() = runTest {
        finishedDraft()
        val id = repo.finalizeDraft(fakeRender)

        assertEquals("session-1", id)
        val dir = repo.files.dir(id)
        assertFalse(draftStore.dir.exists())
        assertNull(draftStore.current())
        for (i in 0 until 6) assertTrue("shot $i", repo.files.shot(id, i).isFile)
        assertEquals("strip", repo.files.strip(id).readText())
        assertTrue(repo.files.thumb(id).isFile)
        assertFalse("draft.json is gone", File(dir, BoothDraftStore.DraftFileName).exists())

        val session = repo.session(id)
        assertEquals(
            SessionEntity(
                id = id, createdAt = 1_000, updatedAt = 1_000, layoutId = "classic", frameId = "theme:cherry",
                filterId = "film", caption = "우리 둘 ♡", showDate = true,
            ),
            session,
        )
        assertEquals(listOf(null, 1, 2, 3, null, 0), repo.shots(id).map { it.slotIndex })
        assertEquals(1, repo.count.first())
    }

    @Test
    fun imageFramesAreStoredWithTheImageLayout() = runTest {
        draftStore.begin(config.copy(frameId = FrameId.asset("lilac-pals")), totalShots = 3)
        draftStore.updatePick(listOf(0, 1, 2), null, showDate = false)
        val id = repo.finalizeDraft(fakeRender)
        assertEquals(SessionEntity.ImageLayoutId, repo.session(id)?.layoutId)
        assertEquals("asset:lilac-pals", repo.session(id)?.frameId)
    }

    @Test
    fun aFailedInsertPutsTheDraftBack() = runTest {
        finishedDraft()
        // A row with the same id already exists, so the insert fails after the rename.
        db.sessions().insert(SessionRepository.rowsFor(checkNotNull(draftStore.current()), "session-1", 1L).first, emptyList())
        try {
            repo.finalizeDraft(fakeRender)
            fail("Expected the insert to fail")
        } catch (e: Exception) {
            // expected
        }
        assertFalse(repo.files.dir("session-1").exists())
        assertTrue(draftStore.dir.isDirectory)
        for (i in 0 until 6) assertTrue("shot $i is back", draftStore.shotFile(i).isFile)
        assertEquals(listOf(5, 1, 2, 3), draftStore.current()?.selection)
        assertEquals(6, BoothDraftStore(sessionsDir).current()?.shots?.size)

        // And printing again (with a fresh id) works.
        nextId = "session-2"
        repo.finalizeDraft(fakeRender)
        assertEquals(6, repo.shots("session-2").size)
    }

    @Test
    fun aFailedRenderLeavesTheDraftAlone() = runTest {
        finishedDraft()
        try {
            repo.finalizeDraft { _, _ -> error("out of memory") }
            fail("Expected the render to fail")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertTrue(draftStore.dir.isDirectory)
        assertEquals(6, draftStore.current()?.shots?.size)
        assertTrue(db.sessions().allIds().isEmpty())
    }

    @Test
    fun aSessionFolderWithoutARowIsRecovered() = runTest {
        finishedDraft()
        // As if the app died right after the rename: the folder (with its draft.json) but no row.
        draftStore.dir.resolve(SessionFileStore.StripName).writeText("strip")
        val orphan = File(sessionsDir, "orphan")
        assertTrue(draftStore.dir.renameTo(orphan))
        File(sessionsDir, "stray").mkdirs() // no draft.json: not ours to touch

        assertEquals(1, repo.recoverOrphans())
        assertEquals(6, repo.shots("orphan").size)
        assertEquals("우리 둘 ♡", repo.session("orphan")?.caption)
        assertFalse(File(orphan, BoothDraftStore.DraftFileName).exists())
        assertEquals(0, repo.recoverOrphans())
        assertTrue(File(sessionsDir, "stray").isDirectory)
    }
}
