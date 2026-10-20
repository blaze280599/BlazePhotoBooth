package com.blaze.photobooth.ui.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §5.6.1: which pose the card shows, and which card, at each moment of the sequence. */
class PoseCardStateTest {

    private val plan = listOf("smile", "wink", "laugh", "blow-kiss", "cat-paws", "tongue")

    private fun state(phase: SequencePhase, taken: Int, retake: Int? = null, poseIdeas: Boolean = true) = CaptureUiState(
        loaded = true,
        cuts = 4,
        sequence = SequenceState(phase = phase, taken = taken, total = if (retake != null) 1 else plan.size),
        retakeIndex = retake,
        posePlan = plan,
        poseIdeas = poseIdeas,
    )

    @Test
    fun firstUpBeforeTheFirstShot() {
        val s = state(SequencePhase.Idle, taken = 0)
        assertEquals("smile", s.shownPose?.id)
        assertEquals(PoseCardKind.FirstUp, s.poseCardKind)
        assertTrue(s.canSwapPose)
    }

    @Test
    fun theCurrentShotDuringTheCountdownAndTheNextOneDuringTheBeat() {
        assertEquals("laugh", state(SequencePhase.Countdown(3), taken = 2).shownPose?.id)
        assertEquals(PoseCardKind.Now, state(SequencePhase.Countdown(3), taken = 2).poseCardKind)
        // After shot 3 lands, the beat previews shot 4.
        val beat = state(SequencePhase.Beat, taken = 3)
        assertEquals("blow-kiss", beat.shownPose?.id)
        assertEquals(PoseCardKind.Next, beat.poseCardKind)
        assertTrue(beat.canSwapPose)
    }

    @Test
    fun noSwapWhileThePhotoIsTakenAndNoCardWhenDone() {
        assertFalse(state(SequencePhase.Capturing, taken = 1).canSwapPose)
        assertEquals("wink", state(SequencePhase.Capturing, taken = 1).shownPose?.id)
        assertNull(state(SequencePhase.Beat, taken = 6).shownPose)
        assertNull(state(SequencePhase.Done, taken = 6).shownPose)
        assertNull(state(SequencePhase.Done, taken = 6).poseCardKind)
    }

    @Test
    fun aContinuedSessionSaysUpNext() {
        assertEquals(PoseCardKind.UpNext, state(SequencePhase.Idle, taken = 2).poseCardKind)
        assertEquals(PoseCardKind.UpNext, state(SequencePhase.Paused, taken = 2).poseCardKind)
        assertEquals("laugh", state(SequencePhase.Paused, taken = 2).shownPose?.id)
    }

    @Test
    fun aRetakeShowsThePosePlannedForThatShot() {
        val s = state(SequencePhase.Idle, taken = 0, retake = 3)
        assertEquals("blow-kiss", s.shownPose?.id)
        assertEquals(PoseCardKind.UpNext, s.poseCardKind)
        assertEquals("blow-kiss", state(SequencePhase.Countdown(2), taken = 0, retake = 3).shownPose?.id)
        // Once the retake is taken there's nothing next.
        assertNull(state(SequencePhase.Beat, taken = 1, retake = 3).shownPose)
    }

    @Test
    fun poseIdeasOffMeansNoCard() {
        assertNull(state(SequencePhase.Idle, taken = 0, poseIdeas = false).shownPose)
        assertNull(state(SequencePhase.Countdown(5), taken = 1, poseIdeas = false).shownPose)
        assertFalse(state(SequencePhase.Idle, taken = 0, poseIdeas = false).canSwapPose)
    }

    @Test
    fun nothingBeforeTheDraftHasLoaded() {
        assertNull(CaptureUiState().shownPose)
        assertNull(state(SequencePhase.Idle, 0).copy(posePlan = emptyList()).shownPose)
    }
}
