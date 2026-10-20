package com.blaze.photobooth.ui.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.render.Filters
import com.blaze.photobooth.ui.components.BoothTabRow
import com.blaze.photobooth.ui.components.FavoriteButton
import com.blaze.photobooth.ui.components.SoonAction
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.StripImage
import com.blaze.photobooth.ui.components.StripImageKeys
import com.blaze.photobooth.ui.feature.album.AlbumViewModel
import com.blaze.photobooth.ui.feature.setup.nameRes
import com.blaze.photobooth.ui.navigation.SharedKeys
import com.blaze.photobooth.ui.navigation.sharedHero
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Detail's two views (§4.7). */
enum class DetailTab(val label: Int) {
    Strip(R.string.detail_tab_strip),
    Photos(R.string.detail_tab_photos),
}

/**
 * Detail (README §4.7): the strip as the hero (shared element from its Album card, §7 A10), a *Strip | Photos*
 * toggle (the cuts with the session's filter, then the unused shots), and the actions: ♡ up top, Edit and
 * Delete, plus Save and Share, which arrive in M6. Delete shrinks the strip away.
 */
@Composable
fun DetailScreen(
    state: DetailUiState,
    onEvent: (DetailEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: DetailTab = DetailTab.Strip,
) {
    val extra = BoothTheme.extraColors
    val haptics = rememberBoothHaptics()
    val reduceMotion = LocalReduceMotion.current
    val pager = rememberPagerState(initialPage = initialTab.ordinal) { DetailTab.entries.size }
    val gone by animateFloatAsState(
        if (state.deleting) 1f else 0f,
        if (reduceMotion) tween(0) else tween(AlbumViewModel.LeaveMillis.toInt()),
        label = "delete",
    )
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        DetailTopBar(state, onEvent, Modifier.statusBarsPadding())
        BoothTabRow(
            DetailTab.entries.map { it.label },
            pager,
            onTick = haptics::segmentTick,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        HorizontalPager(
            state = pager,
            // The Photos tab has its own pager; tabs switch by tapping only.
            userScrollEnabled = false,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 1f - 0.4f * gone
                    scaleX = s
                    scaleY = s
                    alpha = 1f - gone
                },
        ) { page ->
            when (DetailTab.entries[page]) {
                DetailTab.Strip -> StripPage(state)
                DetailTab.Photos -> PhotosPage(state)
            }
        }
        ActionBar(onEvent)
    }
}

@Composable
private fun DetailTopBar(state: DetailUiState, onEvent: (DetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { onEvent(DetailEvent.Back) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = BoothTheme.extraColors.raised,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)).orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val frame = when (val f = state.frame) {
                is DetailFrame.Theme -> stringResource(f.theme.nameRes)
                is DetailFrame.Named -> f.name
                DetailFrame.Unknown -> null
            }
            val subtitle = listOfNotNull(frame, stringResource(state.filter.nameRes)).joinToString(" · ")
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.handwritten(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        FavoriteButton(favorite = state.favorite, onToggle = { onEvent(DetailEvent.ToggleFavorite) })
    }
}

/** The strip itself: the hero that flew in from the Album card. */
@Composable
private fun StripPage(state: DetailUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val strip = state.strip ?: return@Box
        StripImage(
            file = strip,
            memoryKey = StripImageKeys.strip(state.sessionId, state.stripVersion),
            placeholderKey = StripImageKeys.thumb(state.sessionId, state.thumbVersion),
            contentDescription = stringResource(R.string.cd_detail_strip),
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(state.aspect, matchHeightConstraintsFirst = true)
                .sharedHero(SharedKeys.strip(state.sessionId))
                .shadow(10.dp, RoundedCornerShape(3.dp)),
        )
    }
}

/**
 * The cuts one by one, with the session's filter (§4.7), then the unused bonus shots, which the row below jumps to.
 */
@Composable
private fun PhotosPage(state: DetailUiState) {
    val photos = state.photos
    if (photos.isEmpty()) return
    val pager = rememberPagerState { photos.size }
    val scope = rememberCoroutineScope()
    val colorFilter = remember(state.filter) { Filters.compose(state.filter) }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val photo = photos[page]
            val unused = page >= state.cuts.size
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (unused) {
                        stringResource(R.string.detail_unused_title, page - state.cuts.size + 1, state.unused.size)
                    } else {
                        stringResource(R.string.detail_cut_title, page + 1, state.cuts.size)
                    },
                    style = MaterialTheme.typography.titleMedium.handwritten(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = photo.file,
                        contentDescription = stringResource(R.string.cd_shot, photo.shotIndex + 1),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(photo.aspect.coerceIn(0.4f, 2.5f))
                            .shadow(6.dp, RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }
        if (state.unused.isNotEmpty()) {
            Text(
                stringResource(R.string.detail_unused_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                itemsIndexed(state.unused, key = { _, p -> p.shotIndex }) { i, photo ->
                    val page = state.cuts.size + i
                    val current = pager.currentPage == page
                    AsyncImage(
                        model = photo.file,
                        contentDescription = stringResource(R.string.cd_detail_unused_shot, photo.shotIndex + 1),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .height(64.dp)
                            .aspectRatio(photo.aspect.coerceIn(0.6f, 1.8f))
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                if (current) 3.dp else 1.dp,
                                if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(role = Role.Button) {
                                scope.launch { pager.animateScrollToPage(page, animationSpec = BoothMotion.gentle()) }
                            },
                    )
                }
            }
        } else {
            PageDots(count = photos.size, current = pager.currentPage)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 10.dp else 7.dp)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/** §4.7's actions. Saving to Photos and sharing are M6, so they wear "Soon" like Result's. */
@Composable
private fun ActionBar(onEvent: (DetailEvent) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                SoonAction(Icons.Rounded.Download, R.string.detail_save_strip, Modifier.weight(1f), width = null)
                SoonAction(Icons.Rounded.Image, R.string.detail_save_photo, Modifier.weight(1f), width = null)
                SoonAction(Icons.Rounded.Collections, R.string.detail_save_all, Modifier.weight(1f), width = null)
                SoonAction(Icons.Rounded.Share, R.string.result_share, Modifier.weight(1f), width = null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SquishyButton(
                    onClick = { onEvent(DetailEvent.Delete) },
                    style = SquishyButtonStyle.Tonal,
                    modifier = Modifier.weight(1f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.detail_delete), style = MaterialTheme.typography.titleMedium)
                }
                SquishyButton(
                    onClick = { onEvent(DetailEvent.Edit) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.detail_edit), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

