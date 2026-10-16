package gx.zelda.screencam

import gx.zelda.screencam.live.GrayImage
import gx.zelda.shrines.scan.MapImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Finds the lit screen in a photo as candidate quadrilaterals. Each comes from a different mask of
 * a ~640 px copy of the photo, so one of them usually survives a bright room, glare or a screen
 * whose lower half is dark; [ScreenChrome] then picks the candidate whose frame looks right.
 */
object ScreenFinder {
    private const val WORK_SIDE = 640

    /** Each candidate's corners in photo pixels, clockwise, starting at any corner. */
    fun candidates(photo: MapImage): List<List<Point>> {
        val f = max(1, ceil(max(photo.width, photo.height) / WORK_SIDE.toDouble()).toInt())
        val w = photo.width / f
        val h = photo.height / f
        val luma = FloatArray(w * h)
        val saturated = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (dy in 0 until f) for (dx in 0 until f) {
                val c = photo.argb[(y * f + dy) * photo.width + x * f + dx]
                s += 0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
            }
            luma[y * w + x] = s / (f * f)
            val c = photo.argb[(y * f + f / 2) * photo.width + x * f + f / 2]
            val hi = max((c shr 16) and 0xff, max((c shr 8) and 0xff, c and 0xff))
            val lo = min((c shr 16) and 0xff, min((c shr 8) and 0xff, c and 0xff))
            saturated[y * w + x] = hi > 60 && (hi - lo) * 255 > 80 * hi
        }
        return outlines(luma, saturated, w, h, radius = 4).map { q -> q.map { Point(it.x * f, it.y * f) } }
    }

    /**
     * The same for a camera frame's grey, already downscaled to [small] (a few hundred pixels
     * across), [scale] small pixels per frame pixel. Corners are in frame pixels.
     */
    fun candidates(small: GrayImage, scale: Double): List<List<Point>> =
        // Closing radius: 4 px at 640 px across, in proportion below that.
        outlines(small.pixels, null, small.width, small.height, radius = max(1, (4.0 * max(small.width, small.height) / WORK_SIDE).roundToInt()))
            .map { q -> q.map { Point(it.x / scale, it.y / scale) } }

    private fun outlines(luma: FloatArray, saturated: BooleanArray?, w: Int, h: Int, radius: Int): List<List<Point>> {
        val otsu = otsu(luma)
        // Below Otsu the screen's dark frame joins it; at Otsu a lit room drops out; colour catches
        // the icons and frame when the map itself is dark.
        val masks = listOfNotNull(
            BooleanArray(w * h) { luma[it] > max(25.0, 0.5 * otsu) },
            BooleanArray(w * h) { luma[it] > otsu },
            saturated?.let { sat -> BooleanArray(w * h) { sat[it] || luma[it] > otsu } },
        )
        val minBlob = (0.005 * w * h).toInt()
        return masks.flatMap { raw ->
            val mask = erode(dilate(raw, w, h, radius), w, h, radius)
            val blobs = components(mask, w, h).filter { it.size >= minBlob }.sortedByDescending { it.size }
            listOfNotNull(
                blobs.firstOrNull()?.let { quad(it, mask, w, h) },
                // The union, for a screen split by a dark band (the sea below the map's south edge).
                blobs.takeIf { it.size > 1 }?.let { quad(concat(it), mask, w, h) },
            )
        }
    }

    private fun concat(arrays: List<IntArray>): IntArray {
        val out = IntArray(arrays.sumOf { it.size })
        var at = 0
        for (a in arrays) {
            a.copyInto(out, at)
            at += a.size
        }
        return out
    }

    /** The biggest quadrilateral in the hull of [pixels], refined by a line fit along each side. */
    private fun quad(pixels: IntArray, mask: BooleanArray, w: Int, h: Int): List<Point>? {
        if (pixels.size < 0.05 * w * h) return null
        val edge = ArrayList<Point>()
        for (i in pixels) {
            val x = i % w
            val y = i / w
            val border = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                !mask[i - 1] || !mask[i + 1] || !mask[i - w] || !mask[i + w]
            if (border) edge += Point(x + 0.5, y + 0.5)
        }
        val hull = convexHull(edge)
        if (hull.size < 4) return null
        // One diagonal is the farthest pair; the other two corners are farthest from it on either side.
        var a = 0
        var c = 0
        var far = -1.0
        for (i in hull.indices) for (j in i + 1 until hull.size) {
            val d = hypot(hull[i].x - hull[j].x, hull[i].y - hull[j].y)
            if (d > far) {
                far = d
                a = i
                c = j
            }
        }
        fun side(p: Point) = (hull[c].x - hull[a].x) * (p.y - hull[a].y) - (hull[c].y - hull[a].y) * (p.x - hull[a].x)
        val b = hull.indices.maxBy { side(hull[it]) }
        val d = hull.indices.minBy { side(hull[it]) }
        var corners = listOf(hull[a], hull[b], hull[c], hull[d])
        // Clockwise on screen (y down) is a positive shoelace sum.
        val area = corners.indices.sumOf { val p = corners[it]; val q = corners[(it + 1) % 4]; p.x * q.y - q.x * p.y }
        if (area < 0) corners = corners.reversed()
        return refineSides(corners, edge)
    }

    /** Fits a line to the edge pixels along the middle of each side and intersects neighbouring sides. */
    private fun refineSides(quad: List<Point>, edge: List<Point>): List<Point> {
        val lines = (0..3).map { i ->
            val p = quad[i]
            val q = quad[(i + 1) % 4]
            val len = hypot(q.x - p.x, q.y - p.y)
            val ux = (q.x - p.x) / len
            val uy = (q.y - p.y) / len
            val near = edge.filter {
                val t = (it.x - p.x) * ux + (it.y - p.y) * uy
                t > 0.15 * len && t < 0.85 * len && abs((it.x - p.x) * uy - (it.y - p.y) * ux) < 4.0
            }
            if (near.size < 10) return quad
            val mx = near.sumOf { it.x } / near.size
            val my = near.sumOf { it.y } / near.size
            var sxx = 0.0
            var sxy = 0.0
            var syy = 0.0
            for (n in near) {
                sxx += (n.x - mx) * (n.x - mx)
                sxy += (n.x - mx) * (n.y - my)
                syy += (n.y - my) * (n.y - my)
            }
            Triple(mx, my, 0.5 * atan2(2 * sxy, sxx - syy))
        }
        return (0..3).map { i ->
            val (x1, y1, t1) = lines[(i + 3) % 4]
            val (x2, y2, t2) = lines[i]
            val den = cos(t1) * sin(t2) - sin(t1) * cos(t2)
            if (abs(den) < 1e-6) return quad
            val t = ((x2 - x1) * sin(t2) - (y2 - y1) * cos(t2)) / den
            Point(x1 + t * cos(t1), y1 + t * sin(t1))
        }
    }

    internal fun otsu(v: FloatArray): Double {
        val hist = IntArray(256)
        for (p in v) hist[p.toInt().coerceIn(0, 255)]++
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var t = 0
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = v.size - wB
            if (wF == 0) break
            sumB += i.toDouble() * hist[i]
            val diff = sumB / wB - (sum - sumB) / wF
            val between = wB.toDouble() * wF * diff * diff
            if (between > best) {
                best = between
                t = i
            }
        }
        return t.toDouble()
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int) = morph(m, w, h, r, grow = true)

    private fun erode(m: BooleanArray, w: Int, h: Int, r: Int) = morph(m, w, h, r, grow = false)

    /** A square structuring element of radius [r], applied as two 1D passes. */
    private fun morph(m: BooleanArray, w: Int, h: Int, r: Int, grow: Boolean): BooleanArray {
        fun pass(src: BooleanArray, horizontal: Boolean): BooleanArray {
            val out = BooleanArray(src.size)
            for (y in 0 until h) for (x in 0 until w) {
                var v = !grow
                for (d in -r..r) {
                    val i = if (horizontal) y * w + (x + d).coerceIn(0, w - 1) else (y + d).coerceIn(0, h - 1) * w + x
                    if (src[i] == grow) {
                        v = grow
                        break
                    }
                }
                out[y * w + x] = v
            }
            return out
        }
        return pass(pass(m, horizontal = true), horizontal = false)
    }

    /** 8-connected components of [m], as lists of pixel indices. */
    private fun components(m: BooleanArray, w: Int, h: Int): List<IntArray> {
        val seen = BooleanArray(m.size)
        val stack = IntArray(m.size)
        val out = ArrayList<IntArray>()
        val members = IntArrayList()
        for (start in m.indices) {
            if (!m[start] || seen[start]) continue
            members.clear()
            var top = 0
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val p = stack[--top]
                members.add(p)
                val x = p % w
                val y = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (m[q] && !seen[q]) {
                        seen[q] = true
                        stack[top++] = q
                    }
                }
            }
            out += members.toIntArray()
        }
        return out
    }

    /** Andrew's monotone chain. */
    internal fun convexHull(points: List<Point>): List<Point> {
        val p = points.sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        if (p.size < 3) return p
        fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        fun chain(seq: List<Point>): List<Point> {
            val out = ArrayList<Point>()
            for (pt in seq) {
                while (out.size >= 2 && cross(out[out.size - 2], out.last(), pt) <= 0) out.removeAt(out.size - 1)
                out += pt
            }
            return out
        }
        return chain(p).dropLast(1) + chain(p.asReversed()).dropLast(1)
    }

    private class IntArrayList {
        private var data = IntArray(256)
        private var size = 0

        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun clear() {
            size = 0
        }

        fun toIntArray(): IntArray = data.copyOf(size)
    }
}
