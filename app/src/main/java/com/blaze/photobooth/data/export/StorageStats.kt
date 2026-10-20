package com.blaze.photobooth.data.export

import java.io.File
import java.util.Locale

/** What Settings' *Storage used* row says (README §4.8): "42 strips · 310 MB". */
data class StorageUsage(val strips: Int, val bytes: Long)

/** Pure helpers for [StorageUsage]; the walk runs on `Dispatchers.IO` in the caller. */
object StorageStats {

    /** Every byte under [dir] (all sessions, their shots and strips, and the draft). */
    fun bytesIn(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /**
     * Megabytes the way Android's own Settings counts them (1 MB = 1,000,000 bytes): one decimal under 10 MB,
     * whole numbers above, and never "0" for something that isn't empty.
     */
    fun megabytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes <= 0) return "0"
        val mb = bytes / 1_000_000.0
        return when {
            mb < 0.1 -> String.format(locale, "%.1f", 0.1)
            mb < 10 -> String.format(locale, "%.1f", mb)
            else -> String.format(locale, "%,d", Math.round(mb))
        }
    }
}
