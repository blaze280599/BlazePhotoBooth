package com.blaze.photobooth.ui.feature.capture

import android.graphics.RenderEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.blaze.photobooth.R
import com.blaze.photobooth.camera.CameraStatus
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.render.Filters
import com.blaze.photobooth.ui.components.CountdownRing
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Capture (README §4.3): the cropped, filtered viewfinder in the frame's colour, the countdown, the shot
 * tray and the big shutter. [viewfinder] draws the camera (a fake in previews and screenshot tests).
 * [screenFlashOn] is the front camera's screen flash (§9.5): the whole screen goes white.
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    permission: CameraPermission,
    onEvent: (CaptureEvent) -> Unit,
    modifier: Modifier = Modifier,
    screenFlashOn: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewfinder: @Composable (Modifier) -> Unit,
) {
    val extra = BoothTheme.extraColors
    val reduceMotion = LocalReduceMotion.current
    var viewfinderBounds by remember { mutableStateOf(Rect.Zero) }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }

    // §7 A6: the thumbnail arcs from the viewfinder into its tray slot.
    val flight = remember { Animatable(1f) }
    LaunchedEffect(state.flight?.key) {
        if (state.flight == null || reduceMotion) {
            flight.snapTo(1f)
        } else {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(FlightMillis, easing = FastOutSlowInEasing))
        }
    }
    val flyingIndex = state.flight?.index?.takeIf { flight.value < 1f }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CaptureTopBar(state, onEvent)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    permission != CameraPermission.Granted -> PermissionCard(permission, onEvent)
                    state.camera is CameraStatus.Failed -> CameraErrorCard(state.camera.reason, onEvent)
                    else -> ViewfinderCard(
                        state = state,
                        modifier = Modifier.onGloballyPositioned { viewfinderBounds = it.boundsInRoot() },
                        viewfinder = viewfinder,
                    )
                }
            }
            ShotTray(
                state = state,
                hiddenIndex = flyingIndex,
                onSlotPositioned = { index, rect -> slotBounds[index] = rect },
            )
            Spacer(Modifier.height(16.dp))
            ShutterButton(
                state = state,
                enabled = permission == CameraPermission.Granted && state.loaded && !state.cameraFailed,
                onClick = { onEvent(CaptureEvent.Shutter) },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (flyingIndex != null) {
            val thumb = state.tray.getOrNull(flyingIndex)?.thumbnail
            val target = slotBounds[flyingIndex]
            if (thumb != null && target != null && viewfinderBounds != Rect.Zero) {
                FlyingThumb(thumb, state.filter, viewfinderBounds, target, flight.value)
            }
        }

        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 120.dp),
        )
        FlashOverlay(state.flashCount, reduceMotion)
        if (screenFlashOn) Box(Modifier.fillMaxSize().background(Color.White))
    }

    if (state.showStorageWarning) {
        AlertDialog(
            onDismissRequest = { onEvent(CaptureEvent.DismissStorageWarning) },
            title = { Text(stringResource(R.string.capture_storage_title)) },
            text = { Text(stringResource(R.string.capture_storage_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(CaptureEvent.StartAnyway) }) { Text(stringResource(R.string.capture_storage_go)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CaptureEvent.DismissStorageWarning) }) {
                    Text(stringResource(R.string.capture_storage_cancel))
                }
            },
        )
    }
}

@Composable
private fun CaptureTopBar(state: CaptureUiState, onEvent: (CaptureEvent) -> Unit) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = BoothTheme.extraColors.raised,
        contentColor = MaterialTheme.colorScheme.primary,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onEvent(CaptureEvent.Close) }, colors = colors) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_booth))
        }
        Spacer(Modifier.weight(1f))
        if (state.total > 0) {
            val description = stringResource(R.string.cd_capture_counter, state.shotNumber, state.total)
            Surface(
                shape = CircleShape,
                color = BoothTheme.extraColors.raised,
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Text(
                    text = if (state.isBonus) {
                        stringResource(R.string.capture_counter_bonus, state.shotNumber, state.total)
                    } else {
                        stringResource(R.string.capture_counter, state.shotNumber, state.total)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onEvent(CaptureEvent.Flip) }, enabled = state.canFlip, colors = colors) {
            Icon(Icons.Rounded.Cameraswitch, contentDescription = stringResource(R.string.cd_flip_camera))
        }
    }
}

/**
 * The live camera, cropped to the current slot's shape (§9.2), framed in the frame's colour with 28 dp
 * corners, with the filter applied on the GPU (§9.3) and the countdown on top. The last second wiggles.
 */
@Composable
private fun ViewfinderCard(state: CaptureUiState, modifier: Modifier, viewfinder: @Composable (Modifier) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val aspect by animateFloatAsState(state.aspect, BoothMotion.gentle(), label = "aspect")
    val wiggle = remember { Animatable(0f) }
    val lastSecond = (state.phase as? SequencePhase.Countdown)?.secondsLeft == 1
    LaunchedEffect(lastSecond) {
        if (lastSecond && !reduceMotion) {
            wiggle.animateTo(
                0f,
                keyframes {
                    durationMillis = WiggleMillis
                    -2.5f at 90
                    2.5f at 200
                    -1.5f at 310
                    1f at 400
                },
            )
        }
    }
    val effect = remember(state.filter) {
        Filters.platform(state.filter)?.let { RenderEffect.createColorFilterEffect(it).asComposeRenderEffect() }
    }
    val wiggling = wiggle.isRunning
    Box(
        modifier
            .aspectRatio(aspect)
            .then(if (wiggling) Modifier.graphicsLayer { rotationZ = wiggle.value } else Modifier)
            .clip(ViewfinderShape)
            .background(Color(state.frameColor))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), ViewfinderShape)
            .padding(FrameWidth)
            .clip(InnerShape)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val description = stringResource(R.string.cd_viewfinder)
        Box(
            Modifier
                .fillMaxSize()
                .then(if (effect != null) Modifier.graphicsLayer { renderEffect = effect } else Modifier)
                .semantics { contentDescription = description },
        ) {
            viewfinder(Modifier.fillMaxSize())
        }
        ViewfinderOverlay(state)
    }
}

@Composable
private fun BoxScope.ViewfinderOverlay(state: CaptureUiState) {
    val prompts = stringArrayResource(R.array.capture_prompts)
    when (val phase = state.phase) {
        is SequencePhase.Countdown -> {
            Pill(
                text = prompts[state.promptIndex % prompts.size],
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
            )
            CountdownRing(
                secondsLeft = phase.secondsLeft,
                totalSeconds = state.timerSec,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, bottom = 8.dp),
            )
        }
        SequencePhase.Beat -> BigWord(R.string.capture_nice)
        SequencePhase.Done -> BigWord(R.string.capture_done)
        SequencePhase.Idle -> Pill(
            text = stringResource(if (state.camera == CameraStatus.Ready) R.string.capture_hint_ready else R.string.capture_hint_starting),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
        SequencePhase.Paused -> Pill(
            text = stringResource(R.string.capture_hint_paused),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
        SequencePhase.Capturing -> Unit
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.handwritten(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BigWord(text: Int) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (reduceMotion) fadeIn() togetherWith fadeOut() else (scaleIn(BoothMotion.bouncy(), 0.4f) + fadeIn()) togetherWith fadeOut()
        },
        label = "word",
    ) { res ->
        Surface(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
        ) {
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.headlineLarge.handwritten(),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * N little slots that fill as photos land (§4.3). Each has its own slot's shape; the next one to fill
 * glows pink. Sized so every slot fits on one row.
 */
@Composable
private fun ShotTray(state: CaptureUiState, hiddenIndex: Int?, onSlotPositioned: (Int, Rect) -> Unit) {
    if (state.tray.isEmpty()) {
        Spacer(Modifier.height(TrayHeight))
        return
    }
    val description = stringResource(R.string.cd_shot_tray, state.taken, state.total)
    val colorFilter = remember(state.filter) { Filters.compose(state.filter) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(TrayHeight)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val gap = 8.dp
        val widths = state.tray.map { TrayHeight * it.aspect.coerceIn(0.6f, 1.6f) }
        val natural = widths.fold(0.dp) { acc, w -> acc + w } + gap * (widths.size - 1)
        val scale = if (natural > maxWidth) maxWidth / natural else 1f
        Row(horizontalArrangement = Arrangement.spacedBy(gap * scale), verticalAlignment = Alignment.CenterVertically) {
            state.tray.forEachIndexed { index, shot ->
                val current = index == state.taken && state.phase != SequencePhase.Done
                val shape = RoundedCornerShape(10.dp * scale)
                Box(
                    Modifier
                        .size(widths[index] * scale, TrayHeight * scale)
                        .onGloballyPositioned { onSlotPositioned(index, it.boundsInRoot()) }
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = if (current) 2.5.dp else 1.dp,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = shape,
                        ),
                ) {
                    if (shot.thumbnail != null && index != hiddenIndex) {
                        Image(
                            bitmap = shot.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** The 88 dp shutter (§6.7): starts the sequence, then becomes pause ⏸ / resume ▶. */
@Composable
private fun ShutterButton(state: CaptureUiState, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) BoothMotion.PressedScale else 1f, BoothMotion.release(), label = "shutter")
    val label = when {
        state.running -> R.string.cd_shutter_pause
        state.phase == SequencePhase.Paused -> R.string.cd_shutter_resume
        else -> R.string.cd_shutter_start
    }
    val description = stringResource(label)
    Surface(
        onClick = onClick,
        enabled = enabled && state.phase != SequencePhase.Done,
        shape = CircleShape,
        color = BoothTheme.extraColors.raised,
        border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 6.dp,
        interactionSource = interaction,
        modifier = Modifier
            .size(ShutterSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(ShutterSize - 24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                val icon = when {
                    state.running -> Icons.Rounded.Pause
                    state.phase == SequencePhase.Paused -> Icons.Rounded.PlayArrow
                    state.phase == SequencePhase.Done -> Icons.Rounded.Check
                    else -> null
                }
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

/** §7 A6: the newest shot along a quadratic Bézier from the viewfinder up and over into its slot. */
@Composable
private fun FlyingThumb(thumb: ImageBitmap, filter: PhotoFilter, from: Rect, to: Rect, t: Float) {
    val density = LocalDensity.current
    val lift = with(density) { 120.dp.toPx() }
    val start = from.center
    val end = to.center
    val control = Offset(lerp(start.x, end.x, 0.5f), minOf(start.y, end.y) - lift)
    val u = 1f - t
    val position = Offset(
        u * u * start.x + 2 * u * t * control.x + t * t * end.x,
        u * u * start.y + 2 * u * t * control.y + t * t * end.y,
    )
    val startSize = androidx.compose.ui.geometry.Size(from.width * 0.6f, from.width * 0.6f * to.height / to.width)
    val size = lerp(startSize, to.size, t)
    val colorFilter = remember(filter) { Filters.compose(filter) }
    with(density) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .offset { IntOffset((position.x - size.width / 2).roundToInt(), (position.y - size.height / 2).roundToInt()) }
                .size(size.width.toDp(), size.height.toDp())
                .graphicsLayer { rotationZ = -10f * sin(PI.toFloat() * t) }
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, Color.White, RoundedCornerShape(10.dp)),
        )
    }
}

private fun lerp(a: androidx.compose.ui.geometry.Size, b: androidx.compose.ui.geometry.Size, t: Float) =
    androidx.compose.ui.geometry.Size(lerp(a.width, b.width, t), lerp(a.height, b.height, t))

/** §4.3: a white flash (≈ 220 ms ease-out) on every shutter. */
@Composable
private fun FlashOverlay(flashCount: Int, reduceMotion: Boolean) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(flashCount) {
        if (flashCount == 0) return@LaunchedEffect
        alpha.snapTo(if (reduceMotion) 0.6f else 0.95f)
        alpha.animateTo(0f, tween(FlashMillis, easing = EaseOut))
    }
    if (alpha.value > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha.value)),
        )
    }
}

/** The friendly pre-prompt (§12), then "Try again" or "Open settings". */
@Composable
private fun PermissionCard(permission: CameraPermission, onEvent: (CaptureEvent) -> Unit) {
    val (body, button, event) = when (permission) {
        CameraPermission.Blocked -> Triple(R.string.capture_permission_blocked_body, R.string.capture_permission_settings, CaptureEvent.OpenSettings)
        CameraPermission.Denied -> Triple(R.string.capture_permission_denied_body, R.string.capture_permission_retry, CaptureEvent.RequestPermission)
        else -> Triple(R.string.capture_permission_body, R.string.capture_permission_allow, CaptureEvent.RequestPermission)
    }
    MessageCard(emoji = "📸", title = R.string.capture_permission_title, body = body, button = button) { onEvent(event) }
}

@Composable
private fun CameraErrorCard(reason: CameraStatus.Reason, onEvent: (CaptureEvent) -> Unit) {
    val body = if (reason == CameraStatus.Reason.InUse) R.string.capture_error_in_use else R.string.capture_error_other
    MessageCard(emoji = "😴", title = R.string.capture_error_title, body = body, button = R.string.capture_error_retry) {
        onEvent(CaptureEvent.RetryCamera)
    }
}

@Composable
private fun MessageCard(emoji: String, title: Int, body: Int, button: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 360.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.displayMedium, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.height(12.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            SquishyButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "Your N shots will be lost" (§3). */
@Composable
fun LeaveBoothDialog(shots: Int, onStay: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.capture_leave_title)) },
        text = { Text(pluralStringResource(R.plurals.capture_leave_body, shots, shots)) },
        confirmButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.capture_leave_stay)) } },
        dismissButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.capture_leave_go)) } },
    )
}

private val ViewfinderShape = RoundedCornerShape(28.dp)
private val FrameWidth = 8.dp
private val InnerShape = RoundedCornerShape(28.dp - FrameWidth)
private val TrayHeight = 56.dp
private val ShutterSize = 88.dp
private const val FlightMillis = 650
private const val FlashMillis = 220
private const val WiggleMillis = 480

@Preview(name = "Capture · idle", widthDp = 411, heightDp = 923)
@Composable
private fun CapturePreview() {
    BoothTheme(darkTheme = true) {
        CaptureScreen(
            state = CaptureUiState(
                loaded = true,
                cuts = 4,
                sequence = SequenceState(total = 6),
                tray = List(6) { TrayShot(1.43f) },
                camera = CameraStatus.Ready,
                aspect = 1.43f,
            ),
            permission = CameraPermission.Granted,
            onEvent = {},
        ) { Box(it.background(Color(0xFF9C83F0))) }
    }
}
