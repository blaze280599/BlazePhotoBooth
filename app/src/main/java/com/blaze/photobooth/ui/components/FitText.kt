package com.blaze.photobooth.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A label in a fixed-size spot (a tab, a segmented button, the name under a card). With big fonts (README §6.7) it
 * grows like any text, but if it no longer fits in [maxLines] it shrinks just enough to fit, never below the size it
 * has at 100% font, so nothing is clipped and it is never smaller than normal.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = TextAlign.Center,
    maxLines: Int = 1,
) {
    val fontScale = LocalDensity.current.fontScale
    val max = style.fontSize
    val min = (max.value / fontScale.coerceAtLeast(1f)).sp
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (fontScale > 1f) WholeWords(min = min, max = max) else null,
    )
}

/**
 * The largest size from [max] down to [min] (0.5 sp steps) at which the text fits without overflowing **and** without
 * breaking a word across lines ("Ikmyeon / g Night"). If none does, [min] (the 100% size, which always fitted before).
 */
private data class WholeWords(val min: TextUnit, val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && !FontScaling.splitsAWord(text.text, (0 until layout.lineCount - 1).map { layout.getLineEnd(it) })) {
                return size.sp
            }
            size -= 0.5f
        }
        return min
    }
}

/**
 * [size] as it looks at 100% font, whatever the font scale: for a digit inside a fixed-size badge circle, which would
 * otherwise spill out of it with big fonts. The badge's meaning reaches TalkBack through its slot or shot.
 */
@Composable
@ReadOnlyComposable
fun TextUnit.fixedScale(): TextUnit = (value / LocalDensity.current.fontScale).sp
