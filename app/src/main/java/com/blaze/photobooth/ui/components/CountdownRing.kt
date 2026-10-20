package com.blaze.photobooth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.ui.theme.BoothMotion
import androidx.compose.ui.graphics.StrokeCap

/**
 * The booth countdown (README §4.3, §7 A5): a giant number that pops in on the Bouncy spring while a ring
 * drains around it. On the last second the ring turns pink. The number is announced politely to TalkBack.
 * Sized to fit its box (up to 160 sp) so it works in every slot shape.
 */
@Composable
fun CountdownRing(secondsLeft: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val total = totalSeconds.coerceAtLeast(1)
    val progress = remember { Animatable(secondsLeft.toFloat() / total) }
    LaunchedEffect(secondsLeft, total) {
        progress.snapTo(secondsLeft.toFloat() / total)
        progress.animateTo((secondsLeft - 1).toFloat() / total, tween(TickMillis, easing = LinearEasing))
    }
    val last = secondsLeft <= 1
    val ringColor by animateColorAsState(if (last) MaterialTheme.colorScheme.primary else Color.White, label = "ring")

    BoxWithConstraints(
        modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = secondsLeft.toString()
        },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = min(min(maxWidth, maxHeight) * 0.78f, MaxDiameter)
        val stroke = with(LocalDensity.current) { (diameter * 0.055f).toPx() }
        Canvas(Modifier.size(diameter)) {
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawCircle(Color.Black.copy(alpha = 0.22f), radius = size.minDimension / 2f - inset)
            drawCircle(Color.White.copy(alpha = 0.3f), radius = size.minDimension / 2f - inset, style = Stroke(stroke))
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        val fontSize = with(LocalDensity.current) { (diameter * 0.6f).toSp() }.value.coerceAtMost(MaxFontSp).sp
        AnimatedContent(
            targetState = secondsLeft,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(FadeMillis)) togetherWith fadeOut(tween(FadeMillis))
                } else {
                    (scaleIn(BoothMotion.bouncy(), initialScale = 0.3f) + fadeIn(tween(FadeMillis))) togetherWith
                        (scaleOut(tween(FadeMillis), targetScale = 1.5f) + fadeOut(tween(FadeMillis)))
                }
            },
            label = "countdown",
        ) { s ->
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = s.toString(),
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (s <= 1) MaterialTheme.colorScheme.primary else Color.White,
                    style = MaterialTheme.typography.displayLarge.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 6f), blurRadius = 18f),
                    ),
                )
            }
        }
    }
}

private const val TickMillis = 1_000
private const val FadeMillis = 160
private const val MaxFontSp = 160f
private val MaxDiameter = 240.dp
