package com.blaze.photobooth.data.export

import com.blaze.photobooth.data.db.ShotEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** README §11.5: file names come from the session's created time, and a taken name gets `_2`, `_3`, … */
class ExportNamesTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val created = LocalDateTime.of(2026, 10, 20, 14, 30, 12).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun namesUseTheSessionsCreatedTimeInTheLocalZone() {
        val stamp = ExportNames.stamp(created, seoul)
        assertEquals("20261020_143012", stamp)
        assertEquals("BlazeBooth_20261020_143012_strip.jpg", ExportNames.nameOf(stamp, ExportItem.Strip))
        assertEquals("BlazeBooth_20261020_143012_cut1.jpg", ExportNames.nameOf(stamp, ExportItem.Photo(4, ExportItem.Photo.Kind.Cut, 1)))
        assertEquals("BlazeBooth_20261020_143012_bonus2.jpg", ExportNames.nameOf(stamp, ExportItem.Photo(5, ExportItem.Photo.Kind.Bonus, 2)))
        // Same moment seen from another zone: that phone's own clock time.
        assertEquals("20261020_073012", ExportNames.stamp(created, ZoneId.of("Europe/Paris")))
    }

    @Test
    fun aFreeNameIsKept() {
        assertEquals("a.jpg", ExportNames.unique("a.jpg") { false })
    }

    @Test
    fun takenNamesGetTheNextFreeNumber() {
        val taken = setOf("BlazeBooth_x_strip.jpg", "BlazeBooth_x_strip_2.jpg", "BlazeBooth_x_strip_3.jpg")
        assertEquals("BlazeBooth_x_strip_4.jpg", ExportNames.unique("BlazeBooth_x_strip.jpg", taken::contains))
        assertEquals("BlazeBooth_x_cut1_2.jpg", ExportNames.unique("BlazeBooth_x_cut1.jpg", setOf("BlazeBooth_x_cut1.jpg")::contains))
    }

    @Test
    fun cutsFollowTheStripAndBonusShotsFollowTheirOrder() {
        // Shot 0 is in slot 2, shot 1 unused, shot 2 in slot 0, shot 3 in slot 1, shot 4 unused.
        val shots = listOf(2, null, 0, 1, null).mapIndexed { i, slot -> ShotEntity("s", i, slot, 100, 100) }
        assertEquals(
            listOf(
                ExportItem.Photo(2, ExportItem.Photo.Kind.Cut, 1),
                ExportItem.Photo(3, ExportItem.Photo.Kind.Cut, 2),
                ExportItem.Photo(0, ExportItem.Photo.Kind.Cut, 3),
            ),
            ExportItem.cutsOf(shots),
        )
        assertEquals(
            listOf(ExportItem.Photo(1, ExportItem.Photo.Kind.Bonus, 1), ExportItem.Photo(4, ExportItem.Photo.Kind.Bonus, 2)),
            ExportItem.bonusOf(shots),
        )
    }
}
