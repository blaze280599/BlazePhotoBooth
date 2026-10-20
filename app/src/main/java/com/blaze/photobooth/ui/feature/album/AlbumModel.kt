package com.blaze.photobooth.ui.feature.album

import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.model.BoothLayout
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One strip in the album grid (README §4.6). [image] is `thumb.jpg` (or `strip.jpg` if the thumbnail is lost) and
 * [version] its last-modified time, for the image cache. [aspect] is the picture's width / height, so the
 * staggered grid can lay cards out before any picture has loaded.
 */
@Immutable
data class AlbumItem(
    val id: String,
    val createdAt: Long,
    val date: LocalDate,
    val layoutId: String,
    val favorite: Boolean,
    val image: File,
    val version: Long,
    val aspect: Float,
)

/** A row of the grid: a month header spanning both columns, or a strip card. */
@Immutable
sealed interface AlbumEntry {
    val key: String

    data class Header(val month: YearMonth) : AlbumEntry {
        override val key: String get() = "month-$month"
    }

    data class Strip(val item: AlbumItem) : AlbumEntry {
        override val key: String get() = item.id
    }
}

/**
 * The filter chips (§4.6): All · Favourites · one per layout in the album. Image frames (her frames, bundled or
 * imported) are one group of their own, whatever their window count.
 */
@Immutable
sealed interface AlbumFilter {
    data object All : AlbumFilter
    data object Favorites : AlbumFilter

    /** [layoutId] is a `sessions.layout_id`: a [BoothLayout] id, or [SessionEntity.ImageLayoutId] for her frames. */
    data class Layout(val layoutId: String) : AlbumFilter {
        val herFrames: Boolean get() = layoutId == SessionEntity.ImageLayoutId
        val layout: BoothLayout? get() = BoothLayout.fromId(layoutId)
    }
}

/** Grouping and filtering for the album grid. Pure (the labels use the platform's date patterns), so it's unit-tested. */
object AlbumGrouping {

    /** The month a strip was made in, in the phone's time zone. */
    fun monthOf(item: AlbumItem): YearMonth = YearMonth.from(item.date)

    fun dateOf(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Newest first, under a header for each month ("October 2026"). [items] must already be newest first. */
    fun group(items: List<AlbumItem>): List<AlbumEntry> {
        val out = ArrayList<AlbumEntry>(items.size + 4)
        var month: YearMonth? = null
        for (item in items) {
            val m = monthOf(item)
            if (m != month) {
                out += AlbumEntry.Header(m)
                month = m
            }
            out += AlbumEntry.Strip(item)
        }
        return out
    }

    fun filter(items: List<AlbumItem>, filter: AlbumFilter): List<AlbumItem> = when (filter) {
        AlbumFilter.All -> items
        AlbumFilter.Favorites -> items.filter { it.favorite }
        is AlbumFilter.Layout -> items.filter { it.layoutId == filter.layoutId }
    }

    /**
     * The chips to show: All and Favourites always, then her frames first (it's her album) and the built-in
     * layouts in catalog order, but only the layouts that have strips (no chip leads to an empty grid).
     * Unknown layout ids get no chip.
     */
    fun filters(items: List<AlbumItem>): List<AlbumFilter> {
        val present = items.mapTo(HashSet()) { it.layoutId }
        val order = listOf(SessionEntity.ImageLayoutId) + BoothLayout.entries.map { it.id }
        return listOf(AlbumFilter.All, AlbumFilter.Favorites) + order.filter { it in present }.map { AlbumFilter.Layout(it) }
    }

    /**
     * The sticky header (§4.6): the index in [entries] of the header of the section that grid row
     * [firstVisible] belongs to, or null for an empty grid.
     */
    fun headerIndexAt(entries: List<AlbumEntry>, firstVisible: Int): Int? {
        if (entries.isEmpty()) return null
        var i = firstVisible.coerceIn(0, entries.lastIndex)
        while (i >= 0) {
            if (entries[i] is AlbumEntry.Header) return i
            i--
        }
        return null
    }

    /** The first header after [index], if any: it pushes the sticky header up as it arrives. */
    fun nextHeaderIndex(entries: List<AlbumEntry>, index: Int): Int? {
        for (i in index + 1..entries.lastIndex) if (entries[i] is AlbumEntry.Header) return i
        return null
    }

    /** "October 2026", in the order and words of [locale] ("2026년 10월" in Korean). */
    fun monthLabel(month: YearMonth, locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale))

    /** The small date on a card: "Oct 20". */
    fun cardDate(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale))
}
