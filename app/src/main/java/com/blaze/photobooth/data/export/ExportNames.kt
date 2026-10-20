package com.blaze.photobooth.data.export

import com.blaze.photobooth.data.db.ShotEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What can leave the app (README §11.5): the printed strip, or one original photo with the session's filter.
 * A [Photo] is either a cut in the strip ([Photo.Kind.Cut], numbered by its slot) or an unused bonus shot
 * ([Photo.Kind.Bonus], numbered in shot order).
 */
sealed interface ExportItem {
    data object Strip : ExportItem

    data class Photo(val shotIndex: Int, val kind: Kind, val number: Int) : ExportItem {
        enum class Kind { Cut, Bonus }
    }

    companion object {
        /** The cuts in strip order: what *Save photos* / *Save all* saves (§4.5, §4.7). */
        fun cutsOf(shots: List<ShotEntity>): List<Photo> =
            shots.filter { it.slotIndex != null }
                .sortedBy { it.slotIndex }
                .map { Photo(it.shotIndex, Photo.Kind.Cut, checkNotNull(it.slotIndex) + 1) }

        /** The unused bonus shots, in the order they were taken. */
        fun bonusOf(shots: List<ShotEntity>): List<Photo> =
            shots.filter { it.slotIndex == null }
                .sortedBy { it.shotIndex }
                .mapIndexed { i, shot -> Photo(shot.shotIndex, Photo.Kind.Bonus, i + 1) }
    }
}

/**
 * File names in Photos (§11.5): `BlazeBooth_20261020_143012_strip.jpg`, `…_cut1.jpg`, `…_bonus1.jpg`, from the
 * session's created time. A name that's already taken gets `_2`, `_3`, … before the extension. Pure.
 */
object ExportNames {
    /** `MediaStore.MediaColumns.RELATIVE_PATH` for every file the app saves. */
    const val Folder = "Pictures/Blaze Booth"

    private const val Prefix = "BlazeBooth_"
    private const val Extension = ".jpg"
    private const val MaxSuffix = 999

    private val StampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)

    fun stamp(createdAt: Long, zone: ZoneId): String = StampFormat.format(Instant.ofEpochMilli(createdAt).atZone(zone))

    fun nameOf(stamp: String, item: ExportItem): String = Prefix + stamp + "_" + when (item) {
        ExportItem.Strip -> "strip"
        is ExportItem.Photo -> when (item.kind) {
            ExportItem.Photo.Kind.Cut -> "cut${item.number}"
            ExportItem.Photo.Kind.Bonus -> "bonus${item.number}"
        }
    } + Extension

    /** [name] if it's free, else the first free `name_2.jpg`, `name_3.jpg`, … */
    fun unique(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val base = name.removeSuffix(Extension)
        for (i in 2..MaxSuffix) {
            val candidate = "${base}_$i$Extension"
            if (!taken(candidate)) return candidate
        }
        // 998 copies of one strip: let MediaStore pick (it adds its own " (n)").
        return name
    }
}
