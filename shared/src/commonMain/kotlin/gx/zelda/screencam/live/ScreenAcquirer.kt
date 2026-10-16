package gx.zelda.screencam.live

import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import gx.zelda.screencam.ScreenChrome
import gx.zelda.screencam.ScreenFinder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the screen in a camera frame to start tracking. Outlines come from [ScreenFinder] on a
 * ~320 px grey copy, plus centred 16:9 rectangles in case the screen fills the frame and the
 * brightness split falls inside it. Each, in every orientation, is scored by how much its frame
 * looks like the map screen's; the best few are refined by [ChromeTracker], whose fit decides.
 */
class ScreenAcquirer(private val chrome: ScreenChrome, private val tracker: ChromeTracker) {
    class Attempt(
        /** Canonical → frame pixels, when accepted. */
        val screenToFrame: Homography?,
        /** The best candidate's frame likeness, and its corners in frame pixels. */
        val score: Double,
        val corners: List<Point>?,
        /** The tracker's correlation for the best refined candidate. */
        val correlation: Double,
        /** Why nothing was accepted; null when it was. */
        val reason: String?,
    )

    fun acquire(frame: GrayImage, pyramid: ChromeTracker.Frame): Attempt {
        val k = max(1, ceil(max(frame.width, frame.height) / WORK_SIDE.toDouble()).toInt())
        val small = frame.shrink(k)
        val scale = 1.0 / k
        val outlines = ScreenFinder.candidates(small, scale) + priors(frame.width, frame.height)
        val scored = ArrayList<Pair<Homography, Double>>()
        for (outline in outlines) for (turn in 0..3) {
            val h = Homography.fit(ScreenChrome.CORNERS, List(4) { outline[(it + turn) % 4] }) ?: continue
            scored += h to chrome.frameScore(small, scale, h)
        }
        scored.sortByDescending { it.second }
        val best = scored.firstOrNull()
        val bestCorners = best?.first?.let { h -> ScreenChrome.CORNERS.map(h::map) }
        if (best == null || best.second < MIN_SCORE) {
            val tooSmall = bestCorners != null && width(bestCorners) < SMALL_SHARE * max(frame.width, frame.height)
            return Attempt(
                null, best?.second ?: 0.0, bestCorners, 0.0,
                if (tooSmall) "Screen too small: move closer" else "No screen found: best match ${dp2(best?.second ?: 0.0)}",
            )
        }
        // Refine the best few distinct candidates; the tracker's fit is the real test.
        var accepted: ChromeTracker.Result? = null
        var tried = 0
        var bestCorrelation = 0.0
        val seen = ArrayList<Homography>()
        for ((h, score) in scored) {
            if (tried == REFINED || score < MIN_SCORE) break
            if (seen.any { same(it, h) }) continue
            seen += h
            tried++
            val r = tracker.track(pyramid, h) ?: continue
            bestCorrelation = max(bestCorrelation, r.correlation)
            // Both must agree: the tracker alone can fit a dark screen's bezel edges.
            if (r.correlation >= MIN_CORRELATION && looksLikeAScreen(ScreenChrome.CORNERS.map(r.screenToFrame::map), frame.width, frame.height) &&
                chrome.frameScore(small, scale, r.screenToFrame) >= MIN_REFINED_SCORE &&
                (accepted == null || r.correlation > accepted.correlation)
            ) {
                accepted = r
            }
        }
        return if (accepted != null) {
            Attempt(accepted.screenToFrame, best.second, ScreenChrome.CORNERS.map(accepted.screenToFrame::map), accepted.correlation, null)
        } else {
            Attempt(null, best.second, bestCorners, bestCorrelation, "Hold steadier: screen match ${dp2(best.second)}, fit ${dp2(bestCorrelation)}")
        }
    }

    /**
     * A screen seen whole: a convex quadrilateral inside the frame (give or take a few pixels), whose
     * opposite sides are within 1.7× of each other and corners between 40° and 140°.
     */
    private fun looksLikeAScreen(c: List<Point>, w: Int, h: Int): Boolean {
        val margin = 0.03 * max(w, h)
        if (c.any { it.x < -margin || it.y < -margin || it.x > w + margin || it.y > h + margin }) return false
        val sides = (0..3).map { hypot(c[(it + 1) % 4].x - c[it].x, c[(it + 1) % 4].y - c[it].y) }
        if (sides.any { it < 1 }) return false
        if (max(sides[0], sides[2]) > 1.7 * min(sides[0], sides[2]) || max(sides[1], sides[3]) > 1.7 * min(sides[1], sides[3])) return false
        var sign = 0
        for (i in 0..3) {
            val a = c[(i + 3) % 4]
            val b = c[i]
            val d = c[(i + 1) % 4]
            val ux = a.x - b.x
            val uy = a.y - b.y
            val vx = d.x - b.x
            val vy = d.y - b.y
            val cross = ux * vy - uy * vx
            val s = if (cross > 0) 1 else -1
            if (sign != 0 && s != sign) return false
            sign = s
            val cos = (ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy))
            if (abs(cos) > COS_40) return false
        }
        return true
    }

    private fun width(corners: List<Point>) = max(hypot(corners[1].x - corners[0].x, corners[1].y - corners[0].y), hypot(corners[3].x - corners[0].x, corners[3].y - corners[0].y))

    /** Two candidates whose corners are within a few pixels of each other. */
    private fun same(a: Homography, b: Homography): Boolean =
        ScreenChrome.CORNERS.all { c -> a.map(c).let { p -> b.map(c).let { q -> hypot(p.x - q.x, p.y - q.y) < 8 } } }

    /** Centred 16:9 rectangles, upright and on their side, at a few sizes. */
    private fun priors(w: Int, h: Int): List<List<Point>> = buildList {
        for (share in doubleArrayOf(0.95, 0.8, 0.6)) {
            for (onSide in listOf(false, true)) {
                var rw = share * w
                var rh = if (onSide) rw * 16 / 9 else rw * 9 / 16
                if (rh > share * h) {
                    rw *= share * h / rh
                    rh = share * h
                }
                val x0 = (w - rw) / 2
                val y0 = (h - rh) / 2
                add(listOf(Point(x0, y0), Point(x0 + rw, y0), Point(x0 + rw, y0 + rh), Point(x0, y0 + rh)))
            }
        }
    }

    companion object {
        /** Longest side of the grey copy outlines are found and scored on. */
        const val WORK_SIDE = 320

        /** Frame likeness a candidate needs to be refined at all. */
        const val MIN_SCORE = 0.3

        /** The tracker's correlation that accepts a candidate… */
        const val MIN_CORRELATION = 0.5

        /** …with its frame likeness, rescored after the tracker's fit. */
        const val MIN_REFINED_SCORE = 0.45
        private const val REFINED = 4

        /** cos 40° (= −cos 140°): how far a seen screen's corners may stray from square. */
        private const val COS_40 = 0.766

        /** A screen narrower than this share of the frame is too small to read markers. */
        private const val SMALL_SHARE = 0.25

        /** Two decimals, for status lines. */
        fun dp2(v: Double): String = ((v * 100).roundToInt() / 100.0).toString()
    }
}
