package com.blaze.photobooth.ui.feature.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the booth's automatic sequence is (README §4.3). */
sealed interface SequencePhase {
    /** Waiting for the first tap on the shutter. */
    data object Idle : SequencePhase

    /** A countdown tick: [secondsLeft] goes timer…1. */
    data class Countdown(val secondsLeft: Int) : SequencePhase

    /** The photo is being taken and saved. */
    data object Capturing : SequencePhase

    /** The 1.2 s "nice!" pause after a shot. */
    data object Beat : SequencePhase

    /** Stopped by the user, the app going to the background, or a failed shot; resuming starts a fresh countdown. */
    data object Paused : SequencePhase

    /** Every shot is taken. */
    data object Done : SequencePhase
}

data class SequenceState(
    val phase: SequencePhase = SequencePhase.Idle,
    val taken: Int = 0,
    val total: Int = 0,
    /** True while paused because the app went to the background; coming back resumes on its own. */
    val pausedByBackground: Boolean = false,
) {
    /** A countdown, capture or beat is under way (the shutter shows ⏸ and flip is disabled). */
    val running: Boolean
        get() = phase is SequencePhase.Countdown || phase == SequencePhase.Capturing || phase == SequencePhase.Beat
}

/**
 * The booth's automatic sequence (README §4.3): countdown → capture → 1.2 s beat → next countdown,
 * until [total] shots are taken. Pause and resume any time; resuming always starts a fresh countdown.
 * Backgrounding pauses, and coming back resumes (§13).
 *
 * A capture that has started always finishes (a half-taken photo is never thrown away); a pause during
 * it takes effect right after. [capture] returns false when the shot failed: the sequence pauses on the
 * same shot so the user can try again.
 *
 * Pure coroutines, so it's tested with virtual time.
 */
class CaptureSequencer(
    private val scope: CoroutineScope,
    private val timerSec: Int,
    total: Int,
    taken: Int = 0,
    private val capture: suspend (index: Int) -> Boolean,
    private val onTick: (secondsLeft: Int) -> Unit = {},
    private val beatMillis: Long = BeatMillis,
    private val finishMillis: Long = FinishMillis,
) {
    private val state = MutableStateFlow(
        SequenceState(
            phase = if (taken >= total) SequencePhase.Done else SequencePhase.Idle,
            taken = taken.coerceAtMost(total),
            total = total,
        ),
    )
    val states: StateFlow<SequenceState> = state.asStateFlow()

    private var job: Job? = null

    /** Start (from Idle) or resume (from Paused) with a fresh countdown. */
    fun start() {
        val s = state.value
        if (s.running || s.phase == SequencePhase.Done) return
        val previous = job
        state.update { it.copy(phase = SequencePhase.Countdown(timerSec), pausedByBackground = false) }
        job = scope.launch {
            // A capture that was in flight when we paused must land before the next one starts.
            previous?.join()
            run()
        }
    }

    fun pause() = stop(byBackground = false)

    /** The shutter button: start, pause or resume. */
    fun toggle() = if (state.value.running) pause() else start()

    fun onBackground() {
        if (state.value.running) stop(byBackground = true)
    }

    fun onForeground() {
        if (state.value.phase == SequencePhase.Paused && state.value.pausedByBackground) start()
    }

    private fun stop(byBackground: Boolean) {
        if (!state.value.running) return
        job?.cancel()
        state.update { it.copy(phase = SequencePhase.Paused, pausedByBackground = byBackground) }
    }

    private suspend fun run() {
        while (state.value.taken < state.value.total) {
            for (s in timerSec downTo 1) {
                state.update { it.copy(phase = SequencePhase.Countdown(s)) }
                onTick(s)
                delay(TickMillis)
            }
            state.update { it.copy(phase = SequencePhase.Capturing) }
            val ok = withContext(NonCancellable) {
                val index = state.value.taken
                capture(index).also { ok -> if (ok) state.update { it.copy(taken = index + 1) } }
            }
            // Paused while the photo was being taken: stop here, before touching the phase.
            currentCoroutineContext().ensureActive()
            if (!ok) {
                state.update { it.copy(phase = SequencePhase.Paused, pausedByBackground = false) }
                return
            }
            state.update { it.copy(phase = SequencePhase.Beat) }
            delay(if (state.value.taken < state.value.total) beatMillis else finishMillis)
        }
        state.update { it.copy(phase = SequencePhase.Done) }
    }

    companion object {
        const val TickMillis = 1_000L

        /** The "nice!" pause between shots (§4.3). */
        const val BeatMillis = 1_200L

        /** After the last shot: long enough to see it land in the tray before Pick. */
        const val FinishMillis = 900L
    }
}
