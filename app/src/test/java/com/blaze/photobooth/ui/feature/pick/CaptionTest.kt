package com.blaze.photobooth.ui.feature.pick

import org.junit.Assert.assertEquals
import org.junit.Test

/** README §13: a very long caption or emoji: at most 24 chars, one line, never a broken character. */
class CaptionTest {

    @Test
    fun shortCaptionsAreKept() {
        assertEquals("best day ♡", PickViewModel.cleanCaption("best day ♡"))
        assertEquals("", PickViewModel.cleanCaption(""))
    }

    @Test
    fun aLineBreakBecomesASpace() {
        assertEquals("best day", PickViewModel.cleanCaption("best\nday"))
    }

    @Test
    fun longCaptionsStopAt24() {
        val long = "W".repeat(40)
        assertEquals("W".repeat(24), PickViewModel.cleanCaption(long))
        assertEquals(PickViewModel.MaxCaption, 24)
    }

    @Test
    fun anEmojiIsNeverCutInHalf() {
        // "🫶" is two chars: 23 letters + 🫶 would end on half an emoji at char 24.
        val caption = PickViewModel.cleanCaption("a".repeat(23) + "🫶🫶")
        assertEquals("a".repeat(23), caption)
        // Twelve emoji fit exactly.
        assertEquals("🫶".repeat(12), PickViewModel.cleanCaption("🫶".repeat(20)))
        // A joined emoji (couple with heart, 8 chars) is kept whole or left out whole.
        val couple = "👩‍❤️‍👨"
        val cut = PickViewModel.cleanCaption("x".repeat(20) + couple)
        assertEquals("x".repeat(20), cut)
        assertEquals("x".repeat(16) + couple, PickViewModel.cleanCaption("x".repeat(16) + couple + "yy"))
    }

    @Test
    fun koreanFitsLikeAnyOtherText() {
        val hangul = "오늘의 네컷"
        assertEquals(hangul, PickViewModel.cleanCaption(hangul))
        assertEquals(24, PickViewModel.cleanCaption("네".repeat(30)).length)
    }
}
