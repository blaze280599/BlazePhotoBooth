package com.blaze.photobooth.render

import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.SlotRect

/**
 * Cell maths for the built-in layouts (README §5.1), in export pixels:
 * `cellW = (W − 2·margin − (cols−1)·gap) / cols`, `cellH = (H − margin − footer − (rows−1)·gap) / rows`.
 * The footer sits below the last row and includes the bottom edge.
 */
object LayoutGeometry {

    private val cache = BoothLayout.entries.associateWith { compute(it) }

    /** Cells in row-major order (row by row, left to right). */
    fun cells(layout: BoothLayout): List<SlotRect> = cache.getValue(layout)

    fun cellWidth(layout: BoothLayout): Float = with(layout) {
        (canvasWidth - 2f * margin - (cols - 1) * gap) / cols
    }

    fun cellHeight(layout: BoothLayout): Float = with(layout) {
        (canvasHeight - margin - footer - (rows - 1f) * gap) / rows
    }

    /** The footer band (wordmark, caption, date). */
    fun footer(layout: BoothLayout): SlotRect = with(layout) {
        SlotRect(0f, (canvasHeight - footer).toFloat(), canvasWidth.toFloat(), footer.toFloat())
    }

    private fun compute(layout: BoothLayout): List<SlotRect> {
        val w = cellWidth(layout)
        val h = cellHeight(layout)
        return buildList {
            for (row in 0 until layout.rows) {
                for (col in 0 until layout.cols) {
                    add(SlotRect(layout.margin + col * (w + layout.gap), layout.margin + row * (h + layout.gap), w, h))
                }
            }
        }
    }
}
