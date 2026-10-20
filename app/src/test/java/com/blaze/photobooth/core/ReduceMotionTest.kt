package com.blaze.photobooth.core

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import com.blaze.photobooth.debug.ReduceMotionTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §6.7: reduce motion, the debug override and the calm motion helpers. */
class ReduceMotionTest {

    @Test
    fun theSystemSettingRulesWithoutAnOverride() {
        assertTrue(ReduceMotionOverride.resolve(system = true, override = null))
        assertFalse(ReduceMotionOverride.resolve(system = false, override = null))
    }

    @Test
    fun aDebugOverrideWinsBothWays() {
        assertTrue(ReduceMotionOverride.resolve(system = false, override = true))
        assertFalse(ReduceMotionOverride.resolve(system = true, override = false))
    }

    @Test
    fun theOverrideIsUnsetByDefault() {
        assertNull(ReduceMotionOverride.value.value)
    }

    @Test
    fun theTriggerFileReadsOnOffOrNothing() {
        assertEquals(true, ReduceMotionTrigger.parse("on"))
        assertEquals(true, ReduceMotionTrigger.parse(" ON\n"))
        assertEquals(false, ReduceMotionTrigger.parse("off"))
        assertEquals(false, ReduceMotionTrigger.parse("0"))
        assertNull(ReduceMotionTrigger.parse(null))
        assertNull(ReduceMotionTrigger.parse(""))
        assertNull(ReduceMotionTrigger.parse("maybe"))
    }

    @Test
    fun withReduceMotionThingsOnlyCrossFade() {
        val pop = scaleIn() + fadeIn()
        val slide = slideInVertically { it }
        assertEquals(fadeIn(), Calm.enter(true, pop))
        assertEquals(fadeIn(), Calm.enter(true, slide))
        assertEquals(fadeOut(), Calm.exit(true, scaleOut() + fadeOut()))
    }

    @Test
    fun withoutReduceMotionTheMotionIsKept() {
        val pop = scaleIn() + fadeIn()
        val leave = scaleOut() + fadeOut()
        assertSame(pop, Calm.enter(false, pop))
        assertSame(leave, Calm.exit(false, leave))
    }

    @Test
    fun withReduceMotionSpringsSnap() {
        val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)
        assertTrue(Calm.spec(true, bouncy) is SnapSpec)
        assertSame(bouncy, Calm.spec(false, bouncy))
    }
}
