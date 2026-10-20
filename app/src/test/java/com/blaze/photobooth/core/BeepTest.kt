package com.blaze.photobooth.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** README §6.5: the countdown beep is soft (≤ 120 ms, quiet) and starts and ends at silence, so it never clicks. */
class BeepTest {

    @Test
    fun lengthMatchesTheDuration() {
        assertEquals(Beep.SampleRate * 90 / 1000, Beep.pcm(880f, 90).size)
    }

    @Test
    fun isQuietAndClickFree() {
        val pcm = Beep.pcm(880f, 110, volume = 0.22f)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 1..(0.22f * Short.MAX_VALUE).toInt() + 1)
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 50)
    }
}
