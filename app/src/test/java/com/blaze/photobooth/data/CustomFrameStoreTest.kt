package com.blaze.photobooth.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.db.FrameEntity
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.frames.CustomFrameStore
import com.blaze.photobooth.data.frames.FrameDeletion
import com.blaze.photobooth.data.frames.FrameVisibility
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.SlotRect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * README §11.1–§11.2: imported frames in the `frames` table and `files/frames/{uuid}/`, renamed, reordered and hidden;
 * deleting a frame that strips use hides it instead, and startup cleanup removes it once nothing does. In-memory Room
 * and a temp folder.
 */
@RunWith(AndroidJUnit4::class)
class CustomFrameStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BoothDatabase
    private lateinit var dir: File
    private lateinit var store: CustomFrameStore
    private var draftFrame: String? = null
    private var clock = 1_000L

    private val slots = listOf(SlotRect(72f, 72f, 1056f, 760f), SlotRect(72.4f, 880f, 1055.6f, 760f))

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoothDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dir = tmp.newFolder("frames")
        store = CustomFrameStore(
            dao = db.frames(),
            dir = dir,
            framesInUse = { db.sessions().frameIdsInUse().toSet() + listOfNotNull(draftFrame) },
            sessionsUsing = { db.sessions().countUsing(it) },
            now = { clock++ },
        )
    }

    @After
    fun close() = db.close()

    private suspend fun add(name: String): FrameId = store.add(name, 1200, 3600, slots) { art, thumb ->
        art.writeText("frame")
        thumb.writeText("thumb")
    }

    private suspend fun useIn(sessionId: String, frame: FrameId, deleted: Boolean = false) {
        val row = SessionEntity(sessionId, clock, clock, SessionEntity.ImageLayoutId, frame.value, "original", null, true)
        db.sessions().insert(row, emptyList())
        if (deleted) db.sessions().softDelete(sessionId, clock)
    }

    private suspend fun visibility(id: FrameId) = store.frames.first().firstOrNull { it.frame.id == id }?.visibility

    @Test
    fun addSavesTheFilesAndARowLastInHerOrder() = runTest {
        val a = add("Frame 1")
        val b = add("Frame 2")
        assertEquals(FrameId.Kind.Custom, a.kind)
        val frames = store.frames.first()
        assertEquals(listOf(a, b), frames.map { it.frame.id })
        val first = frames.first().frame
        assertEquals("Frame 1", first.name)
        assertEquals(1200 to 3600, first.width to first.height)
        // Slots round-trip through slots_json as whole pixels.
        assertEquals(listOf(SlotRect(72f, 72f, 1056f, 760f), SlotRect(72f, 880f, 1056f, 760f)), first.slots)
        // The folder is the uuid; nothing is left in staging.
        assertTrue(store.artworkFile(a.localId).isFile)
        assertTrue(store.thumbFile(a.localId).isFile)
        assertEquals(setOf(a.localId, b.localId), dir.list()!!.toSet())
        assertEquals(listOf(0, 1), db.frames().all().map { it.sortOrder })
    }

    @Test
    fun aFailedSaveLeavesNothingBehind() = runTest {
        try {
            store.add("Broken", 100, 100, slots) { art, _ ->
                art.writeText("half")
                error("disk full")
            }
            fail("expected the error")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertTrue(db.frames().all().isEmpty())
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun renameReorderHideAndShow() = runTest {
        val a = add("Frame 1")
        val b = add("Frame 2")
        val c = add("Frame 3")
        store.rename(a, "  Our first date ♡ with a very long name  ")
        assertEquals("Our first date ♡ with a", store.frames.first().first().frame.name)
        store.rename(b, "   ")
        assertEquals("a blank name is ignored", "Frame 2", store.frames.first()[1].frame.name)

        store.move(c, -1)
        assertEquals(listOf(a, c, b), store.frames.first().map { it.frame.id })
        store.move(a, -1)
        assertEquals("already first", listOf(a, c, b), store.frames.first().map { it.frame.id })
        store.move(a, +1)
        assertEquals(listOf(c, a, b), store.frames.first().map { it.frame.id })

        store.setHidden(b, true)
        assertEquals(FrameVisibility.Hidden, visibility(b))
        assertEquals(FrameEntity.HiddenByHer, db.frames().frame(b.localId)!!.hidden)
        store.setHidden(b, false)
        assertEquals(FrameVisibility.Shown, visibility(b))
    }

    @Test
    fun deletingAnUnusedFrameRemovesItAndItsFiles() = runTest {
        val a = add("Frame 1")
        assertEquals(FrameDeletion.Deleted, store.delete(a))
        assertNull(db.frames().frame(a.localId))
        assertFalse(store.folder(a.localId).exists())
        assertEquals(FrameDeletion.NotFound, store.delete(a))
    }

    @Test
    fun deletingAFrameThatStripsUseOnlyHidesIt() = runTest {
        val a = add("Frame 1")
        useIn("s1", a)
        useIn("s2", a, deleted = true) // in the bin, but Undo could bring it back
        assertEquals(FrameDeletion.KeptHidden(2), store.delete(a))
        assertEquals(FrameVisibility.Deleted, visibility(a))
        assertTrue("files stay for Edit", store.artworkFile(a.localId).isFile)
        // Show again doesn't bring a deleted frame back.
        store.setHidden(a, false)
        assertEquals(FrameVisibility.Deleted, visibility(a))
        // Deleted frames aren't moved around either (they're not listed).
        val b = add("Frame 2")
        store.move(b, -1)
        assertEquals(1, db.frames().frame(b.localId)!!.sortOrder)
    }

    @Test
    fun theDraftCountsAsAUse() = runTest {
        val a = add("Frame 1")
        draftFrame = a.value
        assertEquals(FrameDeletion.KeptHidden(0), store.delete(a))
        assertTrue(store.artworkFile(a.localId).isFile)
    }

    @Test
    fun startupCleanupRemovesDeletedFramesOnceNothingUsesThem() = runTest {
        val used = add("Used")
        val hidden = add("Hidden")
        val drafted = add("In the draft")
        useIn("s1", used)
        store.delete(used)
        store.setHidden(hidden, true)
        draftFrame = drafted.value
        store.delete(drafted)
        // A folder without a row (an import that died) and a staging folder.
        File(dir, "0000-stray").mkdirs()
        File(dir, "1111${CustomFrameStore.StagingSuffix}").mkdirs()

        store.cleanup()
        assertNotNull("still used: kept", db.frames().frame(used.localId))
        assertNotNull("hidden by her: kept", db.frames().frame(hidden.localId))
        assertNotNull("the draft uses it: kept", db.frames().frame(drafted.localId))
        assertEquals(setOf(used.localId, hidden.localId, drafted.localId), dir.list()!!.toSet())

        // The strip is purged and the draft is printed with another frame: next start, both go.
        db.sessions().delete("s1")
        draftFrame = null
        store.cleanup()
        assertNull(db.frames().frame(used.localId))
        assertNull(db.frames().frame(drafted.localId))
        assertFalse(store.folder(used.localId).exists())
        assertEquals(setOf(hidden.localId), dir.list()!!.toSet())
        assertEquals(listOf(hidden), store.frames.first().map { it.frame.id })
    }

    @Test
    fun defaultNamesTakeTheFirstFreeNumber() = runTest {
        val pattern = { n: Int -> "Frame $n" }
        assertEquals("Frame 1", CustomFrameStore.defaultName(emptyList(), pattern))
        add("Frame 1")
        add("Frame 3")
        assertEquals("Frame 2", CustomFrameStore.defaultName(store.names(), pattern))
    }
}
