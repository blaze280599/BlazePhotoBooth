package com.blaze.photobooth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameIdTest {

    @Test
    fun formsFollowTheReadme() {
        assertEquals("theme:strawberry", FrameId.theme(FrameTheme.Strawberry).value)
        assertEquals("asset:lilac-pals", FrameId.asset("lilac-pals").value)
        assertEquals("custom:1234", FrameId.custom("1234").value)
    }

    @Test
    fun parsesKindAndLocalId() {
        assertEquals(FrameId.Kind.Theme, FrameId("theme:film").kind)
        assertEquals(FrameId.Kind.Asset, FrameId("asset:usagi-gold").kind)
        assertEquals(FrameId.Kind.Custom, FrameId("custom:abc").kind)
        assertEquals("usagi-gold", FrameId("asset:usagi-gold").localId)
        assertNull(FrameId("nonsense").kind)
        assertNull(FrameId("theme:").kind)
    }
}
