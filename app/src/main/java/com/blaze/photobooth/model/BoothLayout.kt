package com.blaze.photobooth.model

/**
 * The built-in strip layouts (README §5.1). Every number is in export pixels (600 dpi).
 * Cell rects are computed by [com.blaze.photobooth.render.LayoutGeometry], never hand-placed.
 */
enum class BoothLayout(
    val id: String,
    val rows: Int,
    val cols: Int,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val margin: Int,
    val gap: Int,
    val footer: Int,
) {
    Classic("classic", rows = 4, cols = 1, canvasWidth = 1200, canvasHeight = 3600, margin = 72, gap = 48, footer = 440),
    Grid("grid", rows = 2, cols = 2, canvasWidth = 2400, canvasHeight = 3600, margin = 96, gap = 56, footer = 520),
    Trio("trio", rows = 3, cols = 1, canvasWidth = 1200, canvasHeight = 3600, margin = 72, gap = 48, footer = 440),
    Six("six", rows = 3, cols = 2, canvasWidth = 2400, canvasHeight = 3600, margin = 96, gap = 56, footer = 520),
    Solo("solo", rows = 1, cols = 1, canvasWidth = 1800, canvasHeight = 2200, margin = 120, gap = 0, footer = 520),
    ;

    val cuts: Int get() = rows * cols

    companion object {
        val Default = Classic

        fun fromId(id: String?): BoothLayout? = entries.firstOrNull { it.id == id }
    }
}
