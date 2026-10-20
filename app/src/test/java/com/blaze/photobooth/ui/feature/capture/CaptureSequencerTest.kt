package com.blaze.photobooth.ui.feature.capture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §4.3 / §13: countdown → capture → 1.2 s beat → next, with pause, resume and backgrounding. Virtual time. */
class CaptureSequencerTest {

    /** The sequence runs in backgroundScope, which advanceUntilIdle ignores; jump far ahead instead. */
    private fun TestScope.finishAll() {
        advanceTimeBy(10 * 60_000L)
        runCurrent()
    }

    private class Harness(scope: TestScope, timer: Int = 3, total: Int = 3, taken: Int = 0, captureMillis: Long = 0) {
        val captured = mutableListOf<Int>()
        val ticks = mutableListOf<Int>()
        var fail = false
        val sequencer = CaptureSequencer(
            scope = scope.backgroundScope,
            timerSec = timer,
            total = total,
            taken = taken,
            capture = { index ->
                if (captureMillis > 0) kotlinx.coroutines.delay(captureMillis)
                if (fail) false else true.also { captured += index }
            },
            onTick = { ticks += it },
        )
        val phase get() = sequencer.states.value.phase
        val taken get() = sequencer.states.value.taken
    }

    @Test
    fun startsIdleAndRunsTheWholeSequence() = runTest {
        val h = Harness(this, timer = 3, total = 3)
        assertEquals(SequencePhase.Idle, h.phase)
        h.sequencer.start()
        runCurrent()
        assertEquals(SequencePhase.Countdown(3), h.phase)
        assertEquals(listOf(3), h.ticks)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(SequencePhase.Countdown(2), h.phase)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SequencePhase.Countdown(1), h.phase)
        advanceTimeBy(1_000); runCurrent()
        // Capture is instant here, so we're already in the beat after shot 0.
        assertEquals(listOf(0), h.captured)
        assertEquals(SequencePhase.Beat, h.phase)
        assertEquals(1, h.taken)

        advanceTimeBy(1_199); runCurrent()
        assertEquals(SequencePhase.Beat, h.phase)
        advanceTimeBy(1); runCurrent()
        assertEquals(SequencePhase.Countdown(3), h.phase)

        finishAll()
        assertEquals(SequencePhase.Done, h.phase)
        assertEquals(listOf(0, 1, 2), h.captured)
        assertEquals(listOf(3, 2, 1, 3, 2, 1, 3, 2, 1), h.ticks)
    }

    @Test
    fun totalTimeIsCountdownsPlusBeatsPlusTheFinish() = runTest {
        val h = Harness(this, timer = 5, total = 6)
        h.sequencer.start()
        // 6 × 5 s countdowns + 5 × 1.2 s beats + 0.9 s before Pick.
        val total = 6 * 5_000L + 5 * 1_200L + 900L
        advanceTimeBy(total - 1); runCurrent()
        assertEquals(SequencePhase.Beat, h.phase)
        assertEquals(6, h.taken)
        advanceTimeBy(1); runCurrent()
        assertEquals(SequencePhase.Done, h.phase)
    }

    @Test
    fun pauseMidCountdownThenResumeStartsAFreshCountdown() = runTest {
        val h = Harness(this, timer = 5, total = 2)
        h.sequencer.start()
        advanceTimeBy(2_500); runCurrent()
        assertEquals(SequencePhase.Countdown(3), h.phase)
        h.sequencer.toggle() // ⏸
        runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
        assertFalse(h.sequencer.states.value.running)

        advanceTimeBy(60_000); runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
        assertTrue(h.captured.isEmpty())

        h.sequencer.toggle() // ▶
        runCurrent()
        assertEquals(SequencePhase.Countdown(5), h.phase)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(0), h.captured)
    }

    @Test
    fun pauseDuringTheBeatHoldsTheNextShot() = runTest {
        val h = Harness(this, timer = 3, total = 3)
        h.sequencer.start()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(SequencePhase.Beat, h.phase)
        h.sequencer.pause()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
        assertEquals(1, h.taken)
        h.sequencer.start()
        finishAll()
        assertEquals(listOf(0, 1, 2), h.captured)
    }

    @Test
    fun aCaptureInFlightFinishesEvenIfPaused() = runTest {
        val h = Harness(this, timer = 3, total = 3, captureMillis = 400)
        h.sequencer.start()
        advanceTimeBy(3_100); runCurrent()
        assertEquals(SequencePhase.Capturing, h.phase)
        h.sequencer.pause()
        runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
        advanceTimeBy(1_000); runCurrent()
        // The photo still landed, and the pause held.
        assertEquals(listOf(0), h.captured)
        assertEquals(1, h.taken)
        assertEquals(SequencePhase.Paused, h.phase)
    }

    @Test
    fun resumingWhileAShotIsStillSavingWaitsForIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val captured = mutableListOf<Int>()
        val seq = CaptureSequencer(backgroundScope, timerSec = 1, total = 2, capture = { i -> gate.await(); captured += i; true })
        seq.start()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SequencePhase.Capturing, seq.states.value.phase)
        seq.pause()
        seq.start()
        runCurrent()
        assertEquals(SequencePhase.Countdown(1), seq.states.value.phase)
        gate.complete(Unit)
        finishAll()
        assertEquals(listOf(0, 1), captured)
        assertEquals(SequencePhase.Done, seq.states.value.phase)
    }

    @Test
    fun backgroundPausesAndForegroundResumesWithAFreshCountdown() = runTest {
        val h = Harness(this, timer = 5, total = 2)
        h.sequencer.start()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(SequencePhase.Countdown(2), h.phase)
        h.sequencer.onBackground()
        runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
        assertTrue(h.sequencer.states.value.pausedByBackground)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(h.captured.isEmpty())

        h.sequencer.onForeground()
        runCurrent()
        assertEquals(SequencePhase.Countdown(5), h.phase)
        assertFalse(h.sequencer.states.value.pausedByBackground)
    }

    @Test
    fun foregroundDoesNotResumeAManualPause() = runTest {
        val h = Harness(this, timer = 3, total = 2)
        h.sequencer.start()
        advanceTimeBy(1_000); runCurrent()
        h.sequencer.pause()
        h.sequencer.onBackground()
        h.sequencer.onForeground()
        runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
    }

    @Test
    fun backgroundWhileIdleChangesNothing() = runTest {
        val h = Harness(this)
        h.sequencer.onBackground()
        h.sequencer.onForeground()
        runCurrent()
        assertEquals(SequencePhase.Idle, h.phase)
    }

    @Test
    fun aFailedShotPausesOnTheSameShot() = runTest {
        val h = Harness(this, timer = 1, total = 2)
        h.fail = true
        h.sequencer.start()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SequencePhase.Paused, h.phase)
        assertEquals(0, h.taken)
        h.fail = false
        h.sequencer.start()
        finishAll()
        assertEquals(listOf(0, 1), h.captured)
    }

    @Test
    fun continuingADraftStartsAtTheNextShot() = runTest {
        val h = Harness(this, timer = 1, total = 5, taken = 3)
        h.sequencer.start()
        finishAll()
        assertEquals(listOf(3, 4), h.captured)
    }

    @Test
    fun aCompleteDraftIsAlreadyDone() = runTest {
        val h = Harness(this, total = 4, taken = 4)
        assertEquals(SequencePhase.Done, h.phase)
        h.sequencer.start()
        runCurrent()
        assertEquals(SequencePhase.Done, h.phase)
        assertTrue(h.captured.isEmpty())
    }
}
