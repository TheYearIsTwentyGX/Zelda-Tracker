package gx.zelda.screencam.live

import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import gx.zelda.screencam.ScreenChrome
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Follows the screen from frame to frame by aligning the map screen's frame (ornaments, header,
 * button bar: the same in every screenshot) with a grey reference of it. The map inside pans, the
 * frame doesn't, so this gives the screen's homography whatever the map shows.
 *
 * Each frame: a coarse translation search, then Gauss–Newton on the homography plus a brightness
 * gain and offset, coarse to fine over three reference levels, with Huber weights so a thumb over
 * the screen or a changed readout doesn't drag the fit.
 */
class ChromeTracker(reference: GrayImage) {
    /** One level of the reference: the frame's cells, their canonical centres and grey values. */
    private class Level(val cell: Double, val x: DoubleArray, val y: DoubleArray, val t: FloatArray)

    private val levels: List<Level>

    init {
        require(reference.width == REF_W && reference.height == REF_H) { "reference must be $REF_W × $REF_H" }
        var img = reference
        var cell = ScreenChrome.WIDTH.toDouble() / REF_W
        val built = ArrayList<Level>()
        repeat(3) {
            val xs = ArrayList<Double>()
            val ys = ArrayList<Double>()
            val ts = ArrayList<Float>()
            for (y in 0 until img.height) for (x in 0 until img.width) {
                // A cell counts when its whole footprint is frame.
                val x0 = (x * cell).toInt()
                val y0 = (y * cell).toInt()
                val x1 = ((x + 1) * cell).toInt() - 1
                val y1 = ((y + 1) * cell).toInt() - 1
                if (!ScreenChrome.isFrame(x0, y0) || !ScreenChrome.isFrame(x1, y1) || !ScreenChrome.isFrame(x0, y1) || !ScreenChrome.isFrame(x1, y0)) continue
                xs += (x + 0.5) * cell
                ys += (y + 0.5) * cell
                ts += img[x, y]
            }
            built += Level(cell, xs.toDoubleArray(), ys.toDoubleArray(), ts.toFloatArray())
            img = img.half()
            cell *= 2
        }
        levels = built.reversed() // coarse first
    }

    class Result(val screenToFrame: Homography, val correlation: Double, val iterations: Int)

    /** Pyramid of [frame]: level k is 2^k smaller. */
    class Frame(frame: GrayImage) {
        val pyramid: List<GrayImage> = buildList {
            var img = frame
            add(img)
            while (img.width > 120 && size < 6) {
                img = img.half()
                add(img)
            }
        }
    }

    /**
     * Aligns [frame] starting from [guess], canonical → frame pixels. Null when too little of the
     * frame is in view to tell.
     */
    fun track(frame: Frame, guess: Homography, maxIterations: Int = 12): Result? {
        // Usually the screen has hardly moved: fit from where it was. Search around only if that fails.
        val direct = refine(frame, guess, maxIterations)
        if (direct != null && direct.correlation >= SEARCH_BELOW) return direct
        val searched = searchShift(frame, guess)?.let { refine(frame, it, maxIterations) }
        return listOfNotNull(direct, searched).maxByOrNull { it.correlation }
    }

    private fun refine(frame: Frame, start: Homography, maxIterations: Int): Result? {
        var h = start
        var iterations = 0
        var gain = 1.0
        var bias = 0.0
        for ((li, level) in levels.withIndex()) {
            val img = imageFor(frame, h, level)
            val f = (1 shl frame.pyramid.indexOf(img)).toDouble()
            val iters = if (li == levels.size - 1) maxIterations else maxIterations / 2
            val photometric = fitPhotometric(img, f, h, level) ?: return null
            gain = photometric.first
            bias = photometric.second
            for (it in 0 until iters) {
                iterations++
                val step = gaussNewton(img, f, h, level, gain, bias) ?: return null
                h = h * normalisedStep(step)
                gain += step[8]
                bias += step[9]
                var size = 0.0
                for (k in 0 until 8) size = max(size, abs(step[k]))
                if (size < 1e-4) break
            }
        }
        val fine = levels.last()
        val img = imageFor(frame, h, fine)
        val corr = correlation(img, (1 shl frame.pyramid.indexOf(img)).toDouble(), h, fine) ?: return null
        return Result(h, corr, iterations)
    }

    /** The pyramid level where one reference cell spans about 1–2 pixels. */
    private fun imageFor(frame: Frame, h: Homography, level: Level): GrayImage {
        val perCell = h.localScale(Point(ScreenChrome.WIDTH / 2.0, ScreenChrome.HEIGHT / 2.0)) * level.cell
        val k = max(0, (ln(perCell / 1.5) / ln(2.0)).toInt()).coerceAtMost(frame.pyramid.size - 1)
        return frame.pyramid[k]
    }

    /** Best whole-cell shift of [guess] in the frame, on the coarsest level, by in-view-weighted correlation. */
    private fun searchShift(frame: Frame, guess: Homography): Homography? {
        val level = levels.first()
        val img = imageFor(frame, guess, level)
        val f = (1 shl frame.pyramid.indexOf(img)).toDouble()
        var best = correlation(img, f, guess, level, weighted = true)
        var bestH = guess
        // Steps of one coarse cell in frame pixels, out to about a sixth of the screen.
        val cellPx = guess.localScale(Point(ScreenChrome.WIDTH / 2.0, ScreenChrome.HEIGHT / 2.0)) * level.cell
        val reach = 5
        for (dy in -reach..reach) for (dx in -reach..reach) {
            if (dx == 0 && dy == 0) continue
            val shifted = Homography.scaleTranslate(1.0, dx * cellPx, dy * cellPx) * guess
            val c = correlation(img, f, shifted, level, weighted = true) ?: continue
            if (best == null || c > best) {
                best = c
                bestH = shifted
            }
        }
        return if (best == null) null else bestH
    }

    /**
     * Zero-mean correlation of the reference with the frame under [h]; null when under 30% is in
     * view. [weighted] scales it by the share in view, so pushing the reference off the frame (and
     * keeping only the parts that happen to fit) doesn't pay.
     */
    private fun correlation(img: GrayImage, f: Double, h: Homography, level: Level, weighted: Boolean = false): Double? {
        var n = 0
        var sa = 0.0
        var sb = 0.0
        var saa = 0.0
        var sbb = 0.0
        var sab = 0.0
        val m = h.m
        for (i in level.t.indices) {
            val x = level.x[i]
            val y = level.y[i]
            val w = m[6] * x + m[7] * y + m[8]
            val v = img.sample(((m[0] * x + m[1] * y + m[2]) / w) / f - 0.5, ((m[3] * x + m[4] * y + m[5]) / w) / f - 0.5)
            if (v.isNaN()) continue
            val t = level.t[i].toDouble()
            n++
            sa += v
            sb += t
            saa += v * v
            sbb += t * t
            sab += v * t
        }
        if (n < 0.3 * level.t.size || n < 20) return null
        val va = saa - sa * sa / n
        val vb = sbb - sb * sb / n
        val c = if (va * vb > 1e-6) (sab - sa * sb / n) / sqrt(va * vb) else 0.0
        return if (weighted) c * n / level.t.size else c
    }

    /** Gain and offset taking the frame's levels to the reference's under [h]. */
    private fun fitPhotometric(img: GrayImage, f: Double, h: Homography, level: Level): Pair<Double, Double>? {
        var n = 0
        var sa = 0.0
        var sb = 0.0
        var saa = 0.0
        var sbb = 0.0
        val m = h.m
        for (i in level.t.indices) {
            val x = level.x[i]
            val y = level.y[i]
            val w = m[6] * x + m[7] * y + m[8]
            val v = img.sample(((m[0] * x + m[1] * y + m[2]) / w) / f - 0.5, ((m[3] * x + m[4] * y + m[5]) / w) / f - 0.5)
            if (v.isNaN()) continue
            n++
            sa += v
            sb += level.t[i]
            saa += v.toDouble() * v
            sbb += level.t[i].toDouble() * level.t[i]
        }
        if (n < 20) return null
        val sdA = sqrt(max(1e-6, saa / n - (sa / n) * (sa / n)))
        val sdB = sqrt(max(1e-6, sbb / n - (sb / n) * (sb / n)))
        val gain = sdB / sdA
        return gain to sb / n - gain * sa / n
    }

    /**
     * One Gauss–Newton step on (p0…p7, gain, bias), where the warp is h ∘ (I + D(p)) in canonical
     * coordinates normalised to 0–1, and the residual is gain·frame + bias − reference.
     */
    private fun gaussNewton(img: GrayImage, f: Double, h: Homography, level: Level, gain: Double, bias: Double): DoubleArray? {
        val a = Array(10) { DoubleArray(10) }
        val b = DoubleArray(10)
        val g = DoubleArray(10)
        val m = h.m
        val sw = ScreenChrome.WIDTH.toDouble()
        val sh = ScreenChrome.HEIGHT.toDouble()
        var n = 0
        for (i in level.t.indices) {
            val x = level.x[i]
            val y = level.y[i]
            val w = m[6] * x + m[7] * y + m[8]
            val u = (m[0] * x + m[1] * y + m[2]) / w
            val v = (m[3] * x + m[4] * y + m[5]) / w
            val fu = u / f - 0.5
            val fv = v / f - 0.5
            val iv = img.sample(fu, fv)
            if (iv.isNaN()) continue
            val gx = img.sample(fu + 1, fv) - img.sample(fu - 1, fv)
            val gy = img.sample(fu, fv + 1) - img.sample(fu, fv - 1)
            if (gx.isNaN() || gy.isNaN()) continue
            // Image gradient per full-resolution frame pixel.
            val iu = 0.5 * gx / f
            val iw = 0.5 * gy / f
            // d(frame point)/d(canonical point)
            val dudx = (m[0] - u * m[6]) / w
            val dudy = (m[1] - u * m[7]) / w
            val dvdx = (m[3] - v * m[6]) / w
            val dvdy = (m[4] - v * m[7]) / w
            // Gradient wrt canonical x and y.
            val cx = gain * (iu * dudx + iw * dvdx)
            val cy = gain * (iu * dudy + iw * dvdy)
            val xn = x / sw
            val yn = y / sh
            // d(canonical)/dp for x' = sw·xn', y' = sh·yn'.
            g[0] = cx * sw * xn
            g[1] = cx * sw * yn
            g[2] = cx * sw
            g[3] = cy * sh * xn
            g[4] = cy * sh * yn
            g[5] = cy * sh
            g[6] = -(cx * sw * xn * xn + cy * sh * xn * yn)
            g[7] = -(cx * sw * xn * yn + cy * sh * yn * yn)
            g[8] = iv.toDouble()
            g[9] = 1.0
            val r = gain * iv + bias - level.t[i]
            val wt = if (abs(r) <= HUBER) 1.0 else HUBER / abs(r)
            for (j in 0 until 10) {
                val gj = wt * g[j]
                b[j] += gj * r
                for (k in j until 10) a[j][k] += gj * g[k]
            }
            n++
        }
        if (n < 20) return null
        for (j in 0 until 10) {
            for (k in 0 until j) a[j][k] = a[k][j]
            a[j][j] *= 1 + DAMPING
        }
        return solve(a, DoubleArray(10) { -b[it] })
    }

    /** The canonical → canonical homography of I + D(p) in normalised coordinates. */
    private fun normalisedStep(p: DoubleArray): Homography {
        val sw = ScreenChrome.WIDTH.toDouble()
        val sh = ScreenChrome.HEIGHT.toDouble()
        val d = Homography(doubleArrayOf(1 + p[0], p[1], p[2], p[3], 1 + p[4], p[5], p[6], p[7], 1.0))
        val scale = Homography(doubleArrayOf(sw, 0.0, 0.0, 0.0, sh, 0.0, 0.0, 0.0, 1.0))
        val unscale = Homography(doubleArrayOf(1 / sw, 0.0, 0.0, 0.0, 1 / sh, 0.0, 0.0, 0.0, 1.0))
        return scale * d * unscale
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
            if (abs(m[p][c]) < 1e-12) return null
            val t = m[p]
            m[p] = m[c]
            m[c] = t
            for (r in 0 until n) {
                if (r == c) continue
                val q = m[r][c] / m[c][c]
                if (q != 0.0) for (k in c..n) m[r][k] -= q * m[c][k]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    companion object {
        const val REF_W = 240
        const val REF_H = 135

        /** A direct fit correlating less than this is retried after a coarse search. */
        private const val SEARCH_BELOW = 0.55

        /** Residual (grey levels) past which a cell is down-weighted. */
        private const val HUBER = 12.0
        private const val DAMPING = 1e-3

        /** Each game's reference lives at `files/<game id>/map_chrome_track.bin`: REF_W × REF_H grey bytes, row by row. */
        fun referencePath(game: Game) = "files/${game.id}/map_chrome_track.bin"

        suspend fun loadReference(game: Game): GrayImage = decodeReference(Res.readBytes(referencePath(game)))

        fun decodeReference(bytes: ByteArray): GrayImage = GrayImage.fromLuma(bytes, REF_W, REF_H)

        fun encodeReference(img: GrayImage): ByteArray = ByteArray(img.pixels.size) { img.pixels[it].roundToInt().coerceIn(0, 255).toByte() }

        /** The reference: canonical [screenshots]' grey, averaged into REF_W × REF_H cells. */
        fun reference(screenshots: List<GrayImage>): GrayImage {
            val cell = ScreenChrome.WIDTH / REF_W
            return GrayImage(REF_W, REF_H, FloatArray(REF_W * REF_H) { i ->
                val cx = i % REF_W
                val cy = i / REF_W
                var s = 0f
                for (img in screenshots) for (dy in 0 until cell) for (dx in 0 until cell) s += img[cx * cell + dx, cy * cell + dy]
                (s / (cell * cell * screenshots.size)).roundToInt().toFloat()
            })
        }
    }
}
