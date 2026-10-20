package com.blaze.photobooth.render

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.screenshots.TestArt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * README §13: the longest caption (24 of the widest letter) shrinks to fit the footer's text column, so it never runs
 * off the strip or into the stickers, on every layout. Milk has no stickers, so every dark pixel in the footer is text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionFitTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun theLongestCaptionStaysInsideTheTextColumn() {
        for (layout in BoothLayout.entries) {
            val spec = StripSpec(
                frame = FrameSource.Theme(FrameTheme.Milk),
                layout = layout,
                caption = "W".repeat(24),
                date = LocalDate.of(2026, 10, 20),
            )
            val strip = TestArt.render(context, spec, List(layout.cuts) { null }, scale = 1f)
            val footer = LayoutGeometry.footer(layout)
            val column = StickerLayout.footerTextBlock(layout)
            var inked = 0
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            for (y in footer.y.toInt() until footer.bottom.toInt().coerceAtMost(strip.height)) {
                for (x in 0 until strip.width) {
                    val c = strip.getPixel(x, y)
                    if (Color.red(c) < 140 && Color.green(c) < 140 && Color.blue(c) < 140) {
                        inked++
                        minX = minOf(minX, x)
                        maxX = maxOf(maxX, x)
                    }
                }
            }
            assertTrue("${layout.id}: the footer has text", inked > 0)
            assertTrue("${layout.id}: text starts at $minX, column at ${column.x}", minX >= column.x - 2)
            assertTrue("${layout.id}: text ends at $maxX, column at ${column.right}", maxX <= column.right + 2)
        }
    }
}
