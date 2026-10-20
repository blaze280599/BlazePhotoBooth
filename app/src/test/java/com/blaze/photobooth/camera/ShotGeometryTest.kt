package com.blaze.photobooth.camera

import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.render.LayoutGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §9.4: every stored shot is upright, mirrored only for selfies, cropped to its slot and ≤ 2400 px. */
class ShotGeometryTest {

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals("x", expected.first, actual.first, 0.01f)
        assertEquals("y", expected.second, actual.second, 0.01f)
    }

    @Test
    fun onlyFrontCameraSelfiesAreMirrored() {
        assertTrue(ShotGeometry.shouldMirror(Lens.Front, mirrorSetting = true))
        assertFalse(ShotGeometry.shouldMirror(Lens.Front, mirrorSetting = false))
        assertFalse(ShotGeometry.shouldMirror(Lens.Back, mirrorSetting = true))
        assertFalse(ShotGeometry.shouldMirror(Lens.Back, mirrorSetting = false))
    }

    @Test
    fun rotationSnapsToQuarterTurns() {
        assertEquals(0, ShotGeometry.normalizeRotation(0))
        assertEquals(90, ShotGeometry.normalizeRotation(90))
        assertEquals(270, ShotGeometry.normalizeRotation(-90))
        assertEquals(0, ShotGeometry.normalizeRotation(360))
        assertEquals(180, ShotGeometry.normalizeRotation(178))
    }

    @Test
    fun portraitPhoneRotatesALandscapeSensorFrameUpright() {
        // A 4:3 sensor frame with rotation 270 (typical front camera, portrait phone) → 3000×4000 upright.
        val plan = ShotGeometry.plan(4000, 3000, 270, mirror = false, aspect = 1f)
        assertEquals(3000, plan.uprightWidth)
        assertEquals(4000, plan.uprightHeight)
    }

    @Test
    fun cropMatchesTheSlotAspectAndIsCentred() {
        val classic = LayoutGeometry.cells(BoothLayout.Classic).first().aspect // ≈ 1.43, landscape cell
        val plan = ShotGeometry.plan(4000, 3000, 90, mirror = true, aspect = classic)
        assertEquals(classic, plan.crop.aspect, 0.001f)
        assertEquals(3000f, plan.crop.w, 0.5f) // full upright width…
        assertEquals((4000f - plan.crop.h) / 2f, plan.crop.y, 0.5f) // …from the middle
        assertEquals(classic, plan.outWidth.toFloat() / plan.outHeight, 0.002f)
    }

    @Test
    fun longEdgeIsAtMost2400() {
        val plan = ShotGeometry.plan(4000, 3000, 0, mirror = false, aspect = 4f / 3f)
        assertEquals(2400, plan.outWidth)
        assertEquals(1800, plan.outHeight)
        val tall = ShotGeometry.plan(4000, 3000, 90, mirror = false, aspect = 0.73f)
        assertEquals(2400, tall.outHeight)
        assertTrue(tall.outWidth < 2400)
    }

    @Test
    fun smallFramesAreNeverUpscaled() {
        val plan = ShotGeometry.plan(1600, 1200, 0, mirror = false, aspect = 1f)
        assertEquals(1200, plan.outWidth)
        assertEquals(1200, plan.outHeight)
    }

    @Test
    fun rotation90TurnsTheTopLeftCornerToTheTopRight() {
        // Clockwise quarter turn, no crop (aspect = upright aspect), no scaling (small).
        val plan = ShotGeometry.plan(400, 300, 90, mirror = false, aspect = 300f / 400f)
        assertPoint(300f to 0f, plan.map(0f, 0f))
        assertPoint(300f to 400f, plan.map(400f, 0f))
        assertPoint(0f to 0f, plan.map(0f, 300f))
    }

    @Test
    fun rotation270TurnsTheTopLeftCornerToTheBottomLeft() {
        val plan = ShotGeometry.plan(400, 300, 270, mirror = false, aspect = 300f / 400f)
        assertPoint(0f to 400f, plan.map(0f, 0f))
        assertPoint(300f to 0f, plan.map(400f, 300f))
    }

    @Test
    fun rotation180FlipsBothAxes() {
        val plan = ShotGeometry.plan(400, 300, 180, mirror = false, aspect = 400f / 300f)
        assertPoint(400f to 300f, plan.map(0f, 0f))
        assertPoint(0f to 0f, plan.map(400f, 300f))
    }

    @Test
    fun mirrorFlipsLeftAndRightAfterRotating() {
        // Rotated 270 (front camera), then mirrored: the source top-left lands bottom-right.
        val plan = ShotGeometry.plan(400, 300, 270, mirror = true, aspect = 300f / 400f)
        assertPoint(300f to 400f, plan.map(0f, 0f))
        val plain = ShotGeometry.plan(400, 300, 0, mirror = true, aspect = 400f / 300f)
        assertPoint(400f to 0f, plain.map(0f, 0f))
        assertPoint(0f to 300f, plain.map(400f, 300f))
    }

    @Test
    fun cropAndScaleMapTheCropRectOntoTheWholeOutput() {
        val plan = ShotGeometry.plan(4000, 3000, 0, mirror = false, aspect = 1f)
        // The 3000×3000 centre square of the frame fills the 2400×2400 output.
        assertPoint(0f to 0f, plan.map(500f, 0f))
        assertPoint(2400f to 2400f, plan.map(3500f, 3000f))
        assertPoint(1200f to 1200f, plan.map(2000f, 1500f))
    }
}
