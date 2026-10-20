package com.blaze.photobooth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** README §4.9 step 4: the slot editor's drag, resize, snap, numbering and gentle validation. */
class WindowEditorTest {

    private val editor = WindowEditor(1200, 3600)

    @Test
    fun dragMovesByTheTotalAndStaysOnTheFrame() {
        val start = SlotRect(100f, 100f, 400f, 300f)
        val moved = editor.drag(emptyList(), start, WindowHandle.Body, dx = 230f, dy = 1000f, snap = 0f)
        assertEquals(SlotRect(330f, 1100f, 400f, 300f), moved.rect)
        assertTrue(moved.guides.isEmpty())
        // Dragged far off the bottom-right: clamped inside, same size.
        val clamped = editor.drag(emptyList(), start, WindowHandle.Body, dx = 5000f, dy = 9000f, snap = 0f).rect
        assertEquals(SlotRect(800f, 3300f, 400f, 300f), clamped)
    }

    @Test
    fun dragSnapsToTheCentreLineAndOtherWindows() {
        val start = SlotRect(100f, 1000f, 400f, 300f)
        // The window's centre lands 6 px right of the frame's centre line (600): it snaps onto it.
        val centred = editor.drag(emptyList(), start, WindowHandle.Body, dx = 306f, dy = 0f, snap = 10f)
        assertEquals(400f, centred.rect.x)
        assertEquals(listOf(SnapGuide(vertical = true, at = 600f)), centred.guides)

        // Its top comes within 8 px of another window's bottom edge (900): it lines up exactly.
        val other = SlotRect(100f, 500f, 1000f, 400f)
        val stacked = editor.drag(listOf(other), start, WindowHandle.Body, dx = 0f, dy = -92f, snap = 10f)
        assertEquals(900f, stacked.rect.y)
        assertTrue(SnapGuide(vertical = false, at = 900f) in stacked.guides)

        // Too far away to snap: no guide, no jump.
        val free = editor.drag(listOf(other), start, WindowHandle.Body, dx = 0f, dy = -60f, snap = 10f)
        assertEquals(940f, free.rect.y)
        assertTrue(free.guides.none { !it.vertical })
    }

    @Test
    fun cornersResizeFromTheOppositeCorner() {
        val start = SlotRect(200f, 400f, 600f, 500f)
        val bigger = editor.drag(emptyList(), start, WindowHandle.BottomRight, dx = 100f, dy = 50f, snap = 0f).rect
        assertEquals(SlotRect(200f, 400f, 700f, 550f), bigger)
        val fromTopLeft = editor.drag(emptyList(), start, WindowHandle.TopLeft, dx = -50f, dy = 100f, snap = 0f).rect
        assertEquals(SlotRect(150f, 500f, 650f, 400f), fromTopLeft)
        // Can't turn inside out or shrink below the minimum.
        val tiny = editor.drag(emptyList(), start, WindowHandle.TopRight, dx = -2000f, dy = 2000f, snap = 0f).rect
        assertEquals(editor.minSize, tiny.w)
        assertEquals(editor.minSize, tiny.h)
        assertEquals(200f, tiny.x)
        assertEquals(900f, tiny.bottom)
        // …or grow past the frame's edge.
        val wide = editor.drag(emptyList(), start, WindowHandle.BottomLeft, dx = -900f, dy = 0f, snap = 0f).rect
        assertEquals(0f, wide.x)
    }

    @Test
    fun aResizedEdgeSnapsToAnotherWindow() {
        val left = SlotRect(80f, 400f, 500f, 500f)
        val start = SlotRect(620f, 400f, 500f, 400f)
        // Bottom edge dragged to 893: snaps to the left window's bottom (900).
        val snapped = editor.drag(listOf(left), start, WindowHandle.BottomRight, dx = 0f, dy = 93f, snap = 10f)
        assertEquals(900f, snapped.rect.bottom)
        assertEquals(listOf(SnapGuide(vertical = false, at = 900f)), snapped.guides)
    }

    @Test
    fun numbersFollowPositionsRowByRow() {
        val a = EditorWindow(0, SlotRect(100f, 2000f, 400f, 300f))
        val b = EditorWindow(1, SlotRect(700f, 120f, 400f, 300f))
        val c = EditorWindow(2, SlotRect(100f, 100f, 400f, 300f))
        val d = EditorWindow(3, SlotRect(700f, 2010f, 400f, 300f))
        // Tops within 5% of the height share a row; left to right inside it.
        assertEquals(listOf(2, 1, 0, 3), editor.ordered(listOf(a, b, c, d)).map { it.id })
        // Drag window 3 (id 0) to the very top: it becomes number 1.
        val moved = a.copy(rect = a.rect.copy(y = 0f, x = 0f))
        assertEquals(0, editor.ordered(listOf(moved, b, c, d)).first().id)
    }

    @Test
    fun validationIsGentleAndSpecific() {
        assertEquals(WindowProblem.NoWindows, editor.problem(emptyList()))
        val one = EditorWindow(0, SlotRect(100f, 100f, 400f, 300f))
        val two = EditorWindow(1, SlotRect(100f, 600f, 400f, 300f))
        assertNull(editor.problem(listOf(one, two)))
        // Touching edges is fine; overlapping isn't, and it names the windows by their numbers.
        assertNull(editor.problem(listOf(one, two.copy(rect = two.rect.copy(y = 400f)))))
        assertEquals(WindowProblem.Overlap(1, 2), editor.problem(listOf(two.copy(rect = two.rect.copy(y = 300f)), one)))
        val nine = List(9) { EditorWindow(it, SlotRect(0f, it * 400f, 100f, 100f)) }
        assertEquals(WindowProblem.TooMany, editor.problem(nine))
    }

    @Test
    fun addFindsFreeRoomWithoutOverlaps() {
        val first = checkNotNull(editor.add(emptyList()))
        assertEquals("centred", 600f, first.x + first.w / 2, 0.5f)
        assertEquals("at the top", 0f, first.y)
        var windows = listOf(EditorWindow(0, first))
        while (true) {
            val next = editor.add(windows) ?: break
            windows = windows + EditorWindow(windows.size, next)
        }
        assertTrue("several fit on a strip (${windows.size})", windows.size >= 6)
        assertNull("no overlaps", editor.problem(windows))
    }

    @Test
    fun eightIsTheMost() {
        val eight = List(8) { EditorWindow(it, SlotRect(0f, it * 400f, 100f, 100f)) }
        assertNull(editor.add(eight))
    }

    @Test
    fun addGivesUpWhenThereIsNoRoom() {
        val full = listOf(EditorWindow(0, SlotRect(0f, 0f, 1200f, 3600f)))
        assertNull(editor.add(full))
    }

    @Test
    fun nudgesMoveAndResizeInSmallSteps() {
        val r = SlotRect(100f, 100f, 400f, 300f)
        assertEquals(76f, editor.nudge(r, WindowNudge.Left).x)
        assertEquals(172f, editor.nudge(r, WindowNudge.Down).y)
        val bigger = editor.nudge(r, WindowNudge.Bigger)
        assertEquals(440f, bigger.w, 0.01f)
        assertEquals(300f, bigger.x + bigger.w / 2, 0.01f)
        assertEquals(0f, editor.nudge(r.copy(x = 5f), WindowNudge.Left).x)
    }

    @Test
    fun theDefaultWindowFitsAnyShape() {
        val tall = WindowEditor.defaultWindow(600, 1800)
        assertEquals(SlotRect(120f, 765f, 360f, 270f), tall)
        val wide = WindowEditor.defaultWindow(3000, 1000)
        assertTrue(wide.h <= 600f && wide.w <= 3000f)
    }
}
