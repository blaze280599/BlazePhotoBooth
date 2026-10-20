package com.blaze.photobooth.render

import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.Decoration
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.SlotRect
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

enum class StickerKind { Heart, Cloud, Clover, Star, Moon, Daisy, Petal, Sparkle }

/** One placed sticker, in canvas pixels. [size] is the side of its square bounding box. */
data class Sticker(
    val kind: StickerKind,
    val cx: Float,
    val cy: Float,
    val size: Float,
    val rotation: Float,
    val colorIndex: Int,
)

/**
 * Seeded sticker scatter for theme frames (README §5.2). Pure Kotlin and deterministic:
 * the seed is the theme id plus the layout id, so preview and export place every sticker identically.
 *
 * Stickers live in the margins, gaps and footer. A sticker may overlap a photo cell's edge by at
 * most [MaxCellOverlap] px, and never covers the footer text block, so faces and lettering stay clear.
 */
object StickerLayout {

    const val MaxCellOverlap = 12f

    private val cache = ConcurrentHashMap<Pair<FrameTheme, BoothLayout>, List<Sticker>>()

    fun place(theme: FrameTheme, layout: BoothLayout): List<Sticker> =
        cache.getOrPut(theme to layout) { compute(theme, layout) }

    /** The footer area kept free for the wordmark, caption and date. */
    fun footerTextBlock(layout: BoothLayout): SlotRect {
        val footer = LayoutGeometry.footer(layout)
        val w = min(layout.canvasWidth * 0.64f, footer.h * 2.2f)
        return SlotRect((layout.canvasWidth - w) / 2f, footer.y + footer.h * 0.1f, w, footer.h * 0.8f)
    }

    /** How deep [box] reaches into [rect]: 0 when apart, else the smaller of the two overlap extents. */
    fun penetration(box: SlotRect, rect: SlotRect): Float {
        val ix = min(box.right, rect.right) - maxOf(box.x, rect.x)
        val iy = min(box.bottom, rect.bottom) - maxOf(box.y, rect.y)
        return if (ix <= 0f || iy <= 0f) 0f else min(ix, iy)
    }

    private fun kindsFor(decoration: Decoration): List<StickerKind> = when (decoration) {
        Decoration.None, Decoration.Sprockets -> emptyList()
        Decoration.Sparkles -> listOf(StickerKind.Sparkle)
        Decoration.Hearts -> listOf(StickerKind.Heart, StickerKind.Heart, StickerKind.Heart, StickerKind.Sparkle)
        Decoration.CloudsAndStars -> listOf(StickerKind.Cloud, StickerKind.Cloud, StickerKind.Star)
        Decoration.Clovers -> listOf(StickerKind.Clover, StickerKind.Clover, StickerKind.Sparkle)
        Decoration.StarsAndMoons -> listOf(StickerKind.Star, StickerKind.Star, StickerKind.Moon, StickerKind.Sparkle)
        Decoration.Daisies -> listOf(StickerKind.Daisy, StickerKind.Daisy, StickerKind.Sparkle)
        Decoration.Petals -> listOf(StickerKind.Petal)
    }

    private fun compute(theme: FrameTheme, layout: BoothLayout): List<Sticker> {
        val kinds = kindsFor(theme.decoration)
        if (kinds.isEmpty()) return emptyList()
        val random = Random("${theme.id}:${layout.id}".hashCode())
        val cells = LayoutGeometry.cells(layout)
        val footer = LayoutGeometry.footer(layout)
        val textBlock = footerTextBlock(layout)
        val tiny = theme.decoration == Decoration.Sparkles
        val target = (layout.canvasWidth + layout.canvasHeight) / if (tiny) 200 else 260
        val base = layout.margin.toFloat()
        val placed = ArrayList<Sticker>(target)

        // Footer first: a few big stickers either side of the text, the way booth strips decorate their foot.
        val footerTarget = if (tiny) 0 else if (layout.cols > 1 || layout == BoothLayout.Solo) 4 else 3
        var attempts = 0
        while (placed.count { it.cy > footer.y } < footerTarget && attempts < MaxAttempts / 3) {
            attempts++
            tryPlace(random, kinds, layout, footer.y + random.nextFloat() * footer.h, base, tiny, cells, footer, textBlock, placed, theme)
        }
        attempts = 0
        while (placed.size < target && attempts < MaxAttempts) {
            attempts++
            tryPlace(random, kinds, layout, random.nextFloat() * layout.canvasHeight, base, tiny, cells, footer, textBlock, placed, theme)
        }
        return placed
    }

    private fun tryPlace(
        random: Random,
        kinds: List<StickerKind>,
        layout: BoothLayout,
        cy: Float,
        base: Float,
        tiny: Boolean,
        cells: List<SlotRect>,
        footer: SlotRect,
        textBlock: SlotRect,
        placed: MutableList<Sticker>,
        theme: FrameTheme,
    ) {
        val kind = kinds[random.nextInt(kinds.size)]
        val cx = random.nextFloat() * layout.canvasWidth
        val inFooter = cy > footer.y
        val scale = when {
            tiny || kind == StickerKind.Sparkle -> 0.45f + random.nextFloat() * 0.4f
            inFooter -> 1.4f + random.nextFloat() * 0.8f
            else -> 0.8f + random.nextFloat() * 0.6f
        }
        val size = base * scale
        val box = SlotRect(cx - size / 2f, cy - size / 2f, size, size)

        // At most 30% of a sticker may bleed off the canvas edge.
        val bleed = size * 0.3f
        if (box.x < -bleed || box.y < -bleed || box.right > layout.canvasWidth + bleed ||
            box.bottom > layout.canvasHeight + bleed
        ) return
        if (cells.any { penetration(box, it) > MaxCellOverlap }) return
        if (penetration(box, textBlock) > 0f) return
        if (placed.any { hypot(it.cx - cx, it.cy - cy) < (it.size + size) * 0.5f }) return

        placed += Sticker(
            kind = kind,
            cx = cx,
            cy = cy,
            size = size,
            rotation = random.nextFloat() * 50f - 25f,
            colorIndex = random.nextInt(maxOf(1, theme.stickerColors.size)),
        )
    }

    private const val MaxAttempts = 6000
}
