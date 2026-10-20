package com.blaze.photobooth.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Size
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * README §9.4 on real bitmaps: a frame with four coloured quarters goes through the actual transform,
 * and each quarter must land where an upright (and, for selfies, mirrored) photo puts it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShotProcessorTest {

    /** Top-left red, top-right green, bottom-left blue, bottom-right yellow. */
    private fun quarters(w: Int = 400, h: Int = 300): Bitmap {
        val b = createBitmap(w, h)
        val c = Canvas(b)
        val p = Paint()
        fun fill(color: Int, l: Int, t: Int) {
            p.color = color
            c.drawRect(l.toFloat(), t.toFloat(), (l + w / 2).toFloat(), (t + h / 2).toFloat(), p)
        }
        fill(Color.RED, 0, 0)
        fill(Color.GREEN, w / 2, 0)
        fill(Color.BLUE, 0, h / 2)
        fill(Color.YELLOW, w / 2, h / 2)
        return b
    }

    /** Colours of the output's corners, sampled a little inside: TL, TR, BL, BR. */
    private fun corners(b: Bitmap): List<Int> {
        val i = 8
        return listOf(
            b.getPixel(i, i),
            b.getPixel(b.width - 1 - i, i),
            b.getPixel(i, b.height - 1 - i),
            b.getPixel(b.width - 1 - i, b.height - 1 - i),
        )
    }

    @Test
    fun backCameraRotation90IsUprightAndNotMirrored() {
        val src = quarters()
        val out = ShotProcessor.transform(src, ShotGeometry.plan(400, 300, 90, mirror = false, aspect = 0.75f))
        assertEquals(300, out.width)
        assertEquals(400, out.height)
        // Clockwise quarter turn: the left column (red over blue) becomes the top row, blue on the left.
        assertEquals(listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN), corners(out))
    }

    @Test
    fun frontCameraRotation270IsUprightAndMirrored() {
        val src = quarters()
        val out = ShotProcessor.transform(src, ShotGeometry.plan(400, 300, 270, mirror = true, aspect = 0.75f))
        assertEquals(listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED), corners(out))
    }

    @Test
    fun frontCameraWithMirrorOffIsJustRotated() {
        val src = quarters()
        val out = ShotProcessor.transform(src, ShotGeometry.plan(400, 300, 270, mirror = false, aspect = 0.75f))
        assertEquals(listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE), corners(out))
    }

    @Test
    fun cropKeepsTheMiddleForALandscapeSlot() {
        // Upright 300×400 cropped to a 3:2 landscape slot keeps the middle 300×200 band.
        val src = quarters()
        val plan = ShotGeometry.plan(400, 300, 90, mirror = false, aspect = 1.5f)
        val out = ShotProcessor.transform(src, plan)
        assertEquals(300, out.width)
        assertEquals(200, out.height)
        // The band spans the source's left/right split: blue and red on top, yellow and green below.
        assertEquals(listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN), corners(out))
    }

    @Test
    fun thumbnailIsSmallAndAlwaysACopy() {
        val big = createBitmap(2400, 1800)
        val thumb = ShotProcessor.thumbnail(big)
        assertEquals(ShotProcessor.ThumbPx, thumb.width)
        val small = createBitmap(100, 80)
        assertNotSame(small, ShotProcessor.thumbnail(small))
    }

    @Test
    fun photoSizesAreCappedAt12Megapixels() {
        val sizes = listOf(Size(8160, 6120), Size(4080, 3072), Size(4000, 3000), Size(1920, 1440))
        assertEquals(listOf(Size(4000, 3000), Size(1920, 1440)), CameraController.atMost12Mp(sizes))
        assertEquals(listOf(Size(8160, 6120)), CameraController.atMost12Mp(listOf(Size(8160, 6120))))
    }
}
