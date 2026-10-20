package com.blaze.photobooth.ui.feature.frames

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.StripPreview
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import com.blaze.photobooth.ui.theme.handwritten
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The import flow (README §4.9): finding the windows, Confirm (with naming), the slot editor, saving and the heart pop.
 * Stateless: [AddFrameRoute] owns the Photo Picker and the ViewModel.
 */
@Composable
fun AddFrameScreen(state: AddFrameUiState, onEvent: (AddFrameEvent) -> Unit, modifier: Modifier = Modifier) {
    val extra = BoothTheme.extraColors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        when (state.step) {
            AddFrameStep.Picking -> PickingContent(onEvent)
            AddFrameStep.Finding -> FindingContent(state.artwork, onEvent)
            AddFrameStep.Confirm, AddFrameStep.Saving, AddFrameStep.Saved -> ConfirmContent(state, onEvent)
            AddFrameStep.Editor -> state.editor?.let { SlotEditorContent(state, it, onEvent) }
            AddFrameStep.Failed -> FailedContent(onEvent)
        }
        if (state.step == AddFrameStep.Saving) SavingOverlay()
        if (state.step == AddFrameStep.Saved) SavedOverlay()
    }
}

/** ✕ and a title over the gradient, like Setup's back button. */
@Composable
internal fun FlowTopBar(title: String, closeDescription: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
            Icon(Icons.Rounded.Close, contentDescription = closeDescription)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 12.dp)
                .semantics { heading() },
        )
    }
}

// ---- Picking ----

@Composable
private fun PickingContent(onEvent: (AddFrameEvent) -> Unit) {
    // Only seen behind the Photo Picker, or if the app came back without its answer: offer to open it again.
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(stringResource(R.string.add_frame_title), stringResource(R.string.cd_add_frame_close), { onEvent(AddFrameEvent.Close) })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SquishyButton(onClick = { onEvent(AddFrameEvent.PickAnother) }, style = SquishyButtonStyle.Tonal) {
                Text(stringResource(R.string.add_frame_pick))
            }
        }
    }
}

// ---- Finding ----

/** "Finding the photo windows… ✨": the picture, dimmed, with a sparkly band sweeping down it. */
@Composable
private fun FindingContent(artwork: Bitmap?, onEvent: (AddFrameEvent) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "finding")
    val sweep = if (reduceMotion) {
        null
    } else {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(FindingSweepMillis, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    }
    val primary = MaterialTheme.colorScheme.primary
    val sparkle = Color.White
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowTopBar(stringResource(R.string.add_frame_title), stringResource(R.string.cd_add_frame_close), { onEvent(AddFrameEvent.Close) })
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val aspect = artwork?.let { it.width.toFloat() / it.height } ?: (1f / 3f)
            Box(
                Modifier
                    .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                    .shadow(10.dp, RoundedCornerShape(6.dp))
                    .background(Color.White, RoundedCornerShape(6.dp)),
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = remember(artwork) { artwork.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.6f),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val t = sweep?.value ?: 0.5f
                    val bandH = size.height * 0.16f
                    val y = -bandH + (size.height + bandH) * t
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, primary.copy(alpha = 0.38f), Color.Transparent),
                            startY = y,
                            endY = y + bandH,
                        ),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, bandH),
                    )
                    for ((i, p) in SparkleSpots.withIndex()) {
                        val twinkle = if (sweep == null) 1f else (0.5f + 0.5f * sin((t * 2f + i * 0.37f) * 2f * PI.toFloat()))
                        drawSparkle(Offset(p.x * size.width, p.y * size.height), (8 + (i % 3) * 3).dp.toPx() * (0.6f + 0.4f * twinkle), primary, sparkle, twinkle)
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.add_frame_finding),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 64.dp)
                .navigationBarsPadding()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private val SparkleSpots = listOf(
    Offset(0.18f, 0.12f), Offset(0.82f, 0.22f), Offset(0.3f, 0.41f), Offset(0.74f, 0.55f), Offset(0.2f, 0.7f), Offset(0.66f, 0.86f),
)

/** A four-point sparkle ✦ with a soft halo. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color, halo: Color, alpha: Float) {
    drawCircle(halo.copy(alpha = 0.55f * alpha), radius * 0.9f, center)
    val path = Path().apply {
        val r = radius
        val k = r * 0.28f
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k * 0.3f, center.y - k * 0.3f, center.x + r, center.y)
        quadraticTo(center.x + k * 0.3f, center.y + k * 0.3f, center.x, center.y + r)
        quadraticTo(center.x - k * 0.3f, center.y + k * 0.3f, center.x - r, center.y)
        quadraticTo(center.x - k * 0.3f, center.y - k * 0.3f, center.x, center.y - r)
        close()
    }
    drawPath(path, color.copy(alpha = 0.35f + 0.65f * alpha))
}

// ---- Confirm ----

@Composable
private fun ConfirmContent(state: AddFrameUiState, onEvent: (AddFrameEvent) -> Unit) {
    val count = state.windows.size
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(stringResource(R.string.add_frame_title), stringResource(R.string.cd_add_frame_close), { onEvent(AddFrameEvent.Close) })
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            FrameWithWindows(state)
        }
        Text(
            text = if (state.found) {
                pluralStringResource(R.plurals.add_frame_found, count, count)
            } else {
                pluralStringResource(R.plurals.add_frame_count, count, count)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = stringResource(R.string.add_frame_note),
            style = MaterialTheme.typography.titleMedium.handwritten(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 12.dp),
        )
        if (state.tooSmall) SmallWarning(state.sourceWidth)
        ConfirmPanel(state, onEvent)
    }
}

/** The frame with the windows see-through, placeholder photos peeking through, and each window outlined and numbered. */
@Composable
private fun FrameWithWindows(state: AddFrameUiState, modifier: Modifier = Modifier) {
    val result = state.result ?: return
    val spec = remember(result, state.windows) {
        StripSpec(FrameSource.Image(previewFrame(state.frameWidth, state.frameHeight, state.windows), result), preview = true)
    }
    val count = state.windows.size
    val description = pluralStringResource(R.plurals.cd_add_frame_preview, count, count)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .aspectRatio(state.frameWidth.toFloat() / state.frameHeight, matchHeightConstraintsFirst = true)
            .shadow(10.dp, RoundedCornerShape(3.dp)),
    ) {
        StripPreview(spec, Modifier.fillMaxSize(), contentDescription = description)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val s = constraints.maxWidth / state.frameWidth.toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                for (w in state.windows) {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(w.x * s, w.y * s),
                        size = Size(w.w * s, w.h * s),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(2.5.dp.toPx(), pathEffect = dash),
                    )
                }
            }
            state.windows.forEachIndexed { i, w ->
                val badge = 26.dp
                val badgePx = with(density) { badge.toPx() }
                NumberBadge(
                    number = i + 1,
                    size = badge,
                    modifier = Modifier.offset {
                        IntOffset(((w.x + w.w / 2) * s - badgePx / 2).roundToInt(), (w.y * s + 6 * density.density).roundToInt())
                    },
                )
            }
        }
    }
}

/** A stand-in [ImageFrame] for drawing the frame being imported with the real renderer. */
internal fun previewFrame(width: Int, height: Int, windows: List<SlotRect>) = ImageFrame(
    id = FrameId.custom("import"),
    name = "",
    width = width,
    height = height,
    slots = windows,
    textArea = null,
    artwork = ArtworkLocation.Asset(""),
    thumb = ArtworkLocation.Asset(""),
)

/** ①②③④ on a strawberry circle. */
@Composable
internal fun NumberBadge(number: Int, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .shadow(2.dp, CircleShape)
            .background(if (selected) colors.primary else BoothTheme.extraColors.raised, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.primary,
        )
    }
}

@Composable
private fun SmallWarning(width: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                text = stringResource(R.string.add_frame_small, width),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** Looks right / Adjust, or (after Looks right) the name and Save. */
@Composable
private fun ConfirmPanel(state: AddFrameUiState, onEvent: (AddFrameEvent) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        AnimatedContent(
            targetState = state.naming,
            transitionSpec = { fadeIn(BoothMotion.gentle()) togetherWith fadeOut(BoothMotion.snappy()) },
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            label = "panel",
        ) { naming ->
            if (!naming) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SquishyButton(
                        onClick = { onEvent(AddFrameEvent.Adjust) },
                        style = SquishyButtonStyle.Outlined,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_frame_adjust), style = MaterialTheme.typography.titleMedium)
                    }
                    SquishyButton(
                        onClick = { onEvent(AddFrameEvent.LooksRight) },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(56.dp),
                    ) {
                        Text(stringResource(R.string.add_frame_looks_right), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { onEvent(AddFrameEvent.SetName(it)) },
                        label = { Text(stringResource(R.string.add_frame_name_label)) },
                        singleLine = true,
                        isError = state.saveFailed,
                        supportingText = if (state.saveFailed) {
                            { Text(stringResource(R.string.add_frame_save_failed)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onEvent(AddFrameEvent.Save) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        SquishyButton(
                            onClick = { onEvent(AddFrameEvent.CancelNaming) },
                            style = SquishyButtonStyle.Outlined,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Text(stringResource(R.string.manage_cancel), style = MaterialTheme.typography.titleMedium)
                        }
                        SquishyButton(
                            onClick = { onEvent(AddFrameEvent.Save) },
                            enabled = state.step == AddFrameStep.Confirm,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(56.dp),
                        ) {
                            Text(stringResource(R.string.add_frame_save), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.add_frame_saving),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** A big heart pops with a little burst, then the flow closes (the route waits [SavedMillis]). */
@Composable
private fun SavedOverlay() {
    val reduceMotion = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduceMotion) pop.animateTo(1f, BoothMotion.bouncy()) }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val t = pop.value.coerceIn(0f, 1.2f)
                    for (i in 0 until 10) {
                        val a = i * (2 * PI.toFloat() / 10) - PI.toFloat() / 2
                        val d = size.minDimension * 0.22f + size.minDimension * 0.24f * t
                        val c = Offset(center.x + d * kotlin.math.cos(a), center.y + d * sin(a))
                        drawCircle(if (i % 2 == 0) primary else tertiary, (4 + i % 3 * 2).dp.toPx() * (1.1f - 0.5f * t), c, alpha = (1.2f - t).coerceIn(0f, 1f))
                    }
                }
                Box(
                    Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .shadow(8.dp, CircleShape)
                        .background(BoothTheme.extraColors.raised, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = primary, modifier = Modifier.size(64.dp))
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    text = stringResource(R.string.add_frame_saved),
                    style = MaterialTheme.typography.titleMedium,
                    color = primary,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

// ---- Failed ----

@Composable
private fun FailedContent(onEvent: (AddFrameEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(stringResource(R.string.add_frame_title), stringResource(R.string.cd_add_frame_close), { onEvent(AddFrameEvent.Close) })
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖼️", style = MaterialTheme.typography.displaySmall, modifier = Modifier.clearAndSetSemantics {})
                    Text(
                        stringResource(R.string.add_frame_open_failed_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.add_frame_open_failed_body),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SquishyButton(onClick = { onEvent(AddFrameEvent.Close) }, style = SquishyButtonStyle.Outlined) {
                            Text(stringResource(R.string.manage_cancel))
                        }
                        SquishyButton(onClick = { onEvent(AddFrameEvent.PickAnother) }, contentPadding = PaddingValues(horizontal = 20.dp)) {
                            Text(stringResource(R.string.add_frame_pick_another))
                        }
                    }
                }
            }
        }
    }
}

private const val FindingSweepMillis = 1_600

/** How long the heart pop shows before the flow closes. */
const val SavedMillis = 1_300L
