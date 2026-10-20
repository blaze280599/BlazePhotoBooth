package com.blaze.photobooth.ui.feature.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.ui.components.Confetti
import com.blaze.photobooth.ui.components.ConfettiPhysics
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.SoonAction
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Result (README §4.5): the strip feeds out of a printer slot in little steps, sways once on a pendulum spring,
 * and confetti bursts (§7 A9). The session is already saved, so the "Saved to your album ♡" chip shows.
 * Saving to Photos and sharing arrive in M6; until then they're shown as "Soon".
 */
@Composable
fun ResultScreen(state: ResultUiState, onEvent: (ResultEvent) -> Unit, modifier: Modifier = Modifier) {
    val extra = BoothTheme.extraColors
    val reduceMotion = LocalReduceMotion.current
    val feed = remember { Animatable(0f) }
    val sway = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    var printed by remember { mutableStateOf(false) }

    LaunchedEffect(state.strip != null) {
        if (state.strip == null) return@LaunchedEffect
        if (reduceMotion) {
            // §6.7: no feed steps, sway or confetti; the strip just fades in.
            feed.animateTo(1f, tween(FadeMillis))
            printed = true
            return@LaunchedEffect
        }
        feed.animateTo(1f, feedSteps())
        printed = true
        coroutineScope {
            launch { sway.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 55f), initialVelocity = SwayKick) }
            launch {
                confetti.animateTo(
                    ConfettiPhysics.DurationSeconds,
                    tween((ConfettiPhysics.DurationSeconds * 1000).toInt(), easing = LinearEasing),
                )
            }
        }
    }

    val confettiColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        extra.mint,
        extra.sky,
        Color(0xFFFFB3C7),
    )
    val particles = remember { ConfettiPhysics.burst(originX = 0.5f, originY = 0.42f) }

    Box(
        modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { onEvent(ResultEvent.Home) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = extra.raised,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_result_home))
                }
                Text(
                    text = stringResource(if (printed) R.string.result_title_done else R.string.result_title_printing),
                    style = MaterialTheme.typography.titleLarge.handwritten(),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 48.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Printer(
                state = state,
                feed = feed.value,
                sway = sway.value,
                fade = reduceMotion,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = printed && state.saved, enter = scaleIn(spring(0.5f, 400f), 0.6f) + fadeIn()) {
                    SavedChip()
                }
            }
            ActionToolbar(onEvent)
        }
        Confetti(
            particles = particles,
            seconds = confetti.value,
            colors = confettiColors,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Eight feeds of the paper: a quick shove, then a pause, like a real booth printer. */
private fun feedSteps() = keyframes {
    durationMillis = FeedSteps * StepMillis
    for (i in 1..FeedSteps) {
        (i - 1f) / FeedSteps at (i - 1) * StepMillis + PauseMillis using EaseOutCubic
        i.toFloat() / FeedSteps at i * StepMillis
    }
}

private const val FeedSteps = 8
private const val StepMillis = 230
private const val PauseMillis = 90
private const val FadeMillis = 400

/** Degrees per second: the nudge that starts the pendulum sway. */
private const val SwayKick = 40f

/**
 * The printer: a dark slot bar with the strip sliding out underneath it, clipped at the slit, swaying from its
 * top edge.
 */
@Composable
private fun Printer(state: ResultUiState, feed: Float, sway: Float, fade: Boolean, modifier: Modifier) {
    val description = stringResource(R.string.cd_result_strip)
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val slotHeight = 30.dp
        val stripHeight = maxHeight - slotHeight / 2 - 8.dp
        val stripWidth: Dp = min(stripHeight * state.aspect, maxWidth - 64.dp)
        val height = stripWidth / state.aspect
        Box(
            Modifier
                .padding(top = slotHeight / 2)
                .width(stripWidth + 40.dp)
                .height(height + 12.dp)
                .clipToBounds(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val strip = state.strip
            if (strip != null) {
                Image(
                    bitmap = strip,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(stripWidth, height)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationZ = sway
                            if (fade) alpha = feed else translationY = -(1f - feed) * size.height
                        }
                        .shadow(8.dp, RoundedCornerShape(2.dp)),
                )
            }
        }
        // The slot bar sits on top of the paper.
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = BoothTheme.extraColors.raised,
            shadowElevation = 6.dp,
            modifier = Modifier
                .width(stripWidth + 64.dp)
                .height(slotHeight)
                .clearAndSetSemantics {},
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun SavedChip() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 2.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.result_saved), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * The floating toolbar (§4.5). Album and Again! work now; saving to Photos and sharing arrive with M6, so they
 * wear a "Soon" tag and are disabled.
 */
@Composable
private fun ActionToolbar(onEvent: (ResultEvent) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SoonAction(Icons.Rounded.Download, R.string.result_save_strip)
                SoonAction(Icons.Rounded.Collections, R.string.result_save_photos)
                SoonAction(Icons.Rounded.Share, R.string.result_share)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SquishyButton(
                    onClick = { onEvent(ResultEvent.Album) },
                    style = SquishyButtonStyle.Tonal,
                    modifier = Modifier.weight(1f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.result_album), style = MaterialTheme.typography.titleMedium)
                }
                SquishyButton(
                    onClick = { onEvent(ResultEvent.Again) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.result_again), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
