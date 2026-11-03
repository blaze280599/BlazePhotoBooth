package com.blaze.photobooth.data

import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.render.FrameSlotDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Her bundled frames (README §5.5.6), read from the real `frames.json` the app ships. */
class FrameManifestTest {

    private val frames = FrameRepository.parseManifest(File("src/main/assets/frames/frames.json").readText())

    @Test
    fun herTwelveFramesInCarouselOrder() {
        // Her first five (v1.0), the two Molang frames (v1.1), then five strips added in v1.2.
        assertEquals(
            listOf(
                "lilac-pals", "usagi-gold", "ikmyeong-night", "blossom-picnic", "rribbitt-sky", "molang-pineapple", "molang-pool",
                "best-day", "union-jack", "hbd-autumn", "snowy-christmas", "yalary-spring",
            ).map(FrameId::asset),
            frames.map { it.id },
        )
    }

    @Test
    fun lilacPalsHasThreeWindowsTheOthersFour() {
        assertEquals(listOf(3) + List(11) { 4 }, frames.map { it.slots.size })
    }

    @Test
    fun theV12FramesAreSingleColumnStrips() {
        for (id in listOf("best-day", "union-jack", "hbd-autumn", "snowy-christmas", "yalary-spring")) {
            val frame = frames.first { it.id == FrameId.asset(id) }
            assertEquals(id, 1200, frame.width)
            assertTrue(id, frame.height > 2.5 * frame.width)
            // One window per row, top to bottom.
            frame.slots.zipWithNext { a, b -> assertTrue(id, b.y >= a.bottom) }
        }
    }

    @Test
    fun theMolangFramesAreTwoByTwoGrids() {
        for (id in listOf("molang-pineapple", "molang-pool")) {
            val frame = frames.first { it.id == FrameId.asset(id) }
            assertEquals(id, 1800 to 2695, frame.width to frame.height)
            val s = frame.slots
            // Two rows of two: tops match in a row, lefts match in a column.
            assertEquals(id, s[0].y, s[1].y)
            assertEquals(id, s[2].y, s[3].y)
            assertEquals(id, s[0].x, s[2].x)
            assertEquals(id, s[1].x, s[3].x)
            assertTrue(id, s[1].x > s[0].x && s[2].y > s[0].y)
        }
    }

    @Test
    fun slotsAreOnTheArtworkInOrderAndApart() {
        for (frame in frames) {
            assertEquals(frame.slots, FrameSlotDetector.order(frame.slots, frame.height))
            for (s in frame.slots) {
                assertTrue("${frame.id} $s", s.x >= 0 && s.y >= 0 && s.right <= frame.width && s.bottom <= frame.height)
            }
            for (i in frame.slots.indices) for (j in i + 1 until frame.slots.size) {
                assertFalse("${frame.id} slots $i/$j overlap", frame.slots[i].overlaps(frame.slots[j]))
            }
        }
    }

    @Test
    fun artworkFilesExist() {
        for (frame in frames) {
            for (location in listOf(frame.artwork, frame.thumb)) {
                val path = (location as ArtworkLocation.Asset).path
                assertTrue(path, File("src/main/assets/$path").isFile)
            }
        }
    }
}
