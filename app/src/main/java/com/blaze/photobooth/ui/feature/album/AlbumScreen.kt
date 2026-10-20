package com.blaze.photobooth.ui.feature.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.ui.components.BlobShape
import com.blaze.photobooth.ui.components.FavoriteButton
import com.blaze.photobooth.ui.components.SoonAction
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.StripImage
import com.blaze.photobooth.ui.components.StripImageKeys
import com.blaze.photobooth.ui.feature.setup.nameRes
import com.blaze.photobooth.ui.navigation.SharedKeys
import com.blaze.photobooth.ui.navigation.sharedHero
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten
import kotlin.math.min

/**
 * The album (README §4.6): a two-column staggered grid of strips under sticky month headers, the filter chips,
 * ♡ on every card, long-press multi-select with Delete, and a cute empty state. Cards animate in, out and into
 * place (§7 A11), and each card's picture is the A10 hero into Detail.
 */
@Composable
fun AlbumScreen(
    state: AlbumUiState,
    onEvent: (AlbumEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val extra = BoothTheme.extraColors
    // A new filter starts at the top (not on the first composition, so coming back from Detail keeps her place).
    val firstFilter = remember { mutableStateOf(true) }
    LaunchedEffect(state.filter) {
        if (firstFilter.value) firstFilter.value = false else gridState.scrollToItem(0)
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        Column(Modifier.fillMaxSize()) {
            AlbumTopBar(state, onEvent, Modifier.statusBarsPadding())
            when {
                !state.loaded -> Unit
                state.empty -> EmptyAlbum(onStart = { onEvent(AlbumEvent.Start) }, modifier = Modifier.weight(1f))
                else -> {
                    FilterRow(state, onEvent)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (state.shown == 0) {
                            NoMatches(state.filter, Modifier.align(Alignment.Center))
                        } else {
                            AlbumGrid(state, gridState, onEvent)
                            StickyMonthHeader(gridState, state.entries)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.selecting,
            enter = slideInVertically(BoothMotion.gentle()) { it } + fadeIn(),
            exit = slideOutVertically(BoothMotion.gentle()) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectionBar(count = state.selection.size, onDelete = { onEvent(AlbumEvent.DeleteSelected) })
        }

        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private val GridPadding = 16.dp
private val GridTop = 4.dp

// ---- Top bar and chips ----

@Composable
private fun AlbumTopBar(state: AlbumUiState, onEvent: (AlbumEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = BoothTheme.extraColors.raised,
        contentColor = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.selecting) {
            FilledTonalIconButton(onClick = { onEvent(AlbumEvent.ClearSelection) }, colors = colors) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_album_clear_selection))
            }
            Text(
                pluralStringResource(R.plurals.album_selected, state.selection.size, state.selection.size),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .semantics { heading() },
            )
        } else {
            FilledTonalIconButton(onClick = { onEvent(AlbumEvent.Back) }, colors = colors) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(R.string.album_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            // Balances the back button so the title sits centred; shows the count once there are strips.
            Box(Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                if (state.total > 0) {
                    Surface(shape = CircleShape, color = BoothTheme.extraColors.raised, contentColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            state.total.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(min = 20.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: AlbumUiState, onEvent: (AlbumEvent) -> Unit) {
    val haptics = rememberBoothHaptics()
    LazyRow(
        contentPadding = PaddingValues(horizontal = GridPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(state.filters, key = { it.toString() }) { filter ->
            val selected = filter == state.filter
            FilterChip(
                selected = selected,
                onClick = {
                    if (!selected) haptics.segmentTick()
                    onEvent(AlbumEvent.SelectFilter(filter))
                },
                label = { Text(filterLabel(filter)) },
                leadingIcon = if (filter == AlbumFilter.Favorites) {
                    {
                        Icon(
                            if (selected) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = BoothTheme.extraColors.raised,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    iconColor = MaterialTheme.colorScheme.primary,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun filterLabel(filter: AlbumFilter): String = when (filter) {
    AlbumFilter.All -> stringResource(R.string.album_filter_all)
    AlbumFilter.Favorites -> stringResource(R.string.album_filter_favorites)
    is AlbumFilter.Layout -> when {
        filter.herFrames -> stringResource(R.string.setup_her_frames)
        else -> filter.layout?.let { stringResource(it.nameRes) } ?: filter.layoutId
    }
}

// ---- The grid ----

@Composable
private fun AlbumGrid(state: AlbumUiState, gridState: LazyStaggeredGridState, onEvent: (AlbumEvent) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GridPadding, end = GridPadding, top = GridTop, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        for (entry in state.entries) {
            when (entry) {
                is AlbumEntry.Header -> item(key = entry.key, contentType = "header", span = StaggeredGridItemSpan.FullLine) {
                    MonthHeader(AlbumGrouping.monthLabel(entry.month, locale), Modifier.animateItem())
                }
                is AlbumEntry.Strip -> item(key = entry.key, contentType = "strip") {
                    val item = entry.item
                    AlbumCard(
                        item = item,
                        dateLabel = AlbumGrouping.cardDate(item.date, locale),
                        selecting = state.selecting,
                        selected = item.id in state.selection,
                        leaving = item.id in state.leaving,
                        onEvent = onEvent,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** "October 2026" as a little pill. The same pill floats over the grid as the sticky header. */
@Composable
private fun MonthHeader(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
            modifier = Modifier.semantics { heading() },
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}

/**
 * The sticky month header (§4.6): the header of the section at the top of the grid stays pinned there, and the
 * next month's header pushes it up as it arrives. Its position is read in the layout phase, so scrolling never
 * recomposes it; only crossing into another month does.
 */
@Composable
private fun StickyMonthHeader(gridState: LazyStaggeredGridState, entries: List<AlbumEntry>) {
    val index by remember(entries) { derivedStateOf { AlbumGrouping.headerIndexAt(entries, gridState.firstVisibleItemIndex) } }
    val header = index?.let { entries.getOrNull(it) } as? AlbumEntry.Header ?: return
    val next = remember(entries, index) { AlbumGrouping.nextHeaderIndex(entries, index ?: 0) }
    val locale = LocalConfiguration.current.locales[0]
    val topPx = with(LocalDensity.current) { GridTop.roundToPx() }
    var heightPx by remember { mutableIntStateOf(0) }
    MonthHeader(
        AlbumGrouping.monthLabel(header.month, locale),
        Modifier
            .padding(horizontal = GridPadding)
            .onSizeChanged { heightPx = it.height }
            .offset {
                val info = gridState.layoutInfo
                val nextTop = next?.let { n -> info.visibleItemsInfo.firstOrNull { it.index == n } }
                    ?.let { it.offset.y - info.viewportStartOffset }
                val pushed = if (nextTop == null) 0 else min(0, nextTop - (topPx + heightPx))
                IntOffset(0, topPx + pushed)
            }
            .clearAndSetSemantics {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    item: AlbumItem,
    dateLabel: String,
    selecting: Boolean,
    selected: Boolean,
    leaving: Boolean,
    onEvent: (AlbumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberBoothHaptics()
    val colors = MaterialTheme.colorScheme
    // Deleted cards shrink and fade away before the grid closes up (§4.7); selected ones sink a little.
    val gone by animateFloatAsState(if (leaving) 1f else 0f, if (reduceMotion) tween(0) else tween(AlbumViewModel.LeaveMillis.toInt()), label = "leave")
    val press by animateFloatAsState(if (selected) 0.94f else 1f, BoothMotion.snappy(), label = "selected")
    val shape = RoundedCornerShape(18.dp)
    val description = stringResource(
        if (item.favorite) R.string.cd_album_strip_favorite else R.string.cd_album_strip,
        dateLabel,
    )
    val selectLabel = stringResource(R.string.cd_album_select)
    Surface(
        shape = shape,
        color = BoothTheme.extraColors.raised,
        shadowElevation = 3.dp,
        modifier = modifier
            .graphicsLayer {
                val s = press * (1f - 0.4f * gone)
                scaleX = s
                scaleY = s
                alpha = 1f - gone
            }
            .then(if (selected) Modifier.border(3.dp, colors.primary, shape) else Modifier)
            .clip(shape)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (selecting) this.selected = selected
            }
            .combinedClickable(
                onClickLabel = null,
                onLongClickLabel = selectLabel,
                onLongClick = {
                    haptics.segmentTick()
                    onEvent(AlbumEvent.LongPress(item.id))
                },
                onClick = {
                    if (selecting) haptics.segmentTick()
                    onEvent(AlbumEvent.Tap(item.id))
                },
            ),
    ) {
        Column(Modifier.padding(start = 7.dp, end = 7.dp, top = 7.dp)) {
            Box {
                StripImage(
                    file = item.image,
                    memoryKey = StripImageKeys.thumb(item.id, item.version),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(item.aspect)
                        .sharedHero(SharedKeys.strip(item.id))
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.surfaceContainer),
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = selecting,
                    enter = scaleIn(BoothMotion.bouncy()) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) colors.primary else colors.onPrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .shadow(2.dp, CircleShape)
                            .background(if (selected) colors.onPrimary else colors.primary.copy(alpha = 0.45f), CircleShape),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.titleMedium.handwritten(),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                FavoriteButton(
                    favorite = item.favorite,
                    onToggle = { onEvent(AlbumEvent.ToggleFavorite(item.id)) },
                    size = 44.dp,
                )
            }
        }
    }
}

// ---- Selection, empty states ----

/** Multi-select's actions (§4.6) in the thumb zone. Saving to Photos is M6. */
@Composable
private fun SelectionBar(count: Int, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SoonAction(Icons.Rounded.Download, R.string.album_save_selected)
            Spacer(Modifier.weight(1f))
            SquishyButton(
                onClick = onDelete,
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(pluralStringResource(R.plurals.album_delete_selected, count, count), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** §4.6: a camera with a heart, *"No strips yet. Let's make memories!"* and a Start button. */
@Composable
private fun EmptyAlbum(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            BlobShape(
                Modifier.size(168.dp),
                brush = Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)),
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = colors.primary, modifier = Modifier.size(64.dp))
            }
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 10.dp)
                    .size(40.dp)
                    .graphicsLayer { rotationZ = 14f },
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.album_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.album_empty_body),
            style = MaterialTheme.typography.titleLarge.handwritten(),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(28.dp))
        SquishyButton(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(stringResource(R.string.home_start), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(48.dp))
    }
}

/** A filter with nothing in it, e.g. no favourites yet. */
@Composable
private fun NoMatches(filter: AlbumFilter, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(
            stringResource(if (filter == AlbumFilter.Favorites) R.string.album_no_favorites else R.string.album_no_matches),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Preview(name = "Album · empty", widthDp = 411, heightDp = 923)
@Composable
private fun AlbumEmptyPreview() {
    BoothTheme(darkTheme = false) { AlbumScreen(AlbumUiState(loaded = true), onEvent = {}) }
}
