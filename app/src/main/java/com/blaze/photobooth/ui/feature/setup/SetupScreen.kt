package com.blaze.photobooth.ui.feature.setup

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.BackFlash
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.Filters
import com.blaze.photobooth.render.LayoutGeometry
import com.blaze.photobooth.ui.components.BoothTabRow
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.StripPreview
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape

/** The four sheet tabs, in pager order. */
enum class SetupTab(val label: Int) {
    Layout(R.string.setup_tab_layout),
    Frame(R.string.setup_tab_frame),
    Filter(R.string.setup_tab_filter),
    Options(R.string.setup_tab_options),
}

/**
 * Setup (README §4.2): the live strip preview on top, an always-expanded sheet with swipeable
 * Layout · Frame · Filter · Options tabs, and the sticky "Let's go! · N shots" button.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: SetupTab = SetupTab.Layout,
) {
    val extra = BoothTheme.extraColors
    val haptics = rememberBoothHaptics()
    // Every selection ticks (§6.5); the other events are navigation.
    val send: (SetupEvent) -> Unit = { event ->
        if (event !is SetupEvent.Back && event !is SetupEvent.Start && event !is SetupEvent.AddFrame) haptics.segmentTick()
        onEvent(event)
    }
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { SetupTab.entries.size }

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        PreviewArea(
            state = state,
            onBack = { onEvent(SetupEvent.Back) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SheetShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 14.dp, bottom = 12.dp)) {
                BoothTabRow(SetupTab.entries.map { it.label }, pagerState, onTick = haptics::segmentTick, modifier = Modifier.padding(horizontal = 20.dp))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PageHeight),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    when (SetupTab.entries[page]) {
                        SetupTab.Layout -> LayoutPage(state, send)
                        SetupTab.Frame -> FramePage(state, send)
                        SetupTab.Filter -> FilterPage(state, send)
                        SetupTab.Options -> OptionsPage(state.config, send)
                    }
                }
                SquishyButton(
                    onClick = { onEvent(SetupEvent.Start) },
                    enabled = state.ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(60.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.setup_cta, state.totalShots, state.totalShots),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

private val PageHeight = 232.dp

// ---- Preview ----

@Composable
private fun PreviewArea(state: SetupUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    Box(modifier) {
        val spec = state.preview
        if (spec != null) {
            val description = stringResource(
                R.string.cd_strip_preview,
                state.selectedFrame?.displayName().orEmpty(),
                pluralStringResource(R.plurals.setup_cuts, state.cuts, state.cuts),
            )
            AnimatedContent(
                targetState = spec,
                contentKey = { "${it.frame.id}|${it.layout}|${it.filter}" },
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(BoothMotion.gentle()) togetherWith fadeOut(BoothMotion.gentle())
                    } else {
                        (fadeIn(BoothMotion.gentle()) + scaleIn(BoothMotion.bouncy(), initialScale = 0.92f))
                            .togetherWith(fadeOut(BoothMotion.snappy()) + scaleOut(BoothMotion.snappy(), targetScale = 1.04f))
                    }
                },
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 20.dp),
                label = "preview",
            ) { target ->
                StripPreview(
                    spec = target,
                    contentDescription = description,
                    modifier = Modifier
                        .fillMaxHeight()
                        .shadow(10.dp, RoundedCornerShape(3.dp)),
                )
            }
        }
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = BoothTheme.extraColors.raised,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .semantics { heading() },
        )
    }
}

// ---- Layout tab ----

@Composable
private fun LayoutPage(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    val locked = state.layoutSetByFrame
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        if (locked) {
            val frameName = state.selectedFrame?.displayName().orEmpty()
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.setup_set_by_frame), style = MaterialTheme.typography.titleSmall)
                    Text(
                        pluralStringResource(R.plurals.setup_set_by_frame_body, state.cuts, frameName, state.cuts),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .selectableGroup()
                .alpha(if (locked) 0.4f else 1f),
        ) {
            itemsIndexed(BoothLayout.entries) { _, layout ->
                LayoutChip(
                    layout = layout,
                    selected = !locked && layout == state.config.layout,
                    enabled = !locked,
                    compact = locked,
                    onClick = { onEvent(SetupEvent.SelectLayout(layout)) },
                )
            }
        }
    }
}

@Composable
private fun LayoutChip(layout: BoothLayout, selected: Boolean, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, BoothMotion.bouncy(), label = "chip")
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier
            .padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(96.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MiniLayoutDiagram(layout, Modifier.size(width = 56.dp, height = if (compact) 44.dp else 80.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(layout.nameRes),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.onSurface,
            )
            // While a frame sets the layout, the chips are only a dimmed reminder, so they stay short.
            if (!compact) {
                Text(
                    pluralStringResource(R.plurals.setup_cuts, layout.cuts, layout.cuts),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** The layout's real cell geometry, drawn tiny (the same [LayoutGeometry] the strip uses). */
@Composable
private fun MiniLayoutDiagram(layout: BoothLayout, modifier: Modifier = Modifier) {
    val paper = MaterialTheme.colorScheme.surfaceContainerHigh
    val cell = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.clearAndSetSemantics {}) {
        val s = minOf(size.width / layout.canvasWidth, size.height / layout.canvasHeight)
        val w = layout.canvasWidth * s
        val h = layout.canvasHeight * s
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        drawRoundRect(paper, Offset(left, top), Size(w, h), CornerRadius(3.dp.toPx()))
        drawRoundRect(outline, Offset(left, top), Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
        for (r in LayoutGeometry.cells(layout)) {
            drawRoundRect(cell, Offset(left + r.x * s, top + r.y * s), Size(r.w * s, r.h * s), CornerRadius(2.dp.toPx()))
        }
    }
}

// ---- Frame tab ----

@Composable
private fun FramePage(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    val selectedIndex = state.frames.indexOfFirst { it.option.id == state.config.frameId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup(),
    ) {
        itemsIndexed(state.frames, key = { _, card -> card.option.id.value }) { index, card ->
            val groupStart = index == 0 || (state.frames[index - 1].option is FrameOption.Artwork) != (card.option is FrameOption.Artwork)
            val label = when {
                !groupStart -> null
                card.option is FrameOption.Artwork -> stringResource(R.string.setup_her_frames)
                else -> stringResource(R.string.setup_themes)
            }
            FrameCard(
                card = card,
                groupLabel = label,
                selected = card.option.id == state.config.frameId,
                onClick = { onEvent(SetupEvent.SelectFrame(card.option.id)) },
            )
        }
        item(key = "add") { AddFrameCard(onClick = { onEvent(SetupEvent.AddFrame) }) }
    }
}

private val CardWidth = 84.dp
private val CardHeight = 140.dp

@Composable
private fun GroupLabel(text: String?) {
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .height(20.dp)
            .then(if (text == null) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }),
    )
}

@Composable
internal fun FrameCard(card: FrameCardState, groupLabel: String?, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, BoothMotion.bouncy(), label = "card")
    val colors = MaterialTheme.colorScheme
    val swatch = remember(card) { swatchSpec(card.option, card.thumbnail) }
    Column(Modifier.width(CardWidth)) {
        GroupLabel(groupLabel)
        Box(
            Modifier
                .padding(top = 6.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
                modifier = Modifier.size(CardWidth, CardHeight),
            ) {
                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    if (swatch != null) StripPreview(swatch, Modifier.fillMaxHeight())
                }
            }
            if (card.option is FrameOption.Artwork) HerFrameBadge(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp))
            SelectedSticker(selected, Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp))
        }
        Text(
            text = card.option.displayName(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** A tiny rendered frame: themes on the classic strip, image frames from their thumbnail. */
private fun swatchSpec(option: FrameOption, thumbnail: Bitmap?): StripSpec? = when (option) {
    is FrameOption.Theme -> StripSpec(FrameSource.Theme(option.theme), BoothLayout.Classic, preview = true)
    is FrameOption.Artwork -> thumbnail?.let { StripSpec(FrameSource.Image(option.frame, it), preview = true) }
}

@Composable
private fun HerFrameBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_her_frame)
    Box(
        modifier
            .size(26.dp)
            .shadow(2.dp, CircleShape)
            .background(BoothTheme.extraColors.raised, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

/** The ✓ sticker on the selected card: pops in with a bounce, then wobbles gently (§7 A4). */
@Composable
private fun SelectedSticker(visible: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(BoothMotion.bouncy()) + fadeIn(),
        exit = scaleOut(BoothMotion.snappy()) + fadeOut(),
        modifier = modifier,
    ) {
        val wobble: State<Float> = if (reduceMotion) {
            remember { mutableFloatStateOf(0f) }
        } else {
            rememberInfiniteTransition(label = "wobble").animateFloat(-9f, 9f, BoothMotion.wobble(), label = "tilt")
        }
        val description = stringResource(R.string.cd_selected)
        Box(
            Modifier
                .graphicsLayer { rotationZ = wobble.value }
                .size(28.dp)
                .shadow(3.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(2.dp, BoothTheme.extraColors.raised, CircleShape)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddFrameCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.width(CardWidth)) {
        GroupLabel(null)
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(CardWidth, CardHeight)
                .clip(MaterialTheme.shapes.small)
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = colors.primary,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                    )
                }
                .selectable(selected = false, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
        }
        Text(
            text = stringResource(R.string.setup_add_frame),
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

// ---- Filter tab ----

@Composable
private fun FilterPage(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup(),
    ) {
        itemsIndexed(PhotoFilter.entries) { _, filter ->
            FilterThumb(
                filter = filter,
                sample = state.filterSample,
                selected = filter == state.config.filter,
                onClick = { onEvent(SetupEvent.SelectFilter(filter)) },
            )
        }
    }
}

@Composable
internal fun FilterThumb(filter: PhotoFilter, sample: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, BoothMotion.bouncy(), label = "filter")
    val colors = MaterialTheme.colorScheme
    val image = remember(sample) { sample?.asImageBitmap() }
    Column(
        Modifier
            .width(80.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(76.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape)
                .padding(if (selected) 5.dp else 3.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = Filters.compose(filter),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            stringResource(filter.nameRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ---- Options tab ----

@Composable
private fun OptionsPage(config: BoothConfig, onEvent: (SetupEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OptionRow(stringResource(R.string.setup_timer)) {
            Segmented(
                values = BoothConfig.TimerChoices,
                selected = config.timerSec,
                label = { stringResource(R.string.setup_timer_value, it) },
                onSelect = { onEvent(SetupEvent.SetTimer(it)) },
            )
        }
        OptionRow(stringResource(R.string.setup_bonus), stringResource(R.string.setup_bonus_hint)) {
            Segmented(
                values = BoothConfig.BonusChoices,
                selected = config.bonusShots,
                label = { stringResource(R.string.setup_bonus_value, it) },
                onSelect = { onEvent(SetupEvent.SetBonus(it)) },
            )
        }
        OptionRow(stringResource(R.string.setup_camera)) {
            Segmented(
                values = Lens.entries,
                selected = config.lens,
                label = { stringResource(if (it == Lens.Front) R.string.setup_camera_front else R.string.setup_camera_back) },
                onSelect = { onEvent(SetupEvent.SetLens(it)) },
            )
        }
        // Mirroring only means something for selfies, and the LED flash only exists on the back: the row
        // under Camera follows the lens (§13).
        if (config.lens == Lens.Front) {
            OptionRow(stringResource(R.string.setup_mirror), stringResource(R.string.setup_mirror_hint)) {
                Switch(checked = config.mirror, onCheckedChange = { onEvent(SetupEvent.SetMirror(it)) })
            }
        } else {
            OptionRow(stringResource(R.string.setup_back_flash), stringResource(R.string.setup_back_flash_hint)) {
                Segmented(
                    values = BackFlash.entries,
                    selected = config.backFlash,
                    label = { stringResource(if (it == BackFlash.Off) R.string.setup_back_flash_off else R.string.setup_back_flash_auto) },
                    onSelect = { onEvent(SetupEvent.SetBackFlash(it)) },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, hint: String? = null, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        control()
    }
}

@Composable
private fun <T> Segmented(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.width(196.dp)) {
        values.forEachIndexed { index, value ->
            SegmentedButton(
                selected = value == selected,
                onClick = { if (value != selected) onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, values.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.outline,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                icon = {},
                label = { Text(label(value), maxLines = 1, softWrap = false) },
            )
        }
    }
}

// ---- Previews ----

/** A filled-in Setup state for previews and screenshot tests (theme frame, nothing loading). */
internal fun sampleSetupState(
    config: BoothConfig = BoothConfig(),
    frames: List<FrameCardState> = FrameTheme.entries.map { FrameCardState(FrameOption.Theme(it)) },
    preview: StripSpec? = StripSpec(
        FrameSource.Theme(FrameTheme.fromId(config.frameId.localId) ?: FrameTheme.Default),
        config.layout,
        config.filter,
        date = java.time.LocalDate.of(2026, 10, 20),
        preview = true,
    ),
    filterSample: Bitmap? = null,
) = SetupUiState(config = config, frames = frames, preview = preview, filterSample = filterSample)

@Preview(name = "Setup · light", widthDp = 411, heightDp = 923)
@Composable
private fun SetupLightPreview() {
    BoothTheme(darkTheme = false) { SetupScreen(sampleSetupState(), onEvent = {}) }
}

@Preview(name = "Setup · dark · frames", widthDp = 411, heightDp = 923)
@Composable
private fun SetupDarkPreview() {
    BoothTheme(darkTheme = true) { SetupScreen(sampleSetupState(), onEvent = {}, initialTab = SetupTab.Frame) }
}
