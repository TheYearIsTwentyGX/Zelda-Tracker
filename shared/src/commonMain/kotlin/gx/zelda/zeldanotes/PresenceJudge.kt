package gx.zelda.zeldanotes

import gx.zelda.screencam.live.KorokSpot
import kotlin.math.max

/** What one view says about a Korok, with "Hide Discovered Destinations" on. */
enum class NotesVerdict {
    /** Its droplet is drawn: the seed is still to collect. */
    PRESENT,

    /** Clear map where its droplet would be: collected. */
    ABSENT,

    /** Covered by other icons, or neither clearly there nor clearly gone. */
    UNSURE,
}

/**
 * Decides, for each Korok whose droplet would sit wholly on screen, whether it's drawn. Droplets
 * found whole are matched to the nearest predicted Korok first. Every other Korok is checked against
 * the pixels those droplets don't already explain: its fill area mostly tan nearby means it's drawn
 * (partly covered), mostly not means it's gone, and an area mostly under other droplets or shrine
 * icons, or right beside a drawn droplet, can't be told. Only views zoomed in far enough for droplets to stand apart are judged.
 */
class PresenceJudge(private val template: DropletTemplate, private val spots: List<KorokSpot>) {
    /** True when [pose] is zoomed in enough to judge: a droplet spans at most [MAX_ICON_UNITS] world units. */
    fun canJudge(pose: NotesPose): Boolean = template.width / pose.scale <= MAX_ICON_UNITS

    fun judge(image: NotesImage, pose: NotesPose, droplets: List<Droplet>): Map<String, NotesVerdict> {
        if (!canJudge(pose)) return emptyMap()
        val t = template
        val tol = max(MIN_MATCH_PX, MATCH_UNITS * pose.scale)
        val w = image.width
        val u = DoubleArray(spots.size) { pose.u(spots[it].x) }
        val v = DoubleArray(spots.size) { pose.v(spots[it].z) }
        fun placed(k: Int): Boolean {
            val ox = u[k].toInt() - t.cx
            val oy = v[k].toInt() - t.cy
            return ox >= 0 && ox + t.width <= w && oy >= image.top && oy + t.height <= image.bottom
        }
        val inView = spots.indices.filter(::placed)
        // Droplets matched greedily to Koroks near enough, closest pairs first; Koroks just off
        // screen take part so an edge droplet isn't pinned on the wrong one.
        val near = spots.indices.filter { u[it] > -tol && u[it] < w + tol && v[it] > image.top - tol && v[it] < image.bottom + tol }
        val pairs = ArrayList<Triple<Double, Int, Int>>()
        for ((di, d) in droplets.withIndex()) for (k in near) {
            val dx = u[k] - d.x
            val dy = v[k] - d.y
            val dd = dx * dx + dy * dy
            if (dd <= tol * tol) pairs += Triple(dd, di, k)
        }
        pairs.sortBy { it.first }
        val droplet = HashSet<Int>()
        val matched = HashSet<Int>()
        for ((_, di, k) in pairs) {
            if (di in droplet || k in matched) continue
            droplet += di
            matched += k
        }
        val explained = shrineIcons(image)
        for (d in droplets) paint(explained, image, d.x.toInt() - t.cx, d.y.toInt() - t.cy)
        val out = HashMap<String, NotesVerdict>()
        val open = inView.filter { it !in matched }.map { k -> k to visible(image, explained, u[k].toInt() - t.cx, v[k].toInt() - t.cy) }
        for ((k, _) in open.sortedByDescending { it.second }) {
            val ox = u[k].toInt() - t.cx
            val oy = v[k].toInt() - t.cy
            // Hidden under other icons, or right beside a drawn droplet that might be its own: in a
            // cluster the nearest-first matching can't be trusted to say which Korok is missing.
            val crowded = droplets.any { d -> (d.x - u[k]) * (d.x - u[k]) + (d.y - v[k]) * (d.y - v[k]) <= tol * tol }
            if (crowded || visible(image, explained, ox, oy) < MIN_VISIBLE) {
                out[spots[k].id] = NotesVerdict.UNSURE
                continue
            }
            // The best tan cover anywhere within the match tolerance: the app's icon can sit a little
            // off the objmap point.
            var best = 0.0
            var bx = ox
            var by = oy
            val r = tol.toInt()
            for (dy in -r..r step 2) for (dx in -r..r step 2) {
                if (dx * dx + dy * dy > r * r) continue
                val c = cover(image, explained, ox + dx, oy + dy)
                if (c > best) {
                    best = c; bx = ox + dx; by = oy + dy
                }
            }
            out[spots[k].id] = when {
                best >= PRESENT_COVER -> NotesVerdict.PRESENT.also { paint(explained, image, bx, by) }
                best <= ABSENT_COVER -> NotesVerdict.ABSENT
                else -> NotesVerdict.UNSURE
            }
        }
        for (k in inView) if (k in matched) out[spots[k].id] = NotesVerdict.PRESENT
        return out
    }

    /**
     * Shrine icons, whole: a blue diamond outline around a white and dark glyph, drawn over any
     * droplet beneath. Each blue blob's box counts as covered.
     */
    private fun shrineIcons(image: NotesImage): BooleanArray {
        val w = image.width
        val out = BooleanArray(w * image.height)
        val blue = BooleanArray(out.size) { image.blue[it] >= 0.3f }
        for (b in Blobs.of(blue, w, image.height)) {
            if (b.area < MIN_SHRINE_AREA) continue
            for (y in (b.minY - 2).coerceAtLeast(0)..(b.maxY + 2).coerceAtMost(image.height - 1)) {
                for (x in (b.minX - 2).coerceAtLeast(0)..(b.maxX + 2).coerceAtMost(w - 1)) out[y * w + x] = true
            }
        }
        return out
    }

    /** Marks the whole drawn droplet with its template's top-left at (ox, oy). */
    private fun paint(mask: BooleanArray, image: NotesImage, ox: Int, oy: Int) {
        val t = template
        for (i in t.shapeX.indices) {
            val x = ox + t.shapeX[i]
            val y = oy + t.shapeY[i]
            if (x in 0 until image.width && y in 0 until image.height) mask[y * image.width + x] = true
        }
    }

    /** The share of the fill at (ox, oy) not already explained by other icons. */
    private fun visible(image: NotesImage, explained: BooleanArray, ox: Int, oy: Int): Double {
        val t = template
        var n = 0
        for (i in t.fillX.indices) {
            val x = ox + t.fillX[i]
            val y = oy + t.fillY[i]
            if (x in 0 until image.width && y in image.top until image.bottom && !explained[y * image.width + x]) n++
        }
        return n.toDouble() / t.fillX.size
    }

    /** How tan the unexplained part of the fill at (ox, oy) is; 0 when too little of it shows. */
    private fun cover(image: NotesImage, explained: BooleanArray, ox: Int, oy: Int): Double {
        val t = template
        var n = 0
        var tan = 0.0
        for (i in t.fillX.indices) {
            val x = ox + t.fillX[i]
            val y = oy + t.fillY[i]
            if (x !in 0 until image.width || y !in image.top until image.bottom) continue
            val j = y * image.width + x
            if (explained[j]) continue
            n++
            tan += image.tan[j]
        }
        return if (n < MIN_VISIBLE * t.fillX.size) 0.0 else tan / n
    }

    companion object {
        /** Judge only when a droplet spans at most this many world units (about 0.75 px/unit on the reference phone). */
        const val MAX_ICON_UNITS = 120.0

        /** The app's icon may sit this far from objmap's point. */
        const val MATCH_UNITS = 60.0
        const val MIN_MATCH_PX = 6.0

        const val MIN_VISIBLE = 0.35

        /** Blue blobs smaller than this (work px) are map water or noise, not a shrine icon. */
        const val MIN_SHRINE_AREA = 15
        const val PRESENT_COVER = 0.6
        const val ABSENT_COVER = 0.2
    }
}
