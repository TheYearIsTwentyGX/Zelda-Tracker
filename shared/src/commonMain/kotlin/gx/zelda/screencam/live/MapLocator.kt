package gx.zelda.screencam.live

import gx.zelda.art.ArtIds
import gx.zelda.art.GameArt
import gx.zelda.map.data.MapTiles
import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Grey map tiles by pyramid level, row and column (see [MapTiles]). */
fun interface TileSource {
    suspend fun tile(level: Int, row: Int, col: Int): GrayImage?
}

/** The map's tiles, from an art pack or the app ([GameArt]). */
val BundledTiles = TileSource { level, row, col ->
    GameArt.tile(ArtIds.BOTW_WORLD, level, row, col, MapTiles.path(level, row, col))?.let(::decodeGrayImage)
}

/** Assembles regions of the tile pyramid, keeping recently used tiles decoded. */
class TileMosaic(private val source: TileSource, private val capacity: Int = 96) {
    private val cache = LinkedHashMap<Long, GrayImage?>(capacity, 0.75f, true)

    private suspend fun tile(level: Int, row: Int, col: Int): GrayImage? {
        val key = (level.toLong() shl 40) or (row.toLong() shl 20) or col.toLong()
        if (cache.containsKey(key)) return cache[key]
        val t = source.tile(level, row, col)
        cache[key] = t
        if (cache.size > capacity) cache.remove(cache.keys.first())
        return t
    }

    /** [w]×[h] pixels of [level] from pixel ([x0], [y0]); outside the map is sea grey. */
    suspend fun region(level: Int, x0: Int, y0: Int, w: Int, h: Int): GrayImage {
        val out = FloatArray(w * h) { SEA }
        val size = MapTiles.TILE_SIZE
        for (row in floorDiv(y0, size)..floorDiv(y0 + h - 1, size)) for (col in floorDiv(x0, size)..floorDiv(x0 + w - 1, size)) {
            if (row < 0 || col < 0) continue
            val t = tile(level, row, col) ?: continue
            val ty0 = row * size
            val tx0 = col * size
            for (y in max(y0, ty0) until min(y0 + h, ty0 + t.height)) {
                for (x in max(x0, tx0) until min(x0 + w, tx0 + t.width)) out[(y - y0) * w + x - x0] = t[x - tx0, y - ty0]
            }
        }
        return GrayImage(w, h, out)
    }

    private fun floorDiv(a: Int, b: Int) = floor(a.toDouble() / b).toInt()

    companion object {
        /** The tiles' padding colour (40, 60, 70) in grey. */
        const val SEA = 54f
    }
}

/**
 * Where on the map a max-zoom view is. The view is cut from the screen (through the tracker's
 * homography) at half a pixel per world unit, like tile level 2, and matched against the tiles:
 * near a predicted spot while tracking, or over the whole map to start.
 */
class MapLocator(private val mosaic: TileMosaic) {
    /** World position of the view's top-left corner, and how clearly the match stood out. */
    class Fix(val viewOrigin: Point, val peak: Double, val ratio: Double) {
        val good: Boolean get() = peak >= MIN_PEAK && ratio >= MIN_RATIO
    }

    /** The screen's map viewport as a level-2 image (half a pixel per world unit). */
    fun viewImage(frame: GrayImage, screenToFrame: Homography): GrayImage {
        val step = 2 * MAX_ZOOM_SCALE // canonical px per output px
        val out = FloatArray(VIEW_W * VIEW_H)
        val m = screenToFrame.m
        for (j in 0 until VIEW_H) for (i in 0 until VIEW_W) {
            var s = 0f
            var n = 0
            for (sy in 0..1) for (sx in 0..1) {
                val x = VIEW_X0 + (i + 0.25 + 0.5 * sx) * step
                val y = VIEW_Y0 + (j + 0.25 + 0.5 * sy) * step
                val w = m[6] * x + m[7] * y + m[8]
                val v = frame.sample((m[0] * x + m[1] * y + m[2]) / w - 0.5, (m[3] * x + m[4] * y + m[5]) / w - 0.5)
                if (!v.isNaN()) {
                    s += v
                    n++
                }
            }
            out[j * VIEW_W + i] = if (n > 0) s / n else Float.NaN
        }
        // Fill anything off-frame with the mean, so it neither matches nor mismatches.
        val mean = out.filter { !it.isNaN() }.average().toFloat()
        for (k in out.indices) if (out[k].isNaN()) out[k] = mean
        return GrayImage(VIEW_W, VIEW_H, out)
    }

    /** Matches [view] within [radius] world units of [predicted] (the view's top-left in world units). */
    suspend fun near(view: GrayImage, predicted: Point, radius: Double = 100.0): Fix {
        val (px1, py1, coarse) = coarse(view, predicted, radius)
        val r1 = (radius / 4).toInt()
        // Level 2: refine around it.
        val t2 = highPass(view)
        val cx2 = 2 * (px1 - r1 + coarse.x)
        val cy2 = 2 * (py1 - r1 + coarse.y)
        val r2 = 3
        val m2 = highPass(mosaic.region(2, cx2 - r2 - PAD, cy2 - r2 - PAD, t2.width + 2 * (r2 + PAD), t2.height + 2 * (r2 + PAD)))
        val fine = search(m2, t2, PAD, PAD, 2 * r2 + 1, 2 * r2 + 1)
        val x = cx2 - r2 + fine.x + fine.subX
        val y = cy2 - r2 + fine.y + fine.subY
        return Fix(Point(x * 2 - 5000.0, y * 2 - 4000.0), fine.peak, coarse.ratio)
    }

    /** A canonical point on the (tracked) screen and the world point the map shows there. */
    class Correspondence(val canonical: Point, val world: Point, val peak: Double)

    /**
     * Matches a grid of blocks of [view] within [radius] level-2 pixels (two world units each) of
     * where a view at [viewOrigin] (world units, top-left) puts them. A screen homography that has drifted bends the view, so the blocks
     * land in slightly different places, and together they say how to straighten it.
     */
    suspend fun blocks(view: GrayImage, viewOrigin: Point, radius: Int = 5): List<Correspondence> {
        val hp = highPass(view)
        val bw = VIEW_W / BLOCK_COLS
        val bh = VIEW_H / BLOCK_ROWS
        val ex = (viewOrigin.x + 5000) / 2
        val ey = (viewOrigin.y + 4000) / 2
        val bx0 = floor(ex).toInt() - radius - PAD
        val by0 = floor(ey).toInt() - radius - PAD
        val map = highPass(mosaic.region(2, bx0, by0, VIEW_W + 2 * (radius + PAD) + 1, VIEW_H + 2 * (radius + PAD) + 1))
        val out = ArrayList<Correspondence>()
        for (r in 0 until BLOCK_ROWS) for (c in 0 until BLOCK_COLS) {
            val t = GrayImage(bw, bh, FloatArray(bw * bh) { hp[c * bw + it % bw, r * bh + it / bw] })
            if (t.pixels.sumOf { it.toDouble() * it } / t.pixels.size < MIN_BLOCK_ENERGY) continue
            // The block's expected place in [map], rounded; search around it.
            val ox = floor(ex).toInt() + c * bw - bx0 - radius
            val oy = floor(ey).toInt() + r * bh - by0 - radius
            val peak = search(map, t, ox, oy, 2 * radius + 1, 2 * radius + 1)
            if (peak.peak < MIN_BLOCK_PEAK) continue
            val mx = bx0 + ox + peak.x + peak.subX
            val my = by0 + oy + peak.y + peak.subY
            val world = Point(mx * 2 - 5000 + bw, my * 2 - 4000 + bh)
            val canonical = Point(VIEW_X0 + (c * bw + bw / 2.0) * 2 * MAX_ZOOM_SCALE, VIEW_Y0 + (r * bh + bh / 2.0) * 2 * MAX_ZOOM_SCALE)
            out += Correspondence(canonical, world, peak.peak)
        }
        return out
    }

    /** Level 1 (a quarter pixel per unit) over [radius]: the best offset's level-1 corner and its peak. */
    private suspend fun coarse(view: GrayImage, predicted: Point, radius: Double): Triple<Int, Int, Peak> {
        val t1 = highPass(view.half())
        val r1 = (radius / 4).toInt()
        val px1 = pixelX(predicted.x, 1)
        val py1 = pixelZ(predicted.y, 1)
        val m1 = highPass(mosaic.region(1, px1 - r1 - PAD, py1 - r1 - PAD, t1.width + 2 * (r1 + PAD), t1.height + 2 * (r1 + PAD)))
        return Triple(px1, py1, search(m1, t1, PAD, PAD, 2 * r1 + 1, 2 * r1 + 1))
    }

    private var overview: GrayImage? = null
    private var overviewIntegral: Integral? = null

    /** Builds the whole-map overview [anywhere] needs, ahead of time (it decodes 20 tiles). */
    suspend fun prepare() {
        if (overview == null) {
            val map = highPass(mosaic.region(0, 0, 0, 1250, 1000).half())
            overview = map
            overviewIntegral = Integral(map)
        }
    }

    /** Matches [view] over the whole map: a coarse pass at level 0, then [near] on the best few. */
    suspend fun anywhere(view: GrayImage): Fix? {
        prepare()
        val map = checkNotNull(overview)
        val integral = checkNotNull(overviewIntegral)
        // Level 0 halved is 1/16 px per unit: the view is 8× smaller than at level 2.
        val t = highPass(view.half().half().half())
        val tz = zeroMean(t)
        val n = t.width * t.height
        val tNorm = sqrt(tz.sumOf { it.toDouble() * it })
        val nx = map.width - t.width + 1
        val ny = map.height - t.height + 1
        val scores = DoubleArray(nx * ny)
        // Every other position: 32 world units apart, inside the ±40 each candidate is re-searched.
        for (y in 0 until ny step 2) for (x in 0 until nx step 2) {
            val varM = integral.variance(x, y, t.width, t.height)
            if (varM < MIN_MAP_VARIANCE) continue
            var sum = 0.0
            for (ty in 0 until t.height) {
                val mo = (y + ty) * map.width + x
                val to = ty * t.width
                for (tx in 0 until t.width) sum += tz[to + tx] * map.pixels[mo + tx]
            }
            scores[y * nx + x] = sum / (sqrt(varM * n) * tNorm)
        }
        // The best few peaks, each clearing its neighbourhood before the next is taken.
        val best = ArrayList<Point>()
        repeat(CANDIDATES) {
            var k = -1
            for (i in scores.indices) if (scores[i] > 0 && (k < 0 || scores[i] > scores[k])) k = i
            if (k < 0) return@repeat
            val x = k % nx
            val y = k / nx
            best += Point(x * 16 - 5000.0, y * 16 - 4000.0)
            for (yy in max(0, y - 5)..min(ny - 1, y + 5)) for (xx in max(0, x - 5)..min(nx - 1, x + 5)) scores[yy * nx + xx] = 0.0
        }
        // Check each at level 1 only; refine just the winner at level 2.
        var winner: Point? = null
        var winnerPeak = -1.0
        for (p in best) {
            val peak = coarse(view, p, 40.0).third.peak
            if (peak > winnerPeak) {
                winnerPeak = peak
                winner = p
            }
        }
        return winner?.let { near(view, it, radius = 40.0) }
    }

    private class Peak(val x: Int, val y: Int, val subX: Double, val subY: Double, val peak: Double, val ratio: Double)

    /** Zero-mean NCC of [t] over [m] at offsets (ox + 0..nx-1, oy + 0..ny-1), with sub-pixel refinement. */
    private fun search(m: GrayImage, t: GrayImage, ox: Int, oy: Int, nx: Int, ny: Int): Peak {
        val tz = zeroMean(t)
        val tn = sqrt(tz.sumOf { it.toDouble() * it })
        val scores = DoubleArray(nx * ny)
        val n = t.width * t.height
        for (j in 0 until ny) for (i in 0 until nx) {
            var sm = 0.0
            var smm = 0.0
            var smt = 0.0
            for (ty in 0 until t.height) {
                val mo = (oy + j + ty) * m.width + ox + i
                val to = ty * t.width
                for (tx in 0 until t.width) {
                    val v = m.pixels[mo + tx].toDouble()
                    sm += v
                    smm += v * v
                    smt += v * tz[to + tx]
                }
            }
            val vm = smm - sm * sm / n
            scores[j * nx + i] = if (vm > 1e-6 && tn > 1e-6) smt / (sqrt(vm) * tn) else 0.0
        }
        var bi = 0
        for (k in scores.indices) if (scores[k] > scores[bi]) bi = k
        val bx = bi % nx
        val by = bi / nx
        var second = 0.0
        for (k in scores.indices) {
            if (abs(k % nx - bx) <= 2 && abs(k / nx - by) <= 2) continue
            second = max(second, scores[k])
        }
        fun parabola(a: Double, b: Double, c: Double): Double {
            val d = a - 2 * b + c
            return if (abs(d) < 1e-9) 0.0 else (0.5 * (a - c) / d).coerceIn(-0.5, 0.5)
        }
        val sx = if (bx in 1 until nx - 1) parabola(scores[bi - 1], scores[bi], scores[bi + 1]) else 0.0
        val sy = if (by in 1 until ny - 1) parabola(scores[bi - nx], scores[bi], scores[bi + nx]) else 0.0
        val peak = scores[bi]
        return Peak(bx, by, sx, sy, peak, if (second > 1e-6) peak / second else 99.0)
    }

    private fun zeroMean(t: GrayImage): FloatArray {
        val mean = t.pixels.average().toFloat()
        return FloatArray(t.pixels.size) { t.pixels[it] - mean }
    }

    /** Summed-area tables for window variance. */
    private class Integral(img: GrayImage) {
        val w = img.width + 1
        val s = DoubleArray(w * (img.height + 1))
        val ss = DoubleArray(w * (img.height + 1))

        init {
            for (y in 0 until img.height) {
                var row = 0.0
                var row2 = 0.0
                for (x in 0 until img.width) {
                    val v = img[x, y].toDouble()
                    row += v
                    row2 += v * v
                    s[(y + 1) * w + x + 1] = s[y * w + x + 1] + row
                    ss[(y + 1) * w + x + 1] = ss[y * w + x + 1] + row2
                }
            }
        }

        fun variance(x: Int, y: Int, tw: Int, th: Int): Double {
            fun box(a: DoubleArray) = a[(y + th) * w + x + tw] - a[y * w + x + tw] - a[(y + th) * w + x] + a[y * w + x]
            val n = (tw * th).toDouble()
            val sum = box(s)
            return (box(ss) - sum * sum / n) / n
        }
    }

    companion object {
        /** Canonical pixels per world unit on the fully zoomed-in map. */
        const val MAX_ZOOM_SCALE = 2.985

        /** The part of the canonical screen used for matching: map, not frame or most of the HUD. */
        const val VIEW_X0 = 300.0
        const val VIEW_Y0 = 150.0
        private const val VIEW_X1 = 1650.0
        private const val VIEW_Y1 = 900.0
        val VIEW_W = ((VIEW_X1 - VIEW_X0) / (2 * MAX_ZOOM_SCALE)).toInt()
        val VIEW_H = ((VIEW_Y1 - VIEW_Y0) / (2 * MAX_ZOOM_SCALE)).toInt()

        /** Margin cut around searched map regions so the high-pass has context. */
        private const val PAD = 8
        const val MIN_PEAK = 0.4
        const val MIN_RATIO = 1.25
        private const val CANDIDATES = 12
        private const val BLOCK_COLS = 4
        private const val BLOCK_ROWS = 3
        private const val MIN_BLOCK_PEAK = 0.4

        /** Mean squared high-pass level a block needs to be worth matching (not flat sea or snow). */
        private const val MIN_BLOCK_ENERGY = 6.0

        /** Flat map windows (sea, blank land) are skipped in the whole-map search. */
        private const val MIN_MAP_VARIANCE = 4.0

        /** Pixel column of world x in tile [level] (level 3 is a pixel per unit). */
        fun pixelX(x: Double, level: Int): Int = floor((x + 5000) / (1 shl (3 - level))).toInt()

        /** Pixel row of world z in tile [level]. */
        fun pixelZ(z: Double, level: Int): Int = floor((z + 4000) / (1 shl (3 - level))).toInt()

        /** A high-pass copy: the image minus its local mean, so brightness and contrast drift don't matter. */
        fun highPass(img: GrayImage, radius: Int = 6): GrayImage {
            val blur = img.pixels.copyOf()
            boxBlur(blur, img.width, img.height, radius)
            val light = img.pixels.copyOf()
            boxBlur(light, img.width, img.height, 1)
            return GrayImage(img.width, img.height, FloatArray(blur.size) { light[it] - blur[it] })
        }

        private fun boxBlur(v: FloatArray, w: Int, h: Int, r: Int) {
            val tmp = FloatArray(v.size)
            val size = 2f * r + 1
            for (row in 0 until h) {
                val o = row * w
                var s = 0f
                for (i in -r..r) s += v[o + i.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[o + x] = s / size
                    s += v[o + (x + r + 1).coerceAtMost(w - 1)] - v[o + (x - r).coerceAtLeast(0)]
                }
            }
            for (x in 0 until w) {
                var s = 0f
                for (i in -r..r) s += tmp[i.coerceIn(0, h - 1) * w + x]
                for (row in 0 until h) {
                    v[row * w + x] = s / size
                    s += tmp[(row + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(row - r).coerceAtLeast(0) * w + x]
                }
            }
        }
    }
}
