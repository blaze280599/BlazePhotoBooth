package com.blaze.photobooth.model

import com.blaze.photobooth.render.FrameSlotDetector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A photo window in the slot editor (README §4.9 step 4). [id] stays put while windows are renumbered. */
data class EditorWindow(val id: Int, val rect: SlotRect)

/** What a drag holds: the window itself (move) or one of its corners (resize). */
enum class WindowHandle { Body, TopLeft, TopRight, BottomLeft, BottomRight }

/** A snap guide line across the frame: vertical at x = [at], or horizontal at y = [at] (frame pixels). */
data class SnapGuide(val vertical: Boolean, val at: Float)

/** A window after a drag step, and the guides it snapped to. */
data class WindowDrag(val rect: SlotRect, val guides: List<SnapGuide>)

/** Why the windows can't be saved yet (gentle validation, §5.5.2 step 4). Numbers are the 1-based display numbers. */
sealed interface WindowProblem {
    data object NoWindows : WindowProblem
    data object TooMany : WindowProblem
    data class Overlap(val first: Int, val second: Int) : WindowProblem
}

/** Small steps for TalkBack's custom actions, where dragging isn't possible. */
enum class WindowNudge { Left, Right, Up, Down, Bigger, Smaller }

/**
 * The slot editor's geometry, in frame pixels ([width] × [height]). Pure, so drag, resize, snap, ordering and the
 * overlap check are unit-tested.
 *
 * Drags are computed from where the window was when the finger went down plus the total movement, so snapping never
 * drifts. Edges snap to the frame's edges and centre lines and to other windows' edges and centres within `snap` px.
 */
class WindowEditor(val width: Int, val height: Int) {

    /** The smallest a window can get. */
    val minSize: Float = max(MinSizePx, min(width, height) * 0.05f)

    /** Windows in strip order (row-major, §5.5.2 step 3): window `i` here is photo `i + 1`. */
    fun ordered(windows: List<EditorWindow>): List<EditorWindow> = FrameSlotDetector.order(windows, height) { it.rect }

    /** Moves (Body) or resizes (a corner) [start] by the total drag ([dx], [dy]), snapping within [snap] px. */
    fun drag(others: List<SlotRect>, start: SlotRect, handle: WindowHandle, dx: Float, dy: Float, snap: Float): WindowDrag =
        if (handle == WindowHandle.Body) move(others, start, dx, dy, snap) else resize(others, start, handle, dx, dy, snap)

    private fun move(others: List<SlotRect>, start: SlotRect, dx: Float, dy: Float, snap: Float): WindowDrag {
        var x = (start.x + dx).coerceIn(0f, width - start.w)
        var y = (start.y + dy).coerceIn(0f, height - start.h)
        val guides = mutableListOf<SnapGuide>()
        nearest(listOf(x, x + start.w / 2, x + start.w), xTargets(others), snap)?.let { (from, to) ->
            x = (x + to - from).coerceIn(0f, width - start.w)
            guides += SnapGuide(vertical = true, at = to)
        }
        nearest(listOf(y, y + start.h / 2, y + start.h), yTargets(others), snap)?.let { (from, to) ->
            y = (y + to - from).coerceIn(0f, height - start.h)
            guides += SnapGuide(vertical = false, at = to)
        }
        val rect = SlotRect(x, y, start.w, start.h)
        return WindowDrag(rect, guides.filter { it.touches(rect) })
    }

    private fun resize(others: List<SlotRect>, start: SlotRect, corner: WindowHandle, dx: Float, dy: Float, snap: Float): WindowDrag {
        val movesLeft = corner == WindowHandle.TopLeft || corner == WindowHandle.BottomLeft
        val movesTop = corner == WindowHandle.TopLeft || corner == WindowHandle.TopRight
        var left = start.x
        var top = start.y
        var right = start.right
        var bottom = start.bottom
        val guides = mutableListOf<SnapGuide>()

        var ex = (if (movesLeft) left else right) + dx
        nearest(listOf(ex), xTargets(others), snap)?.let { (_, to) ->
            ex = to
            guides += SnapGuide(vertical = true, at = to)
        }
        if (movesLeft) left = ex.coerceIn(0f, right - minSize) else right = ex.coerceIn(left + minSize, width.toFloat())

        var ey = (if (movesTop) top else bottom) + dy
        nearest(listOf(ey), yTargets(others), snap)?.let { (_, to) ->
            ey = to
            guides += SnapGuide(vertical = false, at = to)
        }
        if (movesTop) top = ey.coerceIn(0f, bottom - minSize) else bottom = ey.coerceIn(top + minSize, height.toFloat())

        val rect = SlotRect(left, top, right - left, bottom - top)
        return WindowDrag(rect, guides.filter { it.touches(rect) })
    }

    /** A new window in the first free spot (centred, top to bottom), or null when there's no room or already 8. */
    fun add(windows: List<EditorWindow>): SlotRect? {
        if (windows.size >= MaxWindows) return null
        val rects = windows.map { it.rect }
        val gap = min(width, height) * 0.01f
        for (s in listOf(1f, 0.75f, 0.5f, 0.35f)) {
            val base = defaultWindow(width, height)
            val w = max(minSize, base.w * s)
            val h = max(minSize, base.h * s)
            val stepY = max(1f, height * 0.02f)
            val stepX = max(1f, width * 0.02f)
            val xs = generateSequence(0f) { it + stepX }.takeWhile { it + w <= width }.sortedBy { abs(it + w / 2 - width / 2f) }.toList()
            var y = 0f
            while (y + h <= height) {
                for (x in xs) {
                    val candidate = SlotRect(x, y, w, h)
                    val padded = SlotRect(x - gap, y - gap, w + 2 * gap, h + 2 * gap)
                    if (rects.none { it.overlaps(padded) }) return candidate
                }
                y += stepY
            }
        }
        return null
    }

    /** TalkBack's custom actions: move by 2% of the frame, or grow/shrink by 10% around the centre. */
    fun nudge(rect: SlotRect, nudge: WindowNudge): SlotRect {
        val mx = width * 0.02f
        val my = height * 0.02f
        return when (nudge) {
            WindowNudge.Left -> rect.copy(x = (rect.x - mx).coerceAtLeast(0f))
            WindowNudge.Right -> rect.copy(x = (rect.x + mx).coerceAtMost(width - rect.w))
            WindowNudge.Up -> rect.copy(y = (rect.y - my).coerceAtLeast(0f))
            WindowNudge.Down -> rect.copy(y = (rect.y + my).coerceAtMost(height - rect.h))
            WindowNudge.Bigger, WindowNudge.Smaller -> {
                val f = if (nudge == WindowNudge.Bigger) 1.1f else 1 / 1.1f
                val w = (rect.w * f).coerceIn(minSize, width.toFloat())
                val h = (rect.h * f).coerceIn(minSize, height.toFloat())
                val cx = rect.x + rect.w / 2
                val cy = rect.y + rect.h / 2
                SlotRect((cx - w / 2).coerceIn(0f, width - w), (cy - h / 2).coerceIn(0f, height - h), w, h)
            }
        }
    }

    /** The first reason the windows can't be saved, or null if they're fine (1–8 windows, no overlaps). */
    fun problem(windows: List<EditorWindow>): WindowProblem? {
        if (windows.isEmpty()) return WindowProblem.NoWindows
        if (windows.size > MaxWindows) return WindowProblem.TooMany
        val ordered = ordered(windows)
        for (i in ordered.indices) {
            for (j in i + 1 until ordered.size) {
                if (ordered[i].rect.overlaps(ordered[j].rect)) return WindowProblem.Overlap(i + 1, j + 1)
            }
        }
        return null
    }

    private fun xTargets(others: List<SlotRect>) =
        listOf(0f, width / 2f, width.toFloat()) + others.flatMap { listOf(it.x, it.x + it.w / 2, it.right) }

    private fun yTargets(others: List<SlotRect>) =
        listOf(0f, height / 2f, height.toFloat()) + others.flatMap { listOf(it.y, it.y + it.h / 2, it.bottom) }

    /** The closest (candidate, target) pair within [snap], if any. */
    private fun nearest(candidates: List<Float>, targets: List<Float>, snap: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        var bestD = snap
        for (c in candidates) for (t in targets) {
            val d = abs(c - t)
            if (d <= bestD) {
                bestD = d
                best = c to t
            }
        }
        return best
    }

    /** A guide stays only if the final (clamped) rect still lines up with it. */
    private fun SnapGuide.touches(r: SlotRect): Boolean {
        val edges = if (vertical) listOf(r.x, r.x + r.w / 2, r.right) else listOf(r.y, r.y + r.h / 2, r.bottom)
        return edges.any { abs(it - at) < 0.5f }
    }

    companion object {
        const val MaxWindows = FrameSlotDetector.MaxSlots
        private const val MinSizePx = 24f

        /** The window offered when detection found nothing (§13): centred, 60% wide, 4:3, at most 60% tall. */
        fun defaultWindow(width: Int, height: Int): SlotRect {
            var w = width * 0.6f
            var h = w * 0.75f
            if (h > height * 0.6f) {
                h = height * 0.6f
                w = h / 0.75f
            }
            return SlotRect((width - w) / 2f, (height - h) / 2f, w, h)
        }
    }
}
