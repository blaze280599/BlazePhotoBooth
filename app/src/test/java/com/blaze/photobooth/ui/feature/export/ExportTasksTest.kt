package com.blaze.photobooth.ui.feature.export

import android.content.Intent
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.export.ExportError
import com.blaze.photobooth.data.export.ExportItem
import com.blaze.photobooth.data.export.ExportJob
import com.blaze.photobooth.data.export.ExportOutcome
import com.blaze.photobooth.data.export.ExportService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §7 A12 / §13: one export at a time (a second tap is ignored), progress while a batch runs, a ✓ when done, and Retry
 * that carries on from the file that failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportTasksTest {

    private val row = SessionEntity("s1", 1L, 1L, "classic", "theme:milk", "original", null, true)
    private fun jobs(n: Int) = (0 until n).map { ExportJob(row, ExportItem.Photo(it, ExportItem.Photo.Kind.Cut, it + 1)) }

    private class FakeService : ExportService {
        val calls = mutableListOf<List<ExportJob>>()
        var gate: CompletableDeferred<Unit>? = null
        var answer: (List<ExportJob>) -> ExportOutcome = { jobs -> ExportOutcome(jobs.map { "f${(it.item as ExportItem.Photo).number}" }) }

        override suspend fun save(jobs: List<ExportJob>, onProgress: (done: Int, total: Int) -> Unit): ExportOutcome {
            calls += jobs
            onProgress(1, jobs.size)
            gate?.await()
            return answer(jobs)
        }

        override suspend fun shareIntent(jobs: List<ExportJob>): Intent = error("not used")
    }

    @Test
    fun aSecondTapWhileSavingIsIgnoredAndProgressShows() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val service = FakeService().apply { gate = CompletableDeferred() }
        val tasks = ExportTasks(scope, service)
        val messages = mutableListOf<ExportMessage>()
        scope.backgroundScope.launchCollect(tasks, messages)

        tasks.save(ExportKey.AllPhotos, SavedWhat.Photos, jobs(4))
        tasks.save(ExportKey.AllPhotos, SavedWhat.Photos, jobs(4))
        tasks.save(ExportKey.Strip, SavedWhat.Strip, jobs(1))

        assertEquals(1, service.calls.size)
        assertTrue(tasks.actions.value.busy)
        assertEquals(ExportActionState(ExportPhase.Busy, 1, 4), tasks.actions.value[ExportKey.AllPhotos])

        service.gate?.complete(Unit)
        scope.advanceUntilIdle()

        assertEquals(ExportPhase.Done, tasks.actions.value[ExportKey.AllPhotos].phase)
        assertEquals(false, tasks.actions.value.busy)
        assertEquals(listOf<ExportMessage>(ExportMessage.Saved(SavedWhat.Photos, 4)), messages)
    }

    @Test
    fun retryCarriesOnFromTheFileThatFailed() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val service = FakeService().apply {
            answer = { jobs -> ExportOutcome(listOf("f1"), error = ExportError.Failed, stoppedAt = 1).takeIf { jobs.size == 3 } ?: ExportOutcome(listOf("f2", "f3")) }
        }
        val tasks = ExportTasks(scope, service)
        val messages = mutableListOf<ExportMessage>()
        scope.backgroundScope.launchCollect(tasks, messages)

        tasks.save(ExportKey.AllPhotos, SavedWhat.Photos, jobs(3))
        scope.advanceUntilIdle()
        val failed = messages.single() as ExportMessage.Failed
        assertEquals(1, failed.saved)
        assertEquals(ExportPhase.Idle, tasks.actions.value[ExportKey.AllPhotos].phase)

        assertNotNull(failed.retry)
        failed.retry?.invoke()
        scope.advanceUntilIdle()

        assertEquals(jobs(3).drop(1), service.calls.last())
        // The snackbar after Retry counts every file that made it.
        assertEquals(ExportMessage.Saved(SavedWhat.Photos, 3), messages.last())
    }

    @Test
    fun missingFilesGetAFriendlyErrorWithoutRetry() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val service = FakeService().apply { answer = { ExportOutcome(emptyList(), missing = 1) } }
        val tasks = ExportTasks(scope, service)
        val messages = mutableListOf<ExportMessage>()
        scope.backgroundScope.launchCollect(tasks, messages)

        tasks.save(ExportKey.Strip, SavedWhat.Strip, jobs(1))
        scope.advanceUntilIdle()

        val failed = messages.single() as ExportMessage.Failed
        assertEquals(ExportError.MissingFile, failed.error)
        assertNull(failed.retry)
    }

    @Test
    fun aDoneButtonCanBeReset() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val tasks = ExportTasks(scope, FakeService())
        tasks.markDone(ExportKey.Strip)
        assertEquals(ExportPhase.Done, tasks.actions.value[ExportKey.Strip].phase)
        tasks.resetDone()
        assertEquals(ExportPhase.Idle, tasks.actions.value[ExportKey.Strip].phase)
    }

    private fun CoroutineScope.launchCollect(tasks: ExportTasks, into: MutableList<ExportMessage>) =
        launch { tasks.messages.collect { into += it } }
}
