package com.blaze.photobooth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Big fonts (README §6.7, §13: usable at 200%). A height designed at 100% font that grows with the font scale,
 * for panes whose height must be fixed (a pager inside a sheet): [textShare] is roughly how much of it is text.
 * The strip preview above such a pane takes what's left, so it shrinks before controls overflow.
 */
@Composable
@ReadOnlyComposable
fun Dp.growWithFont(textShare: Float = 0.4f): Dp = this * FontScaling.growth(LocalDensity.current.fontScale, textShare)

/** True when the font is big enough (≥ 150%) that side-by-side label + control rows should stack instead. */
@Composable
@ReadOnlyComposable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= FontScaling.LargeFontScale

/** The maths, pure so it's unit-tested. */
object FontScaling {
    const val LargeFontScale = 1.5f
    private const val MaxScale = 2f

    /** 1 at 100% font; grows linearly with the text's share up to 200%, and no further. */
    fun growth(fontScale: Float, textShare: Float): Float = 1f + textShare.coerceIn(0f, 1f) * (fontScale.coerceIn(1f, MaxScale) - 1f)

    /** True if a line ending at one of [lineEnds] (character offsets) cuts a word of [text] in two. */
    fun splitsAWord(text: String, lineEnds: List<Int>): Boolean =
        lineEnds.any { end -> end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit() }
}
