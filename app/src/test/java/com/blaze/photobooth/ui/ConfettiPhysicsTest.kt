package com.blaze.photobooth.ui

import com.blaze.photobooth.ui.components.ConfettiPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §7 A9: about 80 pieces, fired up from one point, falling under gravity, gone after 2.5 s. */
class ConfettiPhysicsTest {

    private val burst = ConfettiPhysics.burst(0.5f, 0.4f)

    @Test
    fun eightyPiecesFromOnePointTheSameEveryTime() {
        assertEquals(80, burst.size)
        assertEquals(burst, ConfettiPhysics.burst(0.5f, 0.4f))
        for (p in burst) assertEquals(0.4f, ConfettiPhysics.position(p, 0f).y, 1e-6f)
    }

    @Test
    fun everyPieceFliesUpFirstThenFalls() {
        for (p in burst) {
            assertTrue(p.vy < 0f)
            assertTrue(ConfettiPhysics.position(p, 0.1f).y < 0.4f)
            assertTrue(ConfettiPhysics.position(p, 2.4f).y > ConfettiPhysics.position(p, 1.5f).y)
        }
    }

    @Test
    fun itFadesOutAtTheEnd() {
        assertEquals(1f, ConfettiPhysics.alpha(1f))
        assertEquals(0.5f, ConfettiPhysics.alpha(2.25f), 1e-4f)
        assertEquals(0f, ConfettiPhysics.alpha(ConfettiPhysics.DurationSeconds))
    }
}
