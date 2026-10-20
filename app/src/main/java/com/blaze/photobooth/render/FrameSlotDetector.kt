package com.blaze.photobooth.render

import com.blaze.photobooth.model.SlotRect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the photo windows in frame artwork (README §5.5.2). Pure Kotlin over ARGB pixels, so it
 * runs in JVM unit tests. Used by the on-phone import flow (M7); bundled frames ship explicit slots.
 *
 * Works on a copy box-downscaled to a long edge ≤ [WorkEdge] px, then scales rects back up.
 *  - **Mode A** (transparent PNG): windows are 4-connected regions with alpha < 32 that don't
 *    touch the border and cover ≥ 1.5% of the image.
 *  - **Mode B** (opaque JPG/PNG, when A finds nothing): near-uniform colour regions (RGB distance
 *    ≤ 18 from the seed) flood-filled from a seed grid; rectangle-like (fill ≥ 0.85), ≥ 3% of the
 *    area, not touching the border. The largest same-coloured group wins and is keyed to
 *    transparency with a 2 px feather, so the frame then behaves like Mode A.
 *
 * Like the offline pipeline (`tools/frames/process_frames.py`), a window's rect is its solid
 * "core": rows and columns holding < 10% of the busiest row/column are trimmed, so a thin leak
 * along an outline into a same-coloured character doesn't stretch the slot.
 */
object FrameSlotDetector {

    enum class Mode { Transparent, SolidBoxes }

    enum class Failure { NoWindows, TooManyWindows, Overlapping }

    sealed interface Result {
        /**
         * [slots] are in row-major order, in full-resolution pixels. [pixels] is the artwork with
         * the windows transparent: the input itself for Mode A, a keyed copy for Mode B.
         */
        class Found(val slots: List<SlotRect>, val mode: Mode, val pixels: IntArray) : Result

        data class NotFound(val reason: Failure) : Result
    }

    const val WorkEdge = 1200
    const val AlphaThreshold = 32
    const val ColorTolerance = 18
    const val MaxSlots = 8

    fun detect(pixels: IntArray, width: Int, height: Int): Result {
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
        val work = Work.downscale(pixels, width, height)

        val transparent = findTransparent(work)
        if (transparent.isNotEmpty()) {
            return finish(transparent.map { work.toFull(it.core, width, height) }, width, height, Mode.Transparent, pixels)
        }
        val boxes = findSolidBoxes(work)
        if (boxes.isEmpty()) return Result.NotFound(Failure.NoWindows)
        val keyed = keyBoxes(pixels, width, height, work, boxes)
        return finish(boxes.map { work.toFull(it.core, width, height) }, width, height, Mode.SolidBoxes, keyed)
    }

    /** Row-major order: tops within 5% of the height share a row, then left to right (§5.5.2 step 3). */
    fun order(slots: List<SlotRect>, imageHeight: Int): List<SlotRect> {
        val rows = mutableListOf<MutableList<SlotRect>>()
        for (slot in slots.sortedBy { it.y }) {
            val row = rows.lastOrNull()
            if (row != null && slot.y - row.first().y < 0.05f * imageHeight) row += slot else rows += mutableListOf(slot)
        }
        return rows.flatMap { row -> row.sortedBy { it.x } }
    }

    private fun finish(rects: List<SlotRect>, width: Int, height: Int, mode: Mode, out: IntArray): Result {
        if (rects.size > MaxSlots) return Result.NotFound(Failure.TooManyWindows)
        if (rects.indices.any { i -> (i + 1 until rects.size).any { j -> rects[i].overlaps(rects[j]) } }) {
            return Result.NotFound(Failure.Overlapping)
        }
        // Grow a little so photos tuck under the artwork's anti-aliased window edges, unless that makes slots touch.
        val grown = rects.map { it.grow(SlotGrow, width, height) }
        val noOverlap = grown.indices.none { i -> (i + 1 until grown.size).any { j -> grown[i].overlaps(grown[j]) } }
        return Result.Found(order(if (noOverlap) grown else rects, height), mode, out)
    }

    private fun SlotRect.grow(by: Float, width: Int, height: Int): SlotRect {
        val l = max(0f, x - by)
        val t = max(0f, y - by)
        return SlotRect(l, t, min(width.toFloat(), right + by) - l, min(height.toFloat(), bottom + by) - t)
    }

    // ---- Mode A ----

    private fun findTransparent(work: Work): List<Region> {
        val visited = BooleanArray(work.size)
        val minArea = 0.015f * work.size
        val out = mutableListOf<Region>()
        for (i in 0 until work.size) {
            if (visited[i] || (work.pixels[i] ushr 24) >= AlphaThreshold) continue
            val region = flood(work, i, visited) { p -> (p ushr 24) < AlphaThreshold }
            if (!region.touchesBorder && region.area >= minArea) out += region
        }
        return out
    }

    // ---- Mode B ----

    private fun findSolidBoxes(work: Work): List<Region> {
        val visited = BooleanArray(work.size)
        val step = max(4, min(work.w, work.h) / 40)
        val minArea = 0.03f * work.size
        val kept = mutableListOf<Region>()
        for (sy in step / 2 until work.h step step) {
            for (sx in step / 2 until work.w step step) {
                val seed = sy * work.w + sx
                if (visited[seed] || (work.pixels[seed] ushr 24) < AlphaThreshold) continue
                val color = work.meanAround(sx, sy)
                val region = flood(work, seed, visited) { p ->
                    (p ushr 24) >= AlphaThreshold && distance(p, color) <= ColorTolerance
                }
                region.color = color
                if (!region.touchesBorder && region.area >= minArea && region.coreFill >= 0.85f) kept += region
            }
        }
        if (kept.isEmpty()) return emptyList()
        // Photo boxes are almost always one colour: take the biggest group of same-coloured regions.
        val groups = mutableListOf<MutableList<Region>>()
        for (region in kept.sortedByDescending { it.area }) {
            val group = groups.firstOrNull { distance(it.first().color, region.color) <= ColorTolerance }
            if (group != null) group += region else groups += mutableListOf(region)
        }
        return groups.maxWith(compareBy<List<Region>> { it.size }.thenBy { g -> g.sumOf { it.area } })
    }

    /** Keys the chosen boxes to transparency at full resolution, with a 2 px feathered edge. */
    private fun keyBoxes(pixels: IntArray, width: Int, height: Int, work: Work, boxes: List<Region>): IntArray {
        val out = pixels.copyOf()
        val clear = BooleanArray(pixels.size)
        val tolerance = ColorTolerance * 1.5f
        val areas = mutableListOf<IntBox>()
        for (box in boxes) {
            val rect = work.toFull(box.core, width, height)
            val pad = work.k + 2
            val x0 = max(0, rect.x.toInt() - pad)
            val y0 = max(0, rect.y.toInt() - pad)
            val x1 = min(width, ceil(rect.right).toInt() + pad)
            val y1 = min(height, ceil(rect.bottom).toInt() + pad)
            areas += IntBox(x0, y0, x1 - 1, y1 - 1)
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val i = y * width + x
                    // Only pixels that belong to the region (or sit right next to it), so a same-coloured
                    // character overlapping the box's surroundings stays opaque.
                    if (box.near(work, x / work.k, y / work.k) && distance(pixels[i], box.color) <= tolerance) clear[i] = true
                }
            }
        }
        for (i in pixels.indices) if (clear[i]) out[i] = pixels[i] and 0x00FFFFFF
        // Feather: opaque pixels 1 px from a window get alpha 1/3, 2 px get 2/3.
        for (a in areas) {
            for (y in max(0, a.y0 - 2)..min(height - 1, a.y1 + 2)) {
                for (x in max(0, a.x0 - 2)..min(width - 1, a.x1 + 2)) {
                    val i = y * width + x
                    if (clear[i]) continue
                    val d = nearestClear(clear, width, height, x, y, 2)
                    if (d > 0) out[i] = ((255 * d / 3) shl 24) or (pixels[i] and 0x00FFFFFF)
                }
            }
        }
        return out
    }

    private fun nearestClear(clear: BooleanArray, w: Int, h: Int, x: Int, y: Int, radius: Int): Int {
        for (r in 1..radius) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && clear[ny * w + nx]) return r
                }
            }
        }
        return 0
    }

    // ---- Shared ----

    /** Iterative 4-connected flood fill from [start]; [accept] decides which pixels join. */
    private inline fun flood(work: Work, start: Int, visited: BooleanArray, accept: (Int) -> Boolean): Region {
        val region = Region(work.w)
        val stack = IntStack()
        stack.push(start)
        visited[start] = true
        while (stack.isNotEmpty()) {
            val i = stack.pop()
            region.add(i, work)
            val x = i % work.w
            val y = i / work.w
            if (x > 0) tryPush(i - 1, work, visited, stack, accept)
            if (x < work.w - 1) tryPush(i + 1, work, visited, stack, accept)
            if (y > 0) tryPush(i - work.w, work, visited, stack, accept)
            if (y < work.h - 1) tryPush(i + work.w, work, visited, stack, accept)
        }
        region.seal()
        return region
    }

    private inline fun tryPush(n: Int, work: Work, visited: BooleanArray, stack: IntStack, accept: (Int) -> Boolean) {
        if (!visited[n] && accept(work.pixels[n])) {
            visited[n] = true
            stack.push(n)
        }
    }

    private fun distance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    private const val SlotGrow = 3f

    /** The box-downscaled working copy (factor [k]). */
    private class Work(val pixels: IntArray, val w: Int, val h: Int, val k: Int) {
        val size get() = w * h

        fun toFull(r: IntBox, width: Int, height: Int): SlotRect {
            val x0 = (r.x0 * k).toFloat()
            val y0 = (r.y0 * k).toFloat()
            val x1 = min(width, (r.x1 + 1) * k).toFloat()
            val y1 = min(height, (r.y1 + 1) * k).toFloat()
            return SlotRect(x0, y0, x1 - x0, y1 - y0)
        }

        /** Mean colour of the 3×3 block around (x, y), so a noisy seed pixel doesn't skew the fill. */
        fun meanAround(x: Int, y: Int): Int {
            var r = 0
            var g = 0
            var b = 0
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val p = pixels[ny * w + nx]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
            return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }

        companion object {
            fun downscale(src: IntArray, width: Int, height: Int): Work {
                val k = max(1, ceil(max(width, height) / WorkEdge.toFloat()).toInt())
                if (k == 1) return Work(src, width, height, 1)
                val w = ceil(width / k.toFloat()).toInt()
                val h = ceil(height / k.toFloat()).toInt()
                val out = IntArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        var a = 0
                        var r = 0
                        var g = 0
                        var b = 0
                        var n = 0
                        for (yy in y * k until min(height, (y + 1) * k)) {
                            for (xx in x * k until min(width, (x + 1) * k)) {
                                val p = src[yy * width + xx]
                                a += p ushr 24
                                r += (p shr 16) and 0xFF
                                g += (p shr 8) and 0xFF
                                b += p and 0xFF
                                n++
                            }
                        }
                        out[y * w + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                    }
                }
                return Work(out, w, h, k)
            }
        }
    }

    private data class IntBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val area get() = (x1 - x0 + 1) * (y1 - y0 + 1)
    }

    /** A connected region in work pixels: its members, bounding box and solid core. */
    private class Region(private val stride: Int) {
        private val members = IntStack()
        var area = 0
            private set
        var touchesBorder = false
            private set
        var color = 0
        private var minX = Int.MAX_VALUE
        private var minY = Int.MAX_VALUE
        private var maxX = Int.MIN_VALUE
        private var maxY = Int.MIN_VALUE
        private lateinit var memberSet: BooleanArray
        private var setW = 0
        lateinit var core: IntBox
            private set
        var coreFill = 0f
            private set

        fun add(i: Int, work: Work) {
            members.push(i)
            area++
            val x = i % stride
            val y = i / stride
            if (x == 0 || y == 0 || x == work.w - 1 || y == work.h - 1) touchesBorder = true
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }

        /** Computes the core rect (trimming thin leaks) and how solidly the region fills it. */
        fun seal() {
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val rows = IntArray(bh)
            val cols = IntArray(bw)
            setW = bw
            memberSet = BooleanArray(bw * bh)
            for (n in 0 until members.size) {
                val i = members[n]
                val x = i % stride - minX
                val y = i / stride - minY
                rows[y]++
                cols[x]++
                memberSet[y * bw + x] = true
            }
            val rowCut = 0.1f * rows.max()
            val colCut = 0.1f * cols.max()
            val y0 = rows.indexOfFirst { it >= rowCut }
            val y1 = rows.indexOfLast { it >= rowCut }
            val x0 = cols.indexOfFirst { it >= colCut }
            val x1 = cols.indexOfLast { it >= colCut }
            core = IntBox(minX + x0, minY + y0, minX + x1, minY + y1)
            var inside = 0
            for (y in y0..y1) for (x in x0..x1) if (memberSet[y * bw + x]) inside++
            coreFill = inside.toFloat() / core.area
        }

        /** True if work pixel (x, y) or one of its 8 neighbours belongs to this region. */
        fun near(work: Work, x: Int, y: Int): Boolean {
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx - minX
                val ny = y + dy - minY
                if (nx < 0 || ny < 0 || nx >= setW || ny >= memberSet.size / setW) continue
                if (x + dx >= work.w || y + dy >= work.h) continue
                if (memberSet[ny * setW + nx]) return true
            }
            return false
        }
    }

    /** A growable IntArray stack (no boxing in the flood fill). */
    private class IntStack {
        private var data = IntArray(1024)
        var size = 0
            private set

        fun push(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun pop(): Int = data[--size]

        fun isNotEmpty() = size > 0

        operator fun get(i: Int) = data[i]
    }
}
