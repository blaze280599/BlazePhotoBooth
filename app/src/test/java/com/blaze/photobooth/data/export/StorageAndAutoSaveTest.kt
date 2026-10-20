package com.blaze.photobooth.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.prefs.BoothSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.Locale

/** README §4.8: *Storage used* adds up the album's files; *Auto-save after printing* saves the strip only when on. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StorageAndAutoSaveTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun storageCountsEveryFileUnderSessions() {
        val dir = temp.newFolder("sessions")
        File(dir, "a").mkdirs()
        File(dir, "a/strip.jpg").writeBytes(ByteArray(1_500))
        File(dir, "a/shot_0.jpg").writeBytes(ByteArray(500))
        File(dir, "_draft").mkdirs()
        File(dir, "_draft/shot_0.jpg").writeBytes(ByteArray(250))
        assertEquals(2_250, StorageStats.bytesIn(dir))
        assertEquals(0, StorageStats.bytesIn(File(temp.root, "nothing")))
    }

    @Test
    fun megabytesReadLikeAndroidsSettings() {
        val en = Locale.US
        assertEquals("0", StorageStats.megabytes(0, en))
        assertEquals("0.1", StorageStats.megabytes(2_000, en))
        assertEquals("4.3", StorageStats.megabytes(4_260_000, en))
        assertEquals("310", StorageStats.megabytes(310_400_000, en))
        assertEquals("1,250", StorageStats.megabytes(1_250_000_000, en))
        assertEquals("4,3", StorageStats.megabytes(4_260_000, Locale.GERMANY))
    }

    private val row = SessionEntity("s1", 1L, 1L, "classic", "theme:milk", "original", null, true)

    private fun saver(scope: TestScope, on: Boolean, outcome: () -> ExportOutcome, saved: MutableList<List<ExportJob>>) =
        StripAutoSaver(
            settings = flowOf(BoothSettings(autoSaveStrip = on)),
            session = { id -> row.takeIf { it.id == id } },
            save = { jobs -> saved += jobs; outcome() },
            scope = scope,
        )

    @Test
    fun autoSaveIsOffByDefault() {
        assertEquals(false, BoothSettings().autoSaveStrip)
    }

    @Test
    fun printWithAutoSaveOnSavesTheStrip() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val saved = mutableListOf<List<ExportJob>>()
        val auto = saver(scope, on = true, outcome = { ExportOutcome(listOf("BlazeBooth_x_strip.jpg")) }, saved)

        auto.onPrinted("s1")
        assertEquals(AutoSaveStatus.Saving, auto.status.value["s1"])
        scope.advanceUntilIdle()

        assertEquals(listOf(listOf(ExportJob(row, ExportItem.Strip))), saved)
        assertEquals(AutoSaveStatus.Saved, auto.status.value["s1"])
    }

    @Test
    fun printWithAutoSaveOffSavesNothing() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val saved = mutableListOf<List<ExportJob>>()
        val auto = saver(scope, on = false, outcome = { error("not called") }, saved)

        auto.onPrinted("s1")
        scope.advanceUntilIdle()

        assertTrue(saved.isEmpty())
        assertEquals(null, auto.status.value["s1"])
    }

    @Test
    fun aFailedAutoSaveIsReported() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val auto = saver(scope, on = true, outcome = { throw IOException("full") }, mutableListOf())

        auto.onPrinted("s1")
        scope.advanceUntilIdle()

        assertEquals(AutoSaveStatus.Failed, auto.status.value["s1"])
    }
}
