package com.blaze.photobooth.ui.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.ui.components.BlobLogo
import com.blaze.photobooth.ui.components.FloatingParticles
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.StripImage
import com.blaze.photobooth.ui.components.StripImageKeys
import com.blaze.photobooth.ui.components.boothParticleColors
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten
import kotlin.math.PI
import kotlin.math.sin

/**
 * Home (README §4.1): gradient sky with floating particles, the morphing blob logo, the bobbing
 * wordmark and tagline, then Start ✿ and My Album in the thumb zone. The badge, strip peek and
 * draft card appear only when [state] has data for them.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = BoothTheme.extraColors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        FloatingParticles(colors = boothParticleColors(), modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = { onEvent(HomeEvent.OpenSettings) },
                modifier = Modifier.align(Alignment.End),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = BoothTheme.extraColors.raised,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings))
            }
            Spacer(Modifier.weight(1f))
            BlobLogo()
            Spacer(Modifier.height(28.dp))
            BobbingWordmark(stringResource(R.string.app_name))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.titleLarge.handwritten(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1.3f))
            AnimatedVisibility(
                visible = state.draft != null,
                enter = slideInVertically(BoothMotion.bouncy()) { it / 2 } + fadeIn(BoothMotion.gentle()),
                exit = slideOutVertically(BoothMotion.gentle()) { it / 2 } + fadeOut(BoothMotion.gentle()),
            ) {
                DraftCard(
                    shotCount = state.draft?.shotCount ?: 0,
                    onContinue = { onEvent(HomeEvent.ContinueDraft) },
                    onDiscard = { onEvent(HomeEvent.DiscardDraft) },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            SquishyButton(
                onClick = { onEvent(HomeEvent.Start) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Text(stringResource(R.string.home_start), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(14.dp))
            AlbumButton(
                albumCount = state.albumCount,
                recentStrip = state.recentStrip,
                onOpenAlbum = { onEvent(HomeEvent.OpenAlbum) },
                onOpenStrip = { onEvent(HomeEvent.OpenStrip(it)) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** "Blaze Booth" in Fredoka SemiBold, each letter bobbing on its own phase (§7 A2). */
@Composable
private fun BobbingWordmark(text: String, modifier: Modifier = Modifier) {
    val phase: State<Float> = if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "wordmark").animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(BoothMotion.BobMillis, easing = LinearEasing)),
            label = "bob",
        )
    }
    val amplitude = with(LocalDensity.current) { 4.dp.toPx() }
    val style = MaterialTheme.typography.displayMedium.copy(
        shadow = Shadow(MaterialTheme.colorScheme.primaryContainer, offset = Offset(0f, amplitude), blurRadius = amplitude),
    )
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = text
            heading()
        },
    ) {
        text.forEachIndexed { index, letter ->
            Text(
                text = letter.toString(),
                style = style,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    translationY = sin(phase.value - index * LetterPhaseStep) * amplitude
                },
            )
        }
    }
}

private const val LetterPhaseStep = 0.55f

/** My Album, with a count badge and the newest strip tucked behind it at −8° (§4.1). */
@Composable
private fun AlbumButton(
    albumCount: Int,
    recentStrip: RecentStrip?,
    onOpenAlbum: () -> Unit,
    onOpenStrip: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (recentStrip != null) PeekVisibleHeight else 0.dp),
    ) {
        if (recentStrip != null) {
            StripPeek(
                strip = recentStrip,
                onClick = { onOpenStrip(recentStrip.sessionId) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 36.dp)
                    .offset(y = -PeekVisibleHeight)
                    .rotate(-8f),
            )
        }
        SquishyButton(
            onClick = onOpenAlbum,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            style = SquishyButtonStyle.Tonal,
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.home_album), style = MaterialTheme.typography.titleMedium)
            if (albumCount > 0) {
                val description = pluralStringResource(R.plurals.cd_album_count, albumCount, albumCount)
                Badge(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clearAndSetSemantics { contentDescription = description },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(if (albumCount > 99) "99+" else albumCount.toString())
                }
            }
        }
    }
}

private val PeekVisibleHeight = 44.dp

/**
 * A mini strip card, short enough that its tilted bottom stays hidden behind the 56 dp Album button.
 * Shows the real thumbnail when there is one, over a pastel four-cut placeholder.
 */
@Composable
private fun StripPeek(strip: RecentStrip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_recent_strip)
    Surface(
        modifier = modifier
            .size(width = 58.dp, height = PeekVisibleHeight + 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
    ) {
        Box {
            Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val cells = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                    BoothTheme.extraColors.sky,
                )
                cells.forEach { color ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(25.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
            }
            if (strip.thumbnail != null) {
                StripImage(
                    file = strip.thumbnail,
                    memoryKey = StripImageKeys.thumb(strip.sessionId, strip.version),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Snackbar-style card offering to continue an unfinished session (§4.1, §11.4). */
@Composable
private fun DraftCard(shotCount: Int, onContinue: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.home_draft_message, shotCount, shotCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.home_draft_discard), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.home_draft_continue), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

@Preview(name = "Home · light", widthDp = 411, heightDp = 923)
@Composable
private fun HomeLightPreview() {
    BoothTheme(darkTheme = false) { HomeScreen(HomeUiState(), onEvent = {}) }
}

@Preview(name = "Home · dark", widthDp = 411, heightDp = 923)
@Composable
private fun HomeDarkPreview() {
    BoothTheme(darkTheme = true) { HomeScreen(HomeUiState(), onEvent = {}) }
}

/** What Home will look like once the album and draft store exist (M3/M5). */
@Preview(name = "Home · with album and draft", widthDp = 411, heightDp = 923)
@Composable
private fun HomeFilledPreview() {
    BoothTheme(darkTheme = false) { HomeScreen(SampleFilledHomeState, onEvent = {}) }
}

internal val SampleFilledHomeState = HomeUiState(
    albumCount = 12,
    recentStrip = RecentStrip(sessionId = "sample", thumbnail = null),
    draft = DraftSummary(shotCount = 3),
)
