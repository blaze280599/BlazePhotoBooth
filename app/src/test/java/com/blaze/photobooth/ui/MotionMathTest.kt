package com.blaze.photobooth.ui

import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import com.blaze.photobooth.ui.components.Arc
import com.blaze.photobooth.ui.components.FontScaling
import com.blaze.photobooth.ui.components.TiltMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The pure maths behind the Setup tilt (§4.2), the A6/A8 arcs and big-font sizing (§6.7). */
class MotionMathTest {

    private val g = SensorManager.GRAVITY_EARTH

    @Test
    fun holdingThePhoneStillLeansNothing() {
        val f = TiltMath.Filter()
        // Held at an angle (gravity partly along x and y): that pose is the baseline, so the strip stays level.
        repeat(200) { f.add(2.5f, 9.4f) }
        assertEquals(0f, f.rotationX, 0.01f)
        assertEquals(0f, f.rotationY, 0.01f)
    }

    @Test
    fun aQuickTiltLeansTheStripAndNeverPastThreeDegrees() {
        val f = TiltMath.Filter()
        repeat(50) { f.add(0f, g) }
        // Tip the phone hard to the right (gravity swings onto x).
        repeat(10) { f.add(g * 0.7f, g * 0.7f) }
        assertTrue("leans right: ${f.rotationY}", f.rotationY > 1f)
        assertTrue(abs(f.rotationY) <= TiltMath.MaxDegrees)
        assertTrue(abs(f.rotationX) <= TiltMath.MaxDegrees)
    }

    @Test
    fun theLeanSettlesBackOnceThePhoneStopsMoving() {
        val f = TiltMath.Filter()
        repeat(50) { f.add(0f, g) }
        repeat(10) { f.add(3f, g) }
        val leaning = f.rotationY
        // About four seconds at 50 Hz in the new pose.
        repeat(200) { f.add(3f, g) }
        assertTrue("was $leaning, now ${f.rotationY}", abs(f.rotationY) < abs(leaning) * 0.1f)
    }

    @Test
    fun leanIsClampedAndSymmetric() {
        assertEquals(TiltMath.MaxDegrees, TiltMath.lean(g), 0.001f)
        assertEquals(-TiltMath.MaxDegrees, TiltMath.lean(-g), 0.001f)
        assertEquals(0f, TiltMath.lean(0f), 0.001f)
        assertEquals(-TiltMath.lean(0.3f), TiltMath.lean(-0.3f), 0.0001f)
    }

    @Test
    fun resetForgetsThePose() {
        val f = TiltMath.Filter()
        repeat(5) { f.add(4f, 4f) }
        f.reset()
        assertEquals(0f, f.rotationX, 0f)
        assertEquals(0f, f.rotationY, 0f)
    }

    @Test
    fun theArcStartsAndEndsOnItsPointsAndLiftsInBetween() {
        val from = Offset(100f, 800f)
        val to = Offset(300f, 1000f)
        assertEquals(from, Arc.point(from, to, lift = 120f, t = 0f))
        assertEquals(to, Arc.point(from, to, lift = 120f, t = 1f))
        val mid = Arc.point(from, to, lift = 120f, t = 0.5f)
        assertEquals(200f, mid.x, 0.001f)
        // Above the straight line between them (y grows downward).
        assertTrue("mid y ${mid.y}", mid.y < (from.y + to.y) / 2f)
    }

    @Test
    fun fixedHeightsGrowWithTheFontUpTo200Percent() {
        assertEquals(1f, FontScaling.growth(1f, 0.4f), 0f)
        assertEquals(1.4f, FontScaling.growth(2f, 0.4f), 0.0001f)
        assertEquals(1.2f, FontScaling.growth(1.5f, 0.4f), 0.0001f)
        // Smaller fonts don't shrink the pane, and 300% grows no more than 200%.
        assertEquals(1f, FontScaling.growth(0.85f, 0.4f), 0f)
        assertEquals(1.4f, FontScaling.growth(3f, 0.4f), 0.0001f)
    }

    @Test
    fun aLineBreakMayNotCutAWord() {
        // "Ikmyeong Night" broken after "Ikmyeon" splits a word; after the space it doesn't.
        assertTrue(FontScaling.splitsAWord("Ikmyeong Night", listOf(7)))
        assertTrue(!FontScaling.splitsAWord("Ikmyeong Night", listOf(9)))
        assertTrue(!FontScaling.splitsAWord("Lilac Pals", emptyList()))
    }
}
