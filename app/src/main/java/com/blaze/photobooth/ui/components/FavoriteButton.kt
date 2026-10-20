package com.blaze.photobooth.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.ui.theme.BoothTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ♡ favourite toggle (README §4.6, §7 A11). Favouriting pops the heart and fires a mini burst of hearts and dots
 * with a `CONFIRM` haptic (§6.5); un-favouriting just pops it back. With reduce motion it only switches.
 */
@Composable
fun FavoriteButton(
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberBoothHaptics()
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    // 0 → 1 while the burst flies; 1 = nothing to draw.
    val burst = remember { Animatable(1f) }
    val heart = remember { heartPath() }
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, BoothTheme.extraColors.mint)
    val description = stringResource(R.string.cd_favorite)

    Box(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .toggleable(
                value = favorite,
                interactionSource = null,
                indication = ripple(bounded = false, radius = size / 2),
                role = Role.Switch,
            ) { on ->
                if (on) haptics.confirm() else haptics.segmentTick()
                if (!reduceMotion) {
                    scope.launch {
                        pop.snapTo(if (on) 0.55f else 0.8f)
                        pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
                    }
                    if (on) {
                        scope.launch {
                            burst.snapTo(0f)
                            burst.animateTo(1f, tween(BurstMillis, easing = LinearOutSlowInEasing))
                        }
                    }
                }
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val t = burst.value
            if (t >= 1f) return@Canvas
            val reach = this.size.minDimension * (0.28f + 0.62f * t)
            val alpha = (1f - t).coerceIn(0f, 1f)
            for (i in 0 until BurstCount) {
                val a = (i * 360f / BurstCount - 90f + 22f) * PI.toFloat() / 180f
                val p = center + Offset(cos(a), sin(a)) * reach
                val color = colors[i % colors.size].copy(alpha = alpha)
                if (i % 2 == 0) {
                    val s = this.size.minDimension * 0.2f * (1f - 0.4f * t)
                    translate(p.x, p.y) { scale(s, s, Offset.Zero) { drawPath(heart, color) } }
                } else {
                    drawCircle(color, this.size.minDimension * 0.045f * (1f - 0.5f * t), p)
                }
            }
        }
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size * 0.54f)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

private const val BurstCount = 8
private const val BurstMillis = 520
