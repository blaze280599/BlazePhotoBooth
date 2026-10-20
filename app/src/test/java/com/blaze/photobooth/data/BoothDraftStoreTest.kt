package com.blaze.photobooth.data

import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.DraftConfig
import com.blaze.photobooth.data.files.DraftShot
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** README §11.1 / §11.4: the draft survives a process death, shot by shot, and Discard removes it all. */
class BoothDraftStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val config = BoothConfig(
        layout = BoothLayout.Grid,
        frameId = FrameId.asset("lilac-pals"),
        filter = PhotoFilter.Milky,
        timerSec = 3,
        bonusShots = 2,
        lens = Lens.Front,
        mirror = true,
    )

    private fun store(scope: kotlinx.coroutines.test.TestScope) =
        BoothDraftStore(tmp.root, io = StandardTestDispatcher(scope.testScheduler), clock = { Now })

    private companion object {
        const val Now = 1_760_000_000_000L
    }

    private fun BoothDraftStore.fakeShot(index: Int) = shotFile(index).apply { parentFile?.mkdirs(); writeText("jpeg $index") }

    @Test
    fun jsonRoundTripsEveryField() {
        val draft = BoothDraft(
            createdAt = 42L,
            config = DraftConfig.from(config),
            totalShots = 5,
            shots = listOf(DraftShot(0, 2400, 1938), DraftShot(1, 2400, 1940)),
            selection = listOf(1, null, 0),
            caption = "오늘 ♡ today",
        )
        val text = BoothDraftStore.encode(draft)
        assertEquals(draft, BoothDraftStore.decode(text))
        assertEquals(config, BoothDraftStore.decode(text)?.config?.toConfig())
        assertTrue(text.contains("\"frame\":\"asset:lilac-pals\""))
    }

    @Test
    fun garbageIsNotADraft() {
        assertNull(BoothDraftStore.decode("{not json"))
        assertNull(BoothDraftStore.decode("{\"hello\":1}"))
    }

    @Test
    fun unknownIdsFallBackToDefaultsFieldByField() {
        val odd = DraftConfig(layout = "zigzag", frame = "nope", filter = "sepia", timerSec = 7, bonusShots = 3, lens = "side", mirror = false)
        val d = BoothConfig()
        assertEquals(d.copy(mirror = false), odd.toConfig())
    }

    @Test
    fun noDraftAtFirst() = runTest {
        assertNull(store(this).current())
        assertNull(store(this).draft.first())
    }

    @Test
    fun beginThenShotsSurviveARestart() = runTest {
        val first = store(this)
        first.begin(config, totalShots = 5)
        first.fakeShot(0)
        first.addShot(DraftShot(0, 2400, 1938))
        first.fakeShot(1)
        val after = first.addShot(DraftShot(1, 2400, 1938))
        assertEquals(2, after.taken)
        assertEquals(2, after.nextIndex)
        assertFalse(after.complete)

        val reopened = store(this).current()
        assertEquals(after, reopened)
        assertEquals(config, reopened?.config?.toConfig())
    }

    @Test
    fun aShotWhoseFileIsMissingIsDroppedOnLoad() = runTest {
        val s = store(this)
        s.begin(config, totalShots = 3)
        s.fakeShot(0)
        s.addShot(DraftShot(0, 10, 10))
        s.addShot(DraftShot(1, 10, 10)) // no file: as if the app died mid-write
        assertEquals(listOf(0), store(this).current()?.shots?.map { it.index })
    }

    @Test
    fun retakeReplacesTheSameIndex() = runTest {
        val s = store(this)
        s.begin(config, totalShots = 2)
        s.fakeShot(0)
        s.addShot(DraftShot(0, 10, 10))
        val d = s.addShot(DraftShot(0, 20, 20))
        assertEquals(listOf(DraftShot(0, 20, 20, takenAt = Now)), d.shots)
    }

    @Test
    fun pickChoicesSurviveARestart() = runTest {
        val s = store(this)
        s.begin(config, totalShots = 5)
        s.updatePick(selection = listOf(4, null, 0), caption = "우리 ♡", showDate = false)
        val reopened = store(this).current()
        assertEquals(listOf(4, null, 0), reopened?.selection)
        assertEquals("우리 ♡", reopened?.caption)
        assertEquals(false, reopened?.showDate)
    }

    @Test
    fun handOffClearsTheDraftOnlyWhenItSucceeds() = runTest {
        val s = store(this)
        s.begin(config, totalShots = 1)
        try {
            s.handOff<Unit> { _, _ -> error("insert failed") }
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(1, s.current()?.totalShots)
        val moved = s.handOff { draft, dir ->
            assertEquals(s.dir, dir)
            dir.deleteRecursively()
            draft.totalShots
        }
        assertEquals(1, moved)
        assertNull(s.current())
    }

    @Test
    fun completeWhenEveryShotIsTaken() = runTest {
        val s = store(this)
        s.begin(config, totalShots = 2)
        s.fakeShot(0)
        s.fakeShot(1)
        s.addShot(DraftShot(1, 10, 10))
        val d = s.addShot(DraftShot(0, 10, 10))
        assertTrue(d.complete)
        assertNull(d.nextIndex)
        assertEquals(listOf(0, 1), d.shots.map { it.index })
    }

    @Test
    fun beginWipesTheOldDraftAndDiscardDeletesTheFolder() = runTest {
        val s = store(this)
        s.begin(config, totalShots = 2)
        val old = s.fakeShot(0)
        s.addShot(DraftShot(0, 10, 10))
        val fresh = s.begin(config.copy(timerSec = 10), totalShots = 6)
        assertFalse(old.exists())
        assertEquals(0, fresh.taken)
        assertEquals(6, store(this).current()?.totalShots)

        s.discard()
        assertFalse(s.dir.exists())
        assertNull(s.current())
        assertNull(store(this).current())
    }

    @Test
    fun theFlowFollowsChanges() = runTest {
        val s = store(this)
        assertNull(s.draft.first())
        s.begin(config, totalShots = 2)
        assertEquals(0, s.draft.first()?.taken)
        s.updateConfig(config.copy(lens = Lens.Back))
        assertEquals("back", s.draft.first()?.config?.lens)
    }
}
