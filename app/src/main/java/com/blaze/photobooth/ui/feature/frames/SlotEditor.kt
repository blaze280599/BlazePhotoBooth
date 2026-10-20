package com.blaze.photobooth.ui.feature.frames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.model.EditorWindow
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.model.WindowHandle
import com.blaze.photobooth.model.WindowNudge
import com.blaze.photobooth.model.WindowProblem
import com.blaze.photobooth.render.FrameCutout
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.theme.SheetShape
import com.blaze.photobooth.ui.theme.handwritten
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The slot editor (README §4.9 step 4): her picture with the photo windows as rounded rects. Drag a window to move it,
 * pull a corner handle to resize it; edges snap to the frame and to other windows (guides show). + Add window, Delete,
 * Done. Numbers follow the windows' positions. TalkBack users get a node per window with move/resize actions.
 */
@Composable
internal fun SlotEditorContent(state: AddFrameUiState, editor: EditorUiState, onEvent: (AddFrameEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(stringResource(R.string.editor_title), stringResource(R.string.cd_editor_cancel), { onEvent(AddFrameEvent.EditorCancel) })
        Text(
            text = stringResource(if (editor.fromStart) R.string.editor_hint_none else R.string.editor_hint),
            style = MaterialTheme.typography.titleMedium.handwritten(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            EditorCanvas(state, editor, onEvent)
        }
        EditorBar(editor, onEvent)
    }
}

@Composable
private fun EditorCanvas(state: AddFrameUiState, editor: EditorUiState, onEvent: (AddFrameEvent) -> Unit, modifier: Modifier = Modifier) {
    val artwork = state.artwork ?: return
    val image = remember(artwork) { artwork.asImageBitmap() }
    val colors = MaterialTheme.colorScheme
    val guideColor = colors.tertiary
    val density = LocalDensity.current
    Box(
        modifier
            .aspectRatio(state.frameWidth.toFloat() / state.frameHeight, matchHeightConstraintsFirst = true)
            .shadow(10.dp, RoundedCornerShape(3.dp))
            .background(Color.White),
    ) {
        Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // View pixels per frame pixel.
            val s = constraints.maxWidth / state.frameWidth.toFloat()
            val current by rememberUpdatedState(editor)
            val scale by rememberUpdatedState(s)
            val handleHit = with(density) { HandleHitRadius.toPx() }
            val snapPx = with(density) { SnapDistance.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val target = hitTest(current, down.position, scale, handleHit)
                            if (target == null) {
                                onEvent(AddFrameEvent.Select(null))
                                return@awaitEachGesture
                            }
                            onEvent(AddFrameEvent.DragStart(target.first, target.second))
                            var total = Offset.Zero
                            val afterSlop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (afterSlop != null) {
                                total = afterSlop.position - down.position
                                onEvent(AddFrameEvent.Drag(total.x / scale, total.y / scale, snapPx / scale))
                                drag(afterSlop.id) { change ->
                                    total += change.positionChange()
                                    change.consume()
                                    onEvent(AddFrameEvent.Drag(total.x / scale, total.y / scale, snapPx / scale))
                                }
                            }
                            onEvent(AddFrameEvent.DragEnd)
                        }
                    },
            ) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                for (g in editor.guides) {
                    if (g.vertical) {
                        drawLine(guideColor, Offset(g.at * s, 0f), Offset(g.at * s, size.height), 2.dp.toPx(), pathEffect = dash)
                    } else {
                        drawLine(guideColor, Offset(0f, g.at * s), Offset(size.width, g.at * s), 2.dp.toPx(), pathEffect = dash)
                    }
                }
                // The selected window is drawn last, on top.
                for (w in editor.windows.sortedBy { it.id == editor.selected }) {
                    val selected = w.id == editor.selected
                    val tl = Offset(w.rect.x * s, w.rect.y * s)
                    val sz = Size(w.rect.w * s, w.rect.h * s)
                    val radius = CornerRadius(FrameCutout.cornerRadius(w.rect) * s)
                    drawRoundRect(colors.primary.copy(alpha = if (selected) 0.30f else 0.18f), tl, sz, radius)
                    drawRoundRect(colors.primary, tl, sz, radius, style = Stroke((if (selected) 3.dp else 2.dp).toPx()))
                    if (selected) {
                        for (c in corners(w.rect, s)) {
                            drawCircle(Color.White, HandleRadius.toPx(), c)
                            drawCircle(colors.primary, HandleRadius.toPx(), c, style = Stroke(2.5.dp.toPx()))
                        }
                    }
                }
            }
            // Numbers, and one TalkBack node per window (no pointer input: touches reach the canvas above).
            editor.windows.forEachIndexed { i, w ->
                WindowNode(i + 1, w, w.id == editor.selected, s, onEvent)
            }
        }
    }
}

@Composable
private fun WindowNode(number: Int, window: EditorWindow, selected: Boolean, s: Float, onEvent: (AddFrameEvent) -> Unit) {
    val density = LocalDensity.current
    val r = window.rect
    val description = if (selected) {
        stringResource(R.string.cd_editor_window_selected, number)
    } else {
        stringResource(R.string.cd_editor_window, number)
    }
    val actions = listOf(
        R.string.editor_move_left to WindowNudge.Left,
        R.string.editor_move_right to WindowNudge.Right,
        R.string.editor_move_up to WindowNudge.Up,
        R.string.editor_move_down to WindowNudge.Down,
        R.string.editor_bigger to WindowNudge.Bigger,
        R.string.editor_smaller to WindowNudge.Smaller,
    ).map { (label, nudge) ->
        CustomAccessibilityAction(stringResource(label)) {
            onEvent(AddFrameEvent.Nudge(window.id, nudge))
            true
        }
    }
    val delete = stringResource(R.string.editor_delete_window)
    Box(
        Modifier
            .offset { IntOffset((r.x * s).roundToInt(), (r.y * s).roundToInt()) }
            .size(with(density) { (r.w * s).toDp() }, with(density) { (r.h * s).toDp() })
            .semantics {
                contentDescription = description
                this.selected = selected
                onClick { onEvent(AddFrameEvent.Select(window.id)); true }
                customActions = actions + CustomAccessibilityAction(delete) {
                    onEvent(AddFrameEvent.Select(window.id))
                    onEvent(AddFrameEvent.DeleteWindow)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        NumberBadge(number, 30.dp, selected = selected)
    }
}

/** The window (and handle) under [p] (view px): the selected window's corners first, then the topmost window. */
private fun hitTest(editor: EditorUiState, p: Offset, s: Float, handleRadius: Float): Pair<Int, WindowHandle>? {
    val selected = editor.windows.firstOrNull { it.id == editor.selected }
    if (selected != null) {
        val handles = listOf(WindowHandle.TopLeft, WindowHandle.TopRight, WindowHandle.BottomLeft, WindowHandle.BottomRight)
        corners(selected.rect, s).zip(handles)
            .filter { (c, _) -> hypot(c.x - p.x, c.y - p.y) <= handleRadius }
            .minByOrNull { (c, _) -> hypot(c.x - p.x, c.y - p.y) }
            ?.let { return selected.id to it.second }
        if (selected.rect.contains(p, s)) return selected.id to WindowHandle.Body
    }
    return editor.windows.lastOrNull { it.rect.contains(p, s) }?.let { it.id to WindowHandle.Body }
}

private fun SlotRect.contains(p: Offset, s: Float) = p.x >= x * s && p.x <= right * s && p.y >= y * s && p.y <= bottom * s

/** Top-left, top-right, bottom-left, bottom-right, in view px. */
private fun corners(r: SlotRect, s: Float) = listOf(
    Offset(r.x * s, r.y * s),
    Offset(r.right * s, r.y * s),
    Offset(r.x * s, r.bottom * s),
    Offset(r.right * s, r.bottom * s),
)

/** The validation note, then + Add window · Delete · Done. */
@Composable
private fun EditorBar(editor: EditorUiState, onEvent: (AddFrameEvent) -> Unit) {
    val message = when {
        editor.note == EditorNote.NoRoom -> stringResource(R.string.editor_no_room)
        editor.note == EditorNote.Full -> stringResource(R.string.editor_full)
        else -> when (val p = editor.problem) {
            WindowProblem.NoWindows -> stringResource(R.string.editor_problem_none)
            WindowProblem.TooMany -> stringResource(R.string.editor_problem_too_many)
            is WindowProblem.Overlap -> stringResource(R.string.editor_problem_overlap, p.first, p.second)
            null -> null
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .padding(bottom = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SquishyButton(
                    onClick = { onEvent(AddFrameEvent.AddWindow) },
                    style = SquishyButtonStyle.Tonal,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(56.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_add), maxLines = 1)
                }
                OutlinedIconButton(
                    onClick = { onEvent(AddFrameEvent.DeleteWindow) },
                    enabled = editor.canDelete,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.editor_delete_window))
                }
                SquishyButton(
                    onClick = { onEvent(AddFrameEvent.EditorDone) },
                    enabled = editor.canFinish,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_done), maxLines = 1)
                }
            }
        }
    }
}

private val HandleRadius = 9.dp
private val HandleHitRadius = 28.dp
private val SnapDistance = 10.dp
