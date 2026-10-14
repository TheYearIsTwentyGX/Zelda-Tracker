package gx.zelda.shrines.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Reads shrine states from a screenshot of the BotW map: Switch album, 1920×1080, 2D map, fully
 * zoomed out. A port of the Python prototype in `tools/shrine-map-spike` (branch
 * `worktree-shrine-map-spike`, see its REPORT.md), plus Hero's Path handling.
 *
 * 1. Colour masks over the whole frame (OpenCV's 8-bit HSV, so the prototype's thresholds carry over).
 * 2. Candidate shrine and tower icons: connected components of the icon-blue masks, confirmed by
 *    template matching around each component.
 * 3. Registration: at minimum zoom the map is world × [ShrineMapData.minZoomScale] plus a
 *    translation. Candidates vote for a translation against the world positions of their own
 *    class, and the inliers are averaged.
 * 4. Each shrine's projected spot is classified from a window around it.
 *
 * Hero's Path mode draws green trail lines over the map and its icons. Trail pixels are treated as
 * unknown rather than as evidence either way: icons are matched on the pixels around the trail, and
 * a spot only counts as empty when at least half its icon outline is free of trail.
 */
class ShrineMapScanner(private val data: ShrineMapData) {
    private val shrineIds = data.shrines.keys.toList()
    private val shrinePoints = shrineIds.map { data.shrines.getValue(it) }

    /** Shrines drawn almost exactly on top of another at minimum zoom, with the other's id. */
    val stacked: Map<String, String> = buildMap {
        for ((a, pa) in data.shrines) for ((b, pb) in data.shrines) {
            if (a != b && hypot(pa.x - pb.x, pa.z - pb.z) * data.minZoomScale < STACK_PX) put(a, b)
        }
    }

    fun scan(image: MapImage): ShotScan {
        if (image.width != WIDTH || image.height != HEIGHT) {
            return ShotScan.Rejected(
                RejectReason.WRONG_SIZE,
                "This image is ${image.width}×${image.height}; screenshots straight from the Switch album are " +
                    "${WIDTH}×$HEIGHT. Share the original screenshot, not a photo, crop or resized copy.",
            )
        }
        val m = MapMasks.of(image)
        val shrineDets = detectShrines(m)
        val towerDets = detectTowers(m)
        val groups = listOf(shrineDets to shrinePoints, towerDets to data.towers)
        // Most icons found must fit; three that line up by chance on a zoomed-in map don't count.
        val reg = register(groups, data.minZoomScale)
            ?.takeIf { it.anchors >= MIN_FIT_SHARE * (shrineDets.size + towerDets.size) }
        if (reg == null) {
            val free = registerFreeScale(groups)
            return if (free != null && abs(free.fittedScale / data.minZoomScale - 1) > SCALE_TOLERANCE) {
                notMinZoom()
            } else {
                ShotScan.Rejected(
                    RejectReason.TOO_FEW_ICONS,
                    "Couldn't line this screenshot up with the map: it needs at least $MIN_ANCHORS shrine or tower " +
                        "icons. Use the 2D map, fully zoomed out, with icons shown.",
                )
            }
        }
        if (abs(reg.fittedScale / data.minZoomScale - 1) > SCALE_TOLERANCE) return notMinZoom()
        val heroesPath = m.isHeroesPath()
        val readings = shrineIds.indices.associate { i ->
            val p = shrinePoints[i]
            val id = shrineIds[i]
            id to classify(m, reg.scale * p.x + reg.tx, reg.scale * p.z + reg.ty, stacked[id], heroesPath)
        }
        return ShotScan.Read(reg, readings, heroesPath)
    }

    private fun notMinZoom() = ShotScan.Rejected(
        RejectReason.NOT_MIN_ZOOM,
        "This map isn't fully zoomed out. Zoom all the way out (right stick) and take the screenshot again.",
    )

    // --- detection ---------------------------------------------------------------------------

    internal data class Detection(val x: Double, val y: Double, val score: Double)

    /** Shrine diamonds; both states share the blue outline, so one ring template finds both. */
    internal fun detectShrines(m: MapMasks): List<Detection> =
        detect(m, MapMasks.BLUE, RING_TEMPLATE, sizes = 8..36, dyGuess = 0, search = 6, threshold = 0.55)

    /** Sheikah Towers. The template is centred on the world point, a little below the icon's middle. */
    internal fun detectTowers(m: MapMasks): List<Detection> =
        detect(m, MapMasks.TOWER, data.towerTemplate, sizes = 12..44, dyGuess = 2, search = 6, threshold = 0.6)

    private fun detect(
        m: MapMasks,
        bits: Int,
        template: IconTemplate,
        sizes: IntRange,
        dyGuess: Int,
        search: Int,
        threshold: Double,
    ): List<Detection> {
        val found = mutableListOf<Detection>()
        for (blob in m.components(bits, VIEW_X0, VIEW_Y0, VIEW_X1, VIEW_Y1)) {
            if (blob.width !in sizes || blob.height !in sizes) continue
            val bx = (blob.minX + blob.maxX) / 2
            val by = (blob.minY + blob.maxY) / 2 + dyGuess
            var best = Detection(0.0, 0.0, -1.0)
            for (dy in -search..search) for (dx in -search..search) {
                val s = m.ncc(bits, template, bx + dx, by + dy, maskTrail = false)
                if (s > best.score) best = Detection((bx + dx).toDouble(), (by + dy).toDouble(), s)
            }
            if (best.score >= threshold && inView(best.x, best.y)) found += best
        }
        // Pieces of one icon (split by a trail line, say) find the same spot: keep the best.
        return found.sortedByDescending { it.score }.fold(mutableListOf<Detection>()) { kept, d ->
            if (kept.none { hypot(it.x - d.x, it.y - d.y) < NMS_PX }) kept += d
            kept
        }
    }

    // --- registration ------------------------------------------------------------------------

    private class Pairs(val world: List<WorldPoint>, val screen: List<Detection>)

    /** Translation-only fit at [scale]; null when fewer than [MIN_ANCHORS] icons agree. */
    internal fun register(groups: List<Pair<List<Detection>, List<WorldPoint>>>, scale: Double): MapRegistration? {
        val (votes, t0) = vote(groups, scale) ?: return null
        if (votes < MIN_ANCHORS) return null
        var tx = t0.first
        var ty = t0.second
        repeat(4) {
            val p = match(groups, scale, tx, ty)
            if (p.world.size < 2) return@repeat
            tx = p.screen.indices.sumOf { p.screen[it].x - scale * p.world[it].x } / p.world.size
            ty = p.screen.indices.sumOf { p.screen[it].y - scale * p.world[it].z } / p.world.size
        }
        return finish(groups, scale, tx, ty)
    }

    /**
     * A scale-and-translation fit over a wide range of scales, only used to tell a zoomed-in map
     * from one that just has too few icons.
     */
    internal fun registerFreeScale(groups: List<Pair<List<Detection>, List<WorldPoint>>>): MapRegistration? {
        var best: Triple<Int, Double, Pair<Double, Double>>? = null
        var s = FREE_SCALE_MIN
        while (s <= FREE_SCALE_MAX) {
            val v = vote(groups, s)
            if (v != null && v.first > (best?.first ?: 0)) best = Triple(v.first, s, v.second)
            s *= FREE_SCALE_STEP
        }
        val (votes, s0, t0) = best ?: return null
        if (votes < FREE_MIN_ANCHORS) return null
        var scale = s0
        var tx = t0.first
        var ty = t0.second
        repeat(4) {
            val p = match(groups, scale, tx, ty)
            if (p.world.size < 3) return@repeat
            val fit = leastSquares(p)
            scale = fit.first
            tx = fit.second
            ty = fit.third
        }
        return finish(groups, scale, tx, ty)?.takeIf { it.anchors >= FREE_MIN_ANCHORS }
    }

    private fun finish(groups: List<Pair<List<Detection>, List<WorldPoint>>>, scale: Double, tx: Double, ty: Double): MapRegistration? {
        val p = match(groups, scale, tx, ty)
        if (p.world.size < MIN_ANCHORS) return null
        val sq = p.world.indices.sumOf {
            val ex = p.screen[it].x - (scale * p.world[it].x + tx)
            val ey = p.screen[it].y - (scale * p.world[it].z + ty)
            ex * ex + ey * ey
        }
        return MapRegistration(scale, tx, ty, p.world.size, sqrt(sq / p.world.size), leastSquares(p).first)
    }

    /** The 4 px translation bin (and its neighbours) that most detection–world pairs agree on. */
    private fun vote(groups: List<Pair<List<Detection>, List<WorldPoint>>>, scale: Double): Pair<Int, Pair<Double, Double>>? {
        val counts = HashMap<Long, Int>()
        for ((dets, world) in groups) for (d in dets) for (w in world) {
            val bx = ((d.x - scale * w.x) / VOTE_BIN).roundToLong()
            val by = ((d.y - scale * w.z) / VOTE_BIN).roundToLong()
            val key = key(bx, by)
            counts[key] = (counts[key] ?: 0) + 1
        }
        if (counts.isEmpty()) return null
        var best = -1
        var bestKey = 0L
        for (k in counts.keys) {
            val bx = k shr 32
            val by = k.toInt().toLong()
            var total = 0
            for (i in -1..1) for (j in -1..1) total += counts[key(bx + i, by + j)] ?: 0
            if (total > best) {
                best = total
                bestKey = k
            }
        }
        return best to ((bestKey shr 32) * VOTE_BIN.toDouble() to bestKey.toInt() * VOTE_BIN.toDouble())
    }

    private fun key(bx: Long, by: Long): Long = (bx shl 32) or (by and 0xffffffffL)

    /** Nearest-neighbour (world, detection) pairs within [MATCH_PX], one detection per world point. */
    private fun match(groups: List<Pair<List<Detection>, List<WorldPoint>>>, scale: Double, tx: Double, ty: Double): Pairs {
        val world = mutableListOf<WorldPoint>()
        val screen = mutableListOf<Detection>()
        for ((dets, points) in groups) {
            val byPoint = HashMap<Int, Pair<Detection, Double>>()
            for (d in dets) {
                var bestJ = -1
                var bestDist = Double.MAX_VALUE
                points.forEachIndexed { j, w ->
                    val dist = hypot(d.x - (scale * w.x + tx), d.y - (scale * w.z + ty))
                    if (dist < bestDist) {
                        bestDist = dist
                        bestJ = j
                    }
                }
                if (bestJ >= 0 && bestDist < MATCH_PX && bestDist < (byPoint[bestJ]?.second ?: Double.MAX_VALUE)) {
                    byPoint[bestJ] = d to bestDist
                }
            }
            for ((j, pair) in byPoint) {
                world += points[j]
                screen += pair.first
            }
        }
        return Pairs(world, screen)
    }

    /** Least squares for one scale shared by both axes plus a translation: (scale, tx, ty). */
    private fun leastSquares(p: Pairs): Triple<Double, Double, Double> {
        val n = p.world.size
        val mwx = p.world.sumOf { it.x } / n
        val mwz = p.world.sumOf { it.z } / n
        val mdx = p.screen.sumOf { it.x } / n
        val mdy = p.screen.sumOf { it.y } / n
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val wx = p.world[i].x - mwx
            val wz = p.world[i].z - mwz
            num += wx * (p.screen[i].x - mdx) + wz * (p.screen[i].y - mdy)
            den += wx * wx + wz * wz
        }
        val s = if (den > 0) num / den else 0.0
        return Triple(s, mdx - s * mwx, mdy - s * mwz)
    }

    // --- classification ----------------------------------------------------------------------

    internal fun classify(m: MapMasks, u: Double, v: Double, stackedWith: String?, heroesPath: Boolean): SpotReading {
        if (!inView(u, v)) return SpotReading.unsure(UnsureReason.NOT_IN_SHOT, "Not in this screenshot")
        if (!inView(u, v, VIEW_MARGIN)) return SpotReading.unsure(UnsureReason.SCREEN_EDGE)
        if (underHud(u, v, heroesPath)) return SpotReading.unsure(UnsureReason.COVERED, "Under the map's HUD")
        val u0 = u.roundToInt()
        val v0 = v.roundToInt()

        // Where the outline fits best, within a few pixels of the projection.
        var ring = 0.0
        var cx = u0
        var cy = v0
        for (dy in -RING_SEARCH..RING_SEARCH) for (dx in -RING_SEARCH..RING_SEARCH) {
            val f = m.fraction(MapMasks.BLUE, u0 + dx, v0 + dy, RING) ?: continue
            if (f > ring) {
                ring = f
                cx = u0 + dx
                cy = v0 + dy
            }
        }
        var shape = 0.0
        for (dy in -SHAPE_SEARCH..SHAPE_SEARCH) for (dx in -SHAPE_SEARCH..SHAPE_SEARCH) {
            // The masked score reads an icon through a trail; the plain one is the prototype's.
            shape = max(shape, max(
                m.ncc(MapMasks.ICON, data.shrineTemplate, cx + dx, cy + dy, maskTrail = false),
                m.ncc(MapMasks.ICON, data.shrineTemplate, cx + dx, cy + dy, maskTrail = true),
            ))
        }
        val outer = m.fraction(MapMasks.BLUE, cx, cy, OUTER_BAND) ?: 0.0

        if (shape >= SHAPE_HI && outer <= OUTER_BLUE_MAX) {
            val blue = m.fraction(MapMasks.BLUE, cx, cy, GLYPH) ?: 0.0
            val orange = m.fraction(MapMasks.ORANGE, cx, cy, GLYPH) ?: 0.0
            val verdict = when {
                orange > GLYPH_MIN && orange > 2 * blue -> ScanVerdict.NOT_DONE
                blue > GLYPH_MIN && blue > 2 * orange -> ScanVerdict.COMPLETED
                else -> return SpotReading.unsure(UnsureReason.COVERED, "Icon found, but something covers its glyph", iconSeen = true)
            }
            if (stackedWith != null) {
                return SpotReading.unsure(UnsureReason.STACKED, "Drawn on top of another shrine's icon", iconSeen = true)
            }
            return SpotReading(verdict, note = "shape ${fmt(shape)}, glyph blue ${fmt(blue)} orange ${fmt(orange)}")
        }
        if (shape >= SHAPE_LO || ring >= RING_LO) {
            val note = if (outer > OUTER_BLUE_MAX) "A bigger blue icon is on top" else "Only part of an icon is visible"
            return SpotReading.unsure(UnsureReason.COVERED, note, iconSeen = ring >= RING_LO)
        }
        var worst = 0
        var worstFrac = 0.0
        for (bit in CLUTTER) {
            val f = m.fraction(bit, u0, v0, FOOTPRINT) ?: 0.0
            if (f > worstFrac) {
                worstFrac = f
                worst = bit
            }
        }
        if (worstFrac > CLUTTER_MAX) return SpotReading.unsure(UnsureReason.COVERED, "Covered by ${clutterName(worst)}")
        if (m.visible(u0, v0, RING) < MIN_VISIBLE_RING) {
            return SpotReading.unsure(UnsureReason.COVERED, "Covered by the Hero's Path trail")
        }
        return SpotReading(ScanVerdict.NOT_PRESENT, note = "clear spot")
    }

    private fun clutterName(bit: Int) = when (bit) {
        MapMasks.GLOW -> "a glowing icon"
        MapMasks.TAN -> "a village or stable icon"
        MapMasks.WHITE -> "a white icon or label"
        MapMasks.PURPLE -> "a pin or its beam"
        MapMasks.YELLOW -> "a stamp"
        else -> "a pin or marker"
    }

    private fun fmt(d: Double) = ((d * 100).roundToInt() / 100.0).toString()

    companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080

        /** The map viewport inside the frame; icons dim past these rows. */
        const val VIEW_X0 = 236
        const val VIEW_Y0 = 126
        const val VIEW_X1 = 1704
        const val VIEW_Y1 = 920

        /** A spot is only judged if its whole icon footprint is inside the viewport. */
        const val VIEW_MARGIN = 14

        /**
         * Fixed HUD over the map, padded by [HUD_PAD]: completion %, clock and temperature, and the
         * Sheikah Sensor. The cursor moves, so it's caught as clutter instead.
         */
        private val HUD_RECTS = listOf(
            intArrayOf(60, 770, 240, 950),
            intArrayOf(1540, 880, 1800, 950),
        )

        /** Hero's Path mode swaps that HUD for a timeline slider. */
        private val HEROES_PATH_HUD_RECTS = listOf(intArrayOf(280, 890, 460, 945))
        private const val HUD_PAD = 14

        const val MIN_ANCHORS = 3

        /**
         * Share of detected icons the minimum-zoom fit must explain. Real shots explain 90% or more
         * (a stray match on the Shrine of Resurrection, say, accounts for the rest).
         */
        private const val MIN_FIT_SHARE = 0.5

        /** A fitted scale this far (as a fraction) from minimum zoom's means the map is zoomed in. */
        const val SCALE_TOLERANCE = 0.03
        private const val FREE_SCALE_MIN = 0.1
        private const val FREE_SCALE_MAX = 1.6
        private const val FREE_SCALE_STEP = 1.004
        private const val FREE_MIN_ANCHORS = 5

        private const val VOTE_BIN = 4
        private const val MATCH_PX = 6.0
        private const val NMS_PX = 10.0

        /** Shrines closer than this at minimum zoom draw on top of each other. */
        const val STACK_PX = 8.0

        private const val RING_SEARCH = 3
        private const val SHAPE_SEARCH = 2

        /** Silhouette score above which the spot holds a shrine icon. */
        private const val SHAPE_HI = 0.60

        /** Below this (and [RING_LO]) there's no shrine-like shape at all. */
        private const val SHAPE_LO = 0.35

        /** Fraction of the outline that's icon-blue for "some outline visible". */
        private const val RING_LO = 0.25

        /** More blue than this just outside the icon: a bigger blue icon is drawn on top. */
        private const val OUTER_BLUE_MAX = 0.40
        private const val GLYPH_MIN = 0.2
        private const val CLUTTER_MAX = 0.03

        /** An empty spot needs at least this much of its outline free of Hero's Path trail. */
        private const val MIN_VISIBLE_RING = 0.5

        private val CLUTTER = intArrayOf(MapMasks.GLOW, MapMasks.TAN, MapMasks.WHITE, MapMasks.PURPLE, MapMasks.YELLOW, MapMasks.RED)

        /** Half-diagonal of the shrine diamond and the inside of its outline, in pixels. */
        private const val R_OUTER = 12.5
        private const val R_INNER = 9.0

        private val RING = Offsets.l1Band(R_INNER, R_OUTER)
        private val OUTER_BAND = Offsets.l1Band(14.0, 18.0)
        private val GLYPH = Offsets.l1Band(0.0, 7.0)
        private val FOOTPRINT = Offsets.l1Band(0.0, 14.0)

        /** The outline as a 33×33 template, for finding diamonds. */
        private val RING_TEMPLATE: IconTemplate = run {
            val r = 16
            val size = 2 * r + 1
            val values = FloatArray(size * size)
            for (y in -r..r) for (x in -r..r) {
                val d = abs(x) + abs(y)
                if (d >= R_INNER && d <= R_OUTER) values[(y + r) * size + x + r] = 1f
            }
            IconTemplate(size, values)
        }

        fun inView(u: Double, v: Double, margin: Int = 0): Boolean =
            u >= VIEW_X0 + margin && u <= VIEW_X1 - margin && v >= VIEW_Y0 + margin && v <= VIEW_Y1 - margin

        private fun underHud(u: Double, v: Double, heroesPath: Boolean): Boolean =
            (if (heroesPath) HEROES_PATH_HUD_RECTS else HUD_RECTS).any { (x0, y0, x1, y1) ->
                u >= x0 - HUD_PAD && u <= x1 + HUD_PAD && v >= y0 - HUD_PAD && v <= y1 + HUD_PAD
            }
    }
}

/** Pixel offsets (dx, dy) whose L1 distance from the centre lies in a band. */
internal class Offsets(val dx: IntArray, val dy: IntArray) {
    val size: Int get() = dx.size

    companion object {
        fun l1Band(lo: Double, hi: Double): Offsets {
            val r = hi.toInt() + 1
            val xs = mutableListOf<Int>()
            val ys = mutableListOf<Int>()
            for (y in -r..r) for (x in -r..r) {
                val d = abs(x) + abs(y)
                if (d >= lo && d <= hi) {
                    xs += x
                    ys += y
                }
            }
            return Offsets(xs.toIntArray(), ys.toIntArray())
        }
    }
}

/** Per-pixel colour classes for one screenshot, as bit flags. */
internal class MapMasks private constructor(val width: Int, val height: Int, private val flags: ShortArray) {
    fun has(x: Int, y: Int, bits: Int): Boolean = flags[y * width + x].toInt() and bits != 0

    /**
     * Fraction of the pixels at [offsets] around (cx, cy) that have [bits], counting only pixels
     * free of Hero's Path trail. Null when the window leaves the image.
     */
    fun fraction(bits: Int, cx: Int, cy: Int, offsets: Offsets): Double? {
        var hit = 0
        var valid = 0
        for (i in 0 until offsets.size) {
            val x = cx + offsets.dx[i]
            val y = cy + offsets.dy[i]
            if (x < 0 || y < 0 || x >= width || y >= height) return null
            val f = flags[y * width + x].toInt()
            if (f and TRAIL != 0) continue
            valid++
            if (f and bits != 0) hit++
        }
        return if (valid == 0) 0.0 else hit.toDouble() / valid
    }

    /**
     * Hero's Path mode turns the frame's ornaments trail-green; normally the frame has none. Measured
     * on the top-left ornament: 17% in Hero's Path, 0% otherwise.
     */
    fun isHeroesPath(): Boolean {
        var trail = 0
        for (y in 0 until 120) for (x in 0 until 230) if (flags[y * width + x].toInt() and TRAIL != 0) trail++
        return trail > 0.05 * 120 * 230
    }

    /** Fraction of the pixels at [offsets] around (cx, cy) that aren't trail. */
    fun visible(cx: Int, cy: Int, offsets: Offsets): Double {
        var valid = 0
        for (i in 0 until offsets.size) {
            val x = (cx + offsets.dx[i]).coerceIn(0, width - 1)
            val y = (cy + offsets.dy[i]).coerceIn(0, height - 1)
            if (flags[y * width + x].toInt() and TRAIL == 0) valid++
        }
        return valid.toDouble() / offsets.size
    }

    /**
     * Normalised cross-correlation (OpenCV's TM_CCOEFF_NORMED) of the [bits] mask against
     * [template] centred on (cx, cy). With [maskTrail], trail pixels are left out of the sums.
     */
    fun ncc(bits: Int, template: IconTemplate, cx: Int, cy: Int, maskTrail: Boolean): Double {
        val r = template.radius
        if (cx - r < 0 || cy - r < 0 || cx + r >= width || cy + r >= height) return 0.0
        val t = template.values
        val size = template.size
        var n = 0
        var sp = 0.0
        var st = 0.0
        var spp = 0.0
        var stt = 0.0
        var spt = 0.0
        for (ty in 0 until size) {
            val row = (cy - r + ty) * width + cx - r
            for (tx in 0 until size) {
                val f = flags[row + tx].toInt()
                if (maskTrail && f and TRAIL != 0) continue
                val p = if (f and bits != 0) 1.0 else 0.0
                val tv = t[ty * size + tx].toDouble()
                n++
                sp += p
                st += tv
                spp += p * p
                stt += tv * tv
                spt += p * tv
            }
        }
        if (n < 10) return 0.0
        val cov = spt - sp * st / n
        val vp = spp - sp * sp / n
        val vt = stt - st * st / n
        val den = vp * vt
        return if (den > 1e-9) cov / sqrt(den) else 0.0
    }

    class Blob(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
    }

    /** 8-connected components of [bits] inside the rectangle [x0, x1) × [y0, y1). */
    fun components(bits: Int, x0: Int, y0: Int, x1: Int, y1: Int): List<Blob> {
        val rw = x1 - x0
        val seen = BooleanArray(rw * (y1 - y0))
        val stack = IntArray(seen.size)
        val blobs = mutableListOf<Blob>()
        for (y in y0 until y1) for (x in x0 until x1) {
            val start = (y - y0) * rw + x - x0
            if (seen[start] || !has(x, y, bits)) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var minX = x
            var maxX = x
            var minY = y
            var maxY = y
            while (top > 0) {
                val p = stack[--top]
                val px = x0 + p % rw
                val py = y0 + p / rw
                minX = min(minX, px)
                maxX = max(maxX, px)
                minY = min(minY, py)
                maxY = max(maxY, py)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx
                    val ny = py + dy
                    if (nx < x0 || ny < y0 || nx >= x1 || ny >= y1) continue
                    val q = (ny - y0) * rw + nx - x0
                    if (!seen[q] && has(nx, ny, bits)) {
                        seen[q] = true
                        stack[top++] = q
                    }
                }
            }
            blobs += Blob(minX, minY, maxX, maxY)
        }
        return blobs
    }

    companion object {
        /** Saturated light blue of icon outlines and glyphs (shrines, towers, Divine Beasts). */
        const val BLUE = 1 shl 0

        /** Tower icons are paler, and how pale varies between saves. */
        const val TOWER = 1 shl 1

        /** Softer blue: the glow around icons. Water is darker and greyer. */
        const val GLOW = 1 shl 2

        /** Orange glyph of an unfinished shrine. */
        const val ORANGE = 1 shl 3

        /** Cream location icons: stables, villages, the castle, labs. */
        const val TAN = 1 shl 4

        /** Near-white: tower glyphs, the cursor label, pin glyphs, the horse. */
        const val WHITE = 1 shl 5
        const val PURPLE = 1 shl 6

        /** Yellow stamps and target rings. */
        const val YELLOW = 1 shl 7

        /** Red pins and Hero's Path's death marker; brighter than Eldin's lava. */
        const val RED = 1 shl 8

        /** Hero's Path trail (green), grown by a pixel to cover its blended edge. */
        const val TRAIL = 1 shl 9

        const val ICON = BLUE or ORANGE

        // OpenCV's fixed-point tables for 8-bit RGB → HSV, so values match the prototype's.
        private val SDIV = IntArray(256) { if (it == 0) 0 else (255.0 * 4096 / it).roundToInt() }
        private val HDIV = IntArray(256) { if (it == 0) 0 else (180.0 * 4096 / (6 * it)).roundToInt() }

        fun of(image: MapImage): MapMasks {
            val w = image.width
            val h = image.height
            val flags = ShortArray(w * h)
            val trail = BooleanArray(w * h)
            val px = image.argb
            for (i in px.indices) {
                val c = px[i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val v = max(r, max(g, b))
                val diff = v - min(r, min(g, b))
                val s = (diff * SDIV[v] + (1 shl 11)) shr 12
                var hue = when (v) {
                    r -> g - b
                    g -> b - r + 2 * diff
                    else -> r - g + 4 * diff
                }
                hue = (hue * HDIV[diff] + (1 shl 11)) shr 12
                if (hue < 0) hue += 180
                var f = 0
                if (hue in 88..112 && s > 110 && v > 170) f = f or BLUE
                if (hue in 88..112 && s > 60 && v > 200) f = f or TOWER
                if (hue in 88..115 && s > 90 && v > 120) f = f or GLOW
                if (hue in 8..30 && s > 80 && v > 170) f = f or ORANGE
                if (hue in 12..30 && s > 45 && s < 150 && v > 215) f = f or TAN
                if (s < 60 && v > 235) f = f or WHITE
                if (hue in 120..160 && s > 90 && v > 150) f = f or PURPLE
                if (hue in 22..38 && s > 150 && v > 200) f = f or YELLOW
                if ((hue <= 6 || hue >= 172) && s > 150 && v > 230) f = f or RED
                flags[i] = f.toShort()
                trail[i] = hue in 45..87 && s > 60 && v > 100
            }
            for (y in 0 until h) for (x in 0 until w) {
                if (!trail[y * w + x]) continue
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) {
                        val q = ny * w + nx
                        flags[q] = (flags[q].toInt() or TRAIL).toShort()
                    }
                }
            }
            return MapMasks(w, h, flags)
        }
    }
}
