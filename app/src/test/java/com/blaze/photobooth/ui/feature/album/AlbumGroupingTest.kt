package com.blaze.photobooth.ui.feature.album

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.db.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * README §4.6: newest first under month headers, the filter chips, and which header sticks to the top. Robolectric
 * only for the platform's localized date patterns.
 */
@RunWith(AndroidJUnit4::class)
class AlbumGroupingTest {

    private fun item(id: String, date: LocalDate, layout: String = "classic", favorite: Boolean = false) = AlbumItem(
        id = id,
        createdAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        date = date,
        layoutId = layout,
        favorite = favorite,
        image = File("$id/thumb.jpg"),
        version = 0,
        aspect = 1f / 3f,
    )

    private val oct20 = item("a", LocalDate.of(2026, 10, 20), favorite = true)
    private val oct2 = item("b", LocalDate.of(2026, 10, 2), layout = SessionEntity.ImageLayoutId)
    private val sep30 = item("c", LocalDate.of(2026, 9, 30), layout = "grid", favorite = true)
    private val dec25 = item("d", LocalDate.of(2025, 12, 25), layout = SessionEntity.ImageLayoutId)
    private val album = listOf(oct20, oct2, sep30, dec25)

    @Test
    fun stripsAreGroupedUnderAHeaderPerMonth() {
        val entries = AlbumGrouping.group(album)
        assertEquals(
            listOf("month-2026-10", "a", "b", "month-2026-09", "c", "month-2025-12", "d"),
            entries.map { it.key },
        )
        assertEquals(YearMonth.of(2025, 12), (entries[5] as AlbumEntry.Header).month)
    }

    @Test
    fun anEmptyAlbumHasNoHeaders() {
        assertEquals(emptyList<AlbumEntry>(), AlbumGrouping.group(emptyList()))
        assertNull(AlbumGrouping.headerIndexAt(emptyList(), 0))
    }

    @Test
    fun theMonthComesFromThePhonesTimeZone() {
        // 23:30 UTC on 31 October is already 1 November in Seoul.
        val millis = LocalDate.of(2026, 10, 31).atStartOfDay(ZoneOffset.UTC).plusHours(23).plusMinutes(30).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 1), AlbumGrouping.dateOf(millis, ZoneId.of("Asia/Seoul")))
        assertEquals(LocalDate.of(2026, 10, 31), AlbumGrouping.dateOf(millis, ZoneOffset.UTC))
    }

    @Test
    fun filtersPickFavouritesAndLayoutsWithHerFramesAsOneGroup() {
        assertEquals(album, AlbumGrouping.filter(album, AlbumFilter.All))
        assertEquals(listOf("a", "c"), AlbumGrouping.filter(album, AlbumFilter.Favorites).map { it.id })
        assertEquals(listOf("b", "d"), AlbumGrouping.filter(album, AlbumFilter.Layout(SessionEntity.ImageLayoutId)).map { it.id })
        assertEquals(listOf("c"), AlbumGrouping.filter(album, AlbumFilter.Layout("grid")).map { it.id })
        assertEquals(emptyList<AlbumItem>(), AlbumGrouping.filter(album, AlbumFilter.Layout("six")))
    }

    @Test
    fun onlyLayoutsInTheAlbumGetAChipAndHerFramesComeFirst() {
        assertEquals(
            listOf(
                AlbumFilter.All,
                AlbumFilter.Favorites,
                AlbumFilter.Layout(SessionEntity.ImageLayoutId),
                AlbumFilter.Layout("classic"),
                AlbumFilter.Layout("grid"),
            ),
            AlbumGrouping.filters(album),
        )
        assertEquals(listOf(AlbumFilter.All, AlbumFilter.Favorites), AlbumGrouping.filters(emptyList()))
        // A layout id nobody knows (a future one) gets no chip.
        assertEquals(2, AlbumGrouping.filters(listOf(item("x", LocalDate.of(2026, 1, 1), layout = "mystery"))).size)
        assertEquals(true, AlbumFilter.Layout(SessionEntity.ImageLayoutId).herFrames)
    }

    @Test
    fun theStickyHeaderIsTheHeaderOfTheTopRowsMonth() {
        val entries = AlbumGrouping.group(album) // H(oct) a b H(sep) c H(dec) d
        assertEquals(0, AlbumGrouping.headerIndexAt(entries, 0))
        assertEquals(0, AlbumGrouping.headerIndexAt(entries, 2))
        assertEquals(3, AlbumGrouping.headerIndexAt(entries, 3))
        assertEquals(3, AlbumGrouping.headerIndexAt(entries, 4))
        assertEquals(5, AlbumGrouping.headerIndexAt(entries, 6))
        // Past the end (a stale index while the list shrinks) stays in range.
        assertEquals(5, AlbumGrouping.headerIndexAt(entries, 99))
        assertEquals(3, AlbumGrouping.nextHeaderIndex(entries, 0))
        assertEquals(5, AlbumGrouping.nextHeaderIndex(entries, 3))
        assertNull(AlbumGrouping.nextHeaderIndex(entries, 5))
    }

    @Test
    fun labels() {
        assertEquals("October 2026", AlbumGrouping.monthLabel(YearMonth.of(2026, 10), Locale.US))
        assertEquals("Oct 20", AlbumGrouping.cardDate(LocalDate.of(2026, 10, 20), Locale.US))
        assertEquals("2026년 10월", AlbumGrouping.monthLabel(YearMonth.of(2026, 10), Locale.KOREAN))
    }
}
