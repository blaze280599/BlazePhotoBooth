package com.blaze.photobooth.ui.feature.pick

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.SlotSelection
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.Filters
import com.blaze.photobooth.ui.components.BoothTabRow
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.StripPreview
import com.blaze.photobooth.ui.feature.setup.FilterThumb
import com.blaze.photobooth.ui.feature.setup.FrameCard
import com.blaze.photobooth.ui.navigation.SharedKeys
import com.blaze.photobooth.ui.navigation.sharedContainer
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import com.blaze.photobooth.ui.theme.handwritten
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The sheet's tabs, in pager order: the tray first (it's where Capture's tray lands, §7 A7), then quick edits. */
enum class PickTab(val label: Int) {
    Cuts(R.string.pick_tab_cuts),
    Frame(R.string.pick_tab_frame),
    Filter(R.string.pick_tab_filter),
    Text(R.string.pick_tab_text),
}

/** §7 A8: shot [shot] flying from the tray into strip slot [slot]; [key] restarts the flight. */
@Immutable
data class PickFlight(val shot: Int, val slot: Int, val key: Int)

/**
 * Pick (README §4.4): the live strip with numbered slots on top, and a sheet with the shot tray and the quick
 * edits (frame, filter, caption and date), then **Print ✿**. [flight] is the shot flying into its slot (A8);
 * each bump of [rejectCount] shakes Print and makes the empty slots blush.
 */
@Composable
fun PickScreen(
    state: PickUiState,
    onEvent: (PickEvent) -> Unit,
    modifier: Modifier = Modifier,
    flight: PickFlight? = null,
    rejectCount: Int = 0,
    initialTab: PickTab = PickTab.Cuts,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val extra = BoothTheme.extraColors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberBoothHaptics()
    val trayBounds = remember { mutableStateMapOf<Int, Rect>() }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }

    val progress = remember { Animatable(1f) }
    LaunchedEffect(flight?.key) {
        if (flight == null || reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f, visibilityThreshold = 0.001f))
        }
    }
    val flyingSlot = flight?.slot?.takeIf { progress.value < 1f }

    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { PickTab.entries.size }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        Column(Modifier.fillMaxSize()) {
            PickTopBar(state, onClose = { onEvent(PickEvent.Close) }, modifier = Modifier.statusBarsPadding())
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val spec = state.preview
                if (spec != null) {
                    PickStrip(
                        spec = spec,
                        state = state,
                        hiddenSlot = flyingSlot,
                        rejectCount = rejectCount,
                        onTapSlot = { onEvent(PickEvent.TapSlot(it)) },
                        onSlotPositioned = { slot, rect -> slotBounds[slot] = rect },
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SheetShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.navigationBarsPadding().padding(top = 14.dp, bottom = 12.dp)) {
                    BoothTabRow(
                        PickTab.entries.map { it.label },
                        pagerState,
                        onTick = haptics::segmentTick,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PageHeight),
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        when (PickTab.entries[page]) {
                            PickTab.Cuts -> CutsPage(state, hidden = flight?.shot?.takeIf { flyingSlot != null }, onEvent) { index, rect ->
                                trayBounds[index] = rect
                            }
                            PickTab.Frame -> FramesPage(state, onEvent)
                            PickTab.Filter -> FiltersPage(state, onEvent)
                            PickTab.Text -> TextPage(state, onEvent)
                        }
                    }
                    PrintButton(
                        ready = state.canPrint,
                        editing = state.editing,
                        printing = state.printing,
                        rejectCount = rejectCount,
                        onClick = { onEvent(PickEvent.Print) },
                    )
                }
            }
        }

        if (flight != null && flyingSlot != null) {
            val shot = state.shots.firstOrNull { it.index == flight.shot }
            val from = trayBounds[flight.shot]
            val to = slotBounds[flight.slot]
            if (shot != null && from != null && to != null) FlyingShot(shot.image, state.filter, from, to, progress.value)
        }

        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 96.dp),
        )

        val peek = state.peek
        AnimatedVisibility(visible = peek != null, enter = fadeIn(), exit = fadeOut()) {
            if (peek != null) PeekOverlay(peek, state.peekImage, state.filter, state.selection.slotOf(peek.index), canRetake = !state.editing, onEvent)
        }
        if (state.printing) PrintingOverlay(state.editing)
    }
}

private val PageHeight = 252.dp

@Composable
private fun PickTopBar(state: PickUiState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onClose,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = BoothTheme.extraColors.raised,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(if (state.editing) R.string.cd_pick_cancel_edit else R.string.cd_pick_discard),
            )
        }
        Text(
            text = stringResource(if (state.editing) R.string.pick_edit_title else R.string.pick_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        val counter = pluralStringResource(R.plurals.pick_counter, state.selection.cuts, state.selection.filled, state.selection.cuts)
        Surface(
            shape = CircleShape,
            color = if (state.selection.complete) MaterialTheme.colorScheme.primary else BoothTheme.extraColors.raised,
            contentColor = if (state.selection.complete) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .widthIn(min = 48.dp)
                .clearAndSetSemantics {
                    contentDescription = counter
                },
        ) {
            Text(
                stringResource(R.string.pick_counter_short, state.selection.filled, state.selection.cuts),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// ---- The strip ----

/**
 * The live strip (§10) with a numbered badge on every slot. Empty slots get a dashed outline and a pulsing "+";
 * tapping a filled slot empties it. [hiddenSlot] stays empty while its shot is still flying in (A8).
 */
@Composable
private fun PickStrip(
    spec: StripSpec,
    state: PickUiState,
    hiddenSlot: Int?,
    rejectCount: Int,
    onTapSlot: (Int) -> Unit,
    onSlotPositioned: (Int, Rect) -> Unit,
) {
    val canvas = spec.canvasSize
    val photos = state.slotPhotos.mapIndexed { i, p -> if (i == hiddenSlot) null else p }
    val description = pluralStringResource(R.plurals.cd_pick_strip, state.selection.cuts, state.selection.filled, state.selection.cuts)
    Box(
        Modifier
            .fillMaxHeight()
            .aspectRatio(canvas.width.toFloat() / canvas.height, matchHeightConstraintsFirst = true)
            .shadow(10.dp, RoundedCornerShape(3.dp)),
    ) {
        StripPreview(spec = spec, photos = photos, contentDescription = description, modifier = Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val scale = with(density) { maxWidth.toPx() } / canvas.width
            spec.slots.forEachIndexed { slot, rect ->
                val filled = state.selection.slots.getOrNull(slot) != null && slot != hiddenSlot
                with(density) {
                    SlotOverlay(
                        number = slot + 1,
                        filled = filled,
                        rejectCount = rejectCount,
                        onTap = { onTapSlot(slot) },
                        modifier = Modifier
                            .offset { IntOffset((rect.x * scale).roundToInt(), (rect.y * scale).roundToInt()) }
                            .size((rect.w * scale).toDp(), (rect.h * scale).toDp())
                            .onGloballyPositioned { onSlotPositioned(slot, it.boundsInRoot()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotOverlay(number: Int, filled: Boolean, rejectCount: Int, onTap: () -> Unit, modifier: Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    // Print with empty slots: the empty ones blush pink for a moment (§13).
    val blush = remember { Animatable(0f) }
    LaunchedEffect(rejectCount) {
        if (rejectCount > 0 && !filled) {
            blush.snapTo(1f)
            blush.animateTo(0f, spring(dampingRatio = 1f, stiffness = 60f))
        }
    }
    val emptyLabel = stringResource(R.string.cd_pick_slot_empty, number)
    val filledLabel = stringResource(R.string.cd_pick_slot_filled, number)
    Box(
        modifier
            .then(
                if (filled) {
                    Modifier
                        .clickable(onClick = onTap, role = Role.Button)
                        .semantics { contentDescription = filledLabel }
                } else {
                    Modifier.clearAndSetSemantics { contentDescription = emptyLabel }
                },
            ),
    ) {
        if (!filled) {
            val pulse = if (reduceMotion) {
                1f
            } else {
                rememberInfiniteTransition(label = "pulse").animateFloat(0.85f, 1.12f, BoothMotion.wobble(), label = "plus").value
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .background(colors.primary.copy(alpha = 0.28f * blush.value), RoundedCornerShape(6.dp))
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .size(30.dp)
                        .background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        NumberBadge(
            number = number,
            filled = filled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
        )
    }
}

/** ①②③④: the slot number, strawberry when filled. */
@Composable
private fun NumberBadge(number: Int, filled: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(22.dp)
            .shadow(2.dp, CircleShape)
            .background(if (filled) colors.primary else BoothTheme.extraColors.raised, CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) colors.onPrimary else colors.primary,
        )
    }
}

// ---- Cuts tab: the shot tray ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CutsPage(state: PickUiState, hidden: Int?, onEvent: (PickEvent) -> Unit, onPositioned: (Int, Rect) -> Unit) {
    val colorFilter = remember(state.filter) { Filters.compose(state.filter) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.pick_tray_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // §7 A7: Capture's tray lands here.
                .sharedContainer(SharedKeys.ShotTray),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // Room for the number badges that sit over each shot's top-right corner.
            contentPadding = PaddingValues(top = 8.dp, end = 6.dp, bottom = 8.dp),
        ) {
            items(state.shots, key = { it.index }) { shot ->
                val slot = state.selection.slotOf(shot.index)
                TrayShot(
                    shot = shot,
                    slot = slot.takeIf { shot.index != hidden },
                    colorFilter = colorFilter,
                    onTap = { onEvent(PickEvent.TapShot(shot.index)) },
                    onLongPress = { onEvent(PickEvent.Peek(shot.index)) },
                    modifier = Modifier.onGloballyPositioned { onPositioned(shot.index, it.boundsInRoot()) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrayShot(
    shot: PickShot,
    slot: Int?,
    colorFilter: ColorFilter?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val placed = slot != null
    val shape = RoundedCornerShape(12.dp)
    val label = if (placed) {
        stringResource(R.string.cd_pick_shot_placed, shot.index + 1, slot + 1)
    } else {
        stringResource(R.string.cd_pick_shot, shot.index + 1)
    }
    val peekLabel = stringResource(R.string.cd_pick_peek)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(shot.aspect.coerceIn(0.7f, 1.6f))
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = peekLabel) { onLongPress(); true }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            ),
    ) {
        Image(
            bitmap = shot.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(colors.surfaceContainer)
                .border(if (placed) 3.dp else 1.dp, if (placed) colors.primary else colors.outline, shape),
        )
        if (shot.bonus) {
            Text(
                stringResource(R.string.pick_bonus),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        AnimatedVisibility(
            visible = placed,
            enter = scaleIn(BoothMotion.bouncy()) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(6.dp, (-6).dp),
        ) {
            NumberBadge(number = (slot ?: 0) + 1, filled = true, modifier = Modifier.size(26.dp))
        }
    }
}

/** §7 A8: the shot's thumbnail glides (with a little lift and tilt) from the tray into its slot. */
@Composable
private fun FlyingShot(image: ImageBitmap, filter: PhotoFilter, from: Rect, to: Rect, t: Float) {
    val density = LocalDensity.current
    val lift = with(density) { 60.dp.toPx() }
    val u = 1f - t
    val control = Offset(lerp(from.center.x, to.center.x, 0.5f), minOf(from.center.y, to.center.y) - lift)
    val center = Offset(
        u * u * from.center.x + 2 * u * t * control.x + t * t * to.center.x,
        u * u * from.center.y + 2 * u * t * control.y + t * t * to.center.y,
    )
    val w = lerp(from.width, to.width, t)
    val h = lerp(from.height, to.height, t)
    val colorFilter = remember(filter) { Filters.compose(filter) }
    with(density) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .offset { IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()) }
                .size(w.toDp(), h.toDp())
                .graphicsLayer { rotationZ = 8f * sin(PI.toFloat() * t) }
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
        )
    }
}

// ---- Quick edits ----

@Composable
private fun FramesPage(state: PickUiState, onEvent: (PickEvent) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup(),
    ) {
        itemsIndexed(state.frames, key = { _, card -> card.option.id.value }) { index, card ->
            val groupStart = index == 0 ||
                (state.frames[index - 1].option is FrameOption.Artwork) !=
                (card.option is FrameOption.Artwork)
            val label = when {
                !groupStart -> null
                card.option is FrameOption.Artwork -> stringResource(R.string.setup_her_frames)
                else -> stringResource(R.string.setup_themes)
            }
            FrameCard(
                card = card,
                groupLabel = label,
                selected = card.option.id == state.frameId,
                onClick = { onEvent(PickEvent.SelectFrame(card.option.id)) },
            )
        }
    }
}

@Composable
private fun FiltersPage(state: PickUiState, onEvent: (PickEvent) -> Unit) {
    // Her own first cut through every filter.
    val sample = state.slotPhotos.firstOrNull { it != null } ?: state.shots.firstOrNull()?.bitmap
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup(),
    ) {
        items(PhotoFilter.entries) { filter ->
            FilterThumb(
                filter = filter,
                sample = sample,
                selected = filter == state.filter,
                onClick = { onEvent(PickEvent.SelectFilter(filter)) },
            )
        }
    }
}

@Composable
private fun TextPage(state: PickUiState, onEvent: (PickEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.textPrints) {
            // §5.5.1: her artwork has its own lettering and no text area, so nothing would print.
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        stringResource(R.string.pick_text_her_frame),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            return@Column
        }
        OutlinedTextField(
            value = state.caption,
            onValueChange = { onEvent(PickEvent.SetCaption(it)) },
            label = { Text(stringResource(R.string.pick_caption)) },
            placeholder = { Text(stringResource(R.string.pick_caption_placeholder), style = MaterialTheme.typography.titleLarge.handwritten()) },
            textStyle = MaterialTheme.typography.titleLarge.handwritten(),
            singleLine = true,
            supportingText = { Text(stringResource(R.string.pick_caption_count, state.caption.length, PickViewModel.MaxCaption)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pick_date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.pick_date_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.showDate, onCheckedChange = { onEvent(PickEvent.SetShowDate(it)) })
        }
    }
}

// ---- Print ----

/**
 * **Print ✿** (§4.4): it looks asleep until every slot is filled. Tapping it early shakes it (§7 A8) and the
 * route plays `REJECT`.
 */
@Composable
private fun PrintButton(ready: Boolean, editing: Boolean, printing: Boolean, rejectCount: Int, onClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(rejectCount) {
        if (rejectCount == 0 || reduceMotion) return@LaunchedEffect
        shake.animateTo(
            0f,
            keyframes {
                durationMillis = ShakeMillis
                -14f at 60
                12f at 130
                -9f at 200
                6f at 270
                -3f at 340
            },
        )
    }
    val density = LocalDensity.current
    SquishyButton(
        onClick = onClick,
        enabled = !printing,
        style = if (ready) SquishyButtonStyle.Primary else SquishyButtonStyle.Tonal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp)
            .height(60.dp)
            .offset { IntOffset(with(density) { shake.value.dp.roundToPx() }, 0) },
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(
            stringResource(
                when {
                    editing && ready -> R.string.pick_save
                    editing -> R.string.pick_save_waiting
                    ready -> R.string.pick_print
                    else -> R.string.pick_print_waiting
                },
            ),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private const val ShakeMillis = 400

// ---- Overlays ----

/** Long-press peek (§4.4): the shot full screen, with Retake (not in Edit). Tap outside (or back) to close. */
@Composable
private fun PeekOverlay(
    shot: PickShot,
    image: ImageBitmap?,
    filter: PhotoFilter,
    slot: Int?,
    canRetake: Boolean,
    onEvent: (PickEvent) -> Unit,
) {
    BackHandler { onEvent(PickEvent.DismissPeek) }
    val colorFilter = remember(filter) { Filters.compose(filter) }
    val closeLabel = stringResource(R.string.cd_pick_peek_close)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
            ) { onEvent(PickEvent.DismissPeek) }
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    slot != null -> stringResource(R.string.pick_peek_title_placed, shot.index + 1, slot + 1)
                    shot.bonus -> stringResource(R.string.pick_peek_title_bonus, shot.index + 1)
                    else -> stringResource(R.string.pick_peek_title, shot.index + 1)
                },
                style = MaterialTheme.typography.titleLarge.handwritten(),
                color = Color.White,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = image ?: shot.image,
                    contentDescription = stringResource(R.string.cd_shot, shot.index + 1),
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(shot.aspect)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A saved session has no camera behind it: Edit re-picks, it doesn't retake (§4.7).
                if (canRetake) {
                    SquishyButton(
                        onClick = { onEvent(PickEvent.Retake(shot.index)) },
                        style = SquishyButtonStyle.Tonal,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.pick_retake), style = MaterialTheme.typography.titleMedium)
                    }
                }
                SquishyButton(
                    onClick = { onEvent(PickEvent.DismissPeek) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.pick_peek_done), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** "Printing your memories…" while the full-size strip renders and the session is saved. */
@Composable
private fun PrintingOverlay(editing: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 6.dp) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(if (editing) R.string.pick_saving else R.string.pick_printing),
                    style = MaterialTheme.typography.titleLarge.handwritten(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Back from Pick (§3): only after confirming. */
@Composable
fun RetakeAllDialog(onStay: () -> Unit, onRetake: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.pick_retake_all_title)) },
        text = { Text(stringResource(R.string.pick_retake_all_body)) },
        confirmButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.pick_retake_all_stay)) } },
        dismissButton = { TextButton(onClick = onRetake) { Text(stringResource(R.string.pick_retake_all_go)) } },
    )
}

/** ✕ on Pick (§3). */
@Composable
fun DiscardSessionDialog(onStay: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.pick_discard_title)) },
        text = { Text(stringResource(R.string.pick_discard_body)) },
        confirmButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.pick_discard_stay)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.pick_discard_go)) } },
    )
}

@Preview(name = "Pick · empty", widthDp = 411, heightDp = 923)
@Composable
private fun PickPreview() {
    BoothTheme(darkTheme = true) {
        PickScreen(
            state = PickUiState(
                loaded = true,
                preview = StripSpec(
                    FrameSource.Theme(FrameTheme.Strawberry),
                    preview = true,
                ),
                selection = SlotSelection(List(4) { null }),
                cuts = 4,
            ),
            onEvent = {},
        )
    }
}
