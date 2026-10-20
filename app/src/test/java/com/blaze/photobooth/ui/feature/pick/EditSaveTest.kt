package com.blaze.photobooth.ui.feature.pick

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.BoothDatabase
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.DraftShot
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.StripExporter
import com.blaze.photobooth.render.StripRenderer
import com.blaze.photobooth.screenshots.TestArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * README §4.7 Edit: Pick backed by a saved session starts from its rows, keeps changes in memory, and Save
 * re-renders `strip.jpg` and `thumb.jpg` at full size and updates the rows. The draft is never touched.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditSaveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: BoothDatabase
    private lateinit var draftStore: BoothDraftStore
    private lateinit var repo: SessionRepository
    private lateinit var source: SavedPickSource

    /** 20 October 2026, 10:00 UTC. */
    private val created = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant().toEpochMilli()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, BoothDatabase::class.java).allowMainThreadQueries().build()
        val sessionsDir = tmp.newFolder("sessions")
        draftStore = BoothDraftStore(sessionsDir, io = Dispatchers.Unconfined)
        repo = SessionRepository(db.sessions(), SessionFileStore(sessionsDir), draftStore, io = Dispatchers.Unconfined, clock = { created + 60_000 })
        val exporter = StripExporter(StripRenderer.get(context), compute = Dispatchers.Unconfined, io = Dispatchers.Unconfined)
        source = SavedPickSource("s", repo, exporter, zone = { ZoneOffset.UTC }, io = Dispatchers.Unconfined)

        // A saved Strawberry classic session: 6 shots, 1–4 in the strip, two unused bonus shots.
        val dir = repo.files.dir("s").apply { mkdirs() }
        repeat(6) { i -> jpeg(File(dir, SessionFileStore.shotName(i)), TestArt.photo(i + 1, 1200, 840)) }
        repo.files.strip("s").writeText("old strip")
        repo.files.thumb("s").writeText("old thumb")
        db.sessions().insert(
            SessionEntity("s", created, created, "classic", "theme:strawberry", "milky", caption = "best day", showDate = true),
            List(6) { ShotEntity("s", it, slotIndex = it.takeIf { i -> i < 4 }, width = 1200, height = 840) },
        )

        // And a booth session in progress, which Edit must not touch.
        draftStore.begin(BoothConfig(frameId = FrameId.asset("lilac-pals")), totalShots = 3)
        draftStore.shotFile(0).writeText("draft shot")
        draftStore.addShot(DraftShot(0, 10, 10))
        draftStore.updatePick(listOf(0, null, null), "draft caption", showDate = false)
    }

    @After
    fun close() = db.close()

    private fun jpeg(file: File, bitmap: Bitmap) = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }

    private fun draftBytes() = File(draftStore.dir, BoothDraftStore.DraftFileName).readBytes()

    @Test
    fun editStartsFromTheSavedRows() = runTest {
        val session = checkNotNull(source.session.first())
        assertEquals(BoothLayout.Classic, session.config.layout)
        assertEquals(FrameId.theme(FrameTheme.Strawberry), session.config.frameId)
        assertEquals(PhotoFilter.Milky, session.config.filter)
        assertEquals(listOf(0, 1, 2, 3), session.selection)
        assertEquals((0 until 6).toList(), session.shots.map { it.index })
        assertEquals(6, session.totalShots)
        assertEquals("best day", session.caption)
        assertEquals("the strip keeps the day it was made", LocalDate.of(2026, 10, 20), session.stampDate)
    }

    @Test
    fun savingReRendersTheStripAndUpdatesTheRowsWithoutTouchingTheDraft() = runTest {
        val draftBefore = draftBytes()
        val config = BoothConfig(layout = BoothLayout.Trio, frameId = FrameId.theme(FrameTheme.Cloud), filter = PhotoFilter.Mono)

        // Changing things in Pick writes nothing yet (Cancel = no changes).
        source.savePick(listOf(5, 0, 2), "new", showDate = false)
        source.saveConfig(config)
        assertEquals("old strip", repo.files.strip("s").readText())
        assertEquals("classic", repo.session("s")?.layoutId)

        val spec = StripSpec(FrameSource.Theme(FrameTheme.Cloud), BoothLayout.Trio, PhotoFilter.Mono, caption = "new", date = LocalDate.of(2026, 10, 20))
        val id = source.commit(spec, PickResult(config, listOf(5, 0, 2), "new", showDate = false))

        assertEquals("s", id)
        val strip = BitmapFactory.decodeFile(repo.files.strip("s").path)
        assertEquals("re-rendered at the trio's export size", 1200, strip.width)
        assertEquals(3600, strip.height)
        assertEquals(600, BitmapFactory.decodeFile(repo.files.thumb("s").path).width)
        assertNotEquals("old thumb", repo.files.thumb("s").readBytes().decodeToString())

        val row = checkNotNull(repo.session("s"))
        assertEquals(listOf("trio", "theme:cloud", "mono", "new"), listOf(row.layoutId, row.frameId, row.filterId, row.caption))
        assertEquals(false, row.showDate)
        assertEquals(created, row.createdAt)
        assertEquals(created + 60_000, row.updatedAt)
        assertEquals(listOf(1, null, 2, null, null, 0), repo.shots("s").map { it.slotIndex })

        assertArrayEquals("the draft is untouched", draftBefore, draftBytes())
        assertEquals(listOf(0, null, null), draftStore.current()?.selection)
        assertEquals("draft shot", draftStore.shotFile(0).readText())
    }

    @Test
    fun aSessionWithAMissingShotFileOffersTheRest() = runTest {
        repo.files.shot("s", 4).delete()
        assertEquals(listOf(0, 1, 2, 3, 5), source.session.first()?.shots?.map { it.index })
    }

    @Test
    fun aDeletedSessionCantBeEdited() = runTest {
        repo.softDelete(listOf("s"))
        assertEquals(null, source.session.first())
    }

    @Test
    fun aGoneFrameFallsBackToADefaultThemeThatFits() {
        val her = BoothConfig(frameId = FrameId.custom("deleted"))
        assertEquals(BoothLayout.Trio, PickViewModel.fallbackConfig(her, shotCount = 5, picked = 3).layout)
        assertEquals(FrameId.theme(FrameTheme.Default), PickViewModel.fallbackConfig(her, 5, 3).frameId)
        assertEquals(BoothLayout.Classic, PickViewModel.fallbackConfig(her, shotCount = 5, picked = 5).layout)
        assertEquals(BoothLayout.Six, PickViewModel.fallbackConfig(her, shotCount = 7, picked = 0).layout)
        assertEquals(BoothLayout.Solo, PickViewModel.fallbackConfig(her, shotCount = 1, picked = 1).layout)
    }
}
