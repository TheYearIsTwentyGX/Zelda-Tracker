package gx.zelda.screencam

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** A point in pixels (or world units). */
data class Point(val x: Double, val y: Double)

/** A plane-to-plane projective transform: a 3×3 matrix, row by row. */
class Homography(val m: DoubleArray) {
    init {
        require(m.size == 9) { "a homography has 9 entries" }
    }

    fun map(x: Double, y: Double): Point {
        val w = m[6] * x + m[7] * y + m[8]
        return Point((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    fun map(p: Point): Point = map(p.x, p.y)

    /** This after [other]: `(this * other).map(p) == map(other.map(p))`. */
    operator fun times(other: Homography): Homography {
        val r = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) {
            var s = 0.0
            for (k in 0..2) s += m[i * 3 + k] * other.m[k * 3 + j]
            r[i * 3 + j] = s
        }
        return Homography(r)
    }

    fun inverse(): Homography {
        val a = m
        val c00 = a[4] * a[8] - a[5] * a[7]
        val c01 = a[5] * a[6] - a[3] * a[8]
        val c02 = a[3] * a[7] - a[4] * a[6]
        val det = a[0] * c00 + a[1] * c01 + a[2] * c02
        val r = doubleArrayOf(
            c00, a[2] * a[7] - a[1] * a[8], a[1] * a[5] - a[2] * a[4],
            c01, a[0] * a[8] - a[2] * a[6], a[2] * a[3] - a[0] * a[5],
            c02, a[1] * a[6] - a[0] * a[7], a[0] * a[4] - a[1] * a[3],
        )
        return Homography(DoubleArray(9) { r[it] / det })
    }

    /** How many target pixels one source pixel spans around [p]: the geometric mean of the two axes. */
    fun localScale(p: Point): Double {
        val a = map(p)
        val bx = map(p.x + 1, p.y)
        val by = map(p.x, p.y + 1)
        return sqrt(hypot(bx.x - a.x, bx.y - a.y) * hypot(by.x - a.x, by.y - a.y))
    }

    companion object {
        fun scaleTranslate(scale: Double, tx: Double, ty: Double) =
            Homography(doubleArrayOf(scale, 0.0, tx, 0.0, scale, ty, 0.0, 0.0, 1.0))

        /**
         * Least-squares fit taking [src] to [dst] (direct linear transform with the last entry fixed
         * at 1, on normalised points). Exact for four points; null when the points are degenerate.
         */
        fun fit(src: List<Point>, dst: List<Point>): Homography? {
            require(src.size == dst.size) { "point lists differ in size" }
            if (src.size < 4) return null
            val (cs, ss) = normalisation(src)
            val (cd, sd) = normalisation(dst)
            val ata = Array(8) { DoubleArray(8) }
            val atb = DoubleArray(8)
            val r1 = DoubleArray(8)
            val r2 = DoubleArray(8)
            for (i in src.indices) {
                val x = (src[i].x - cs.x) * ss
                val y = (src[i].y - cs.y) * ss
                val u = (dst[i].x - cd.x) * sd
                val v = (dst[i].y - cd.y) * sd
                r1.fill(0.0)
                r2.fill(0.0)
                r1[0] = x; r1[1] = y; r1[2] = 1.0; r1[6] = -u * x; r1[7] = -u * y
                r2[3] = x; r2[4] = y; r2[5] = 1.0; r2[6] = -v * x; r2[7] = -v * y
                for (a in 0..7) {
                    for (b in 0..7) ata[a][b] += r1[a] * r1[b] + r2[a] * r2[b]
                    atb[a] += r1[a] * u + r2[a] * v
                }
            }
            val h = solve(ata, atb) ?: return null
            val normal = Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
            val toSrc = scaleTranslate(ss, -ss * cs.x, -ss * cs.y)
            val toDst = scaleTranslate(sd, -sd * cd.x, -sd * cd.y)
            return toDst.inverse() * normal * toSrc
        }

        /**
         * Robust fit: the four-point fit agreeing with the most pairs (within [threshold], measured
         * in [dst] units), then least squares on those inliers. Returns the fit and the inliers' indices.
         */
        fun ransac(
            src: List<Point>,
            dst: List<Point>,
            threshold: Double,
            iterations: Int = 300,
            random: Random = Random(1),
        ): Pair<Homography, List<Int>>? {
            if (src.size < 4) return null
            fun inliers(h: Homography) = src.indices.filter {
                val p = h.map(src[it])
                hypot(p.x - dst[it].x, p.y - dst[it].y) < threshold
            }
            var best = emptyList<Int>()
            repeat(iterations) {
                val sample = src.indices.shuffled(random).take(4)
                val h = fit(sample.map { src[it] }, sample.map { dst[it] }) ?: return@repeat
                val inl = inliers(h)
                if (inl.size > best.size) best = inl
            }
            if (best.size < 4) return null
            var h = fit(best.map { src[it] }, best.map { dst[it] }) ?: return null
            repeat(2) {
                val inl = inliers(h)
                if (inl.size < 4) return null
                best = inl
                h = fit(best.map { src[it] }, best.map { dst[it] }) ?: return null
            }
            return h to best
        }

        /** Centroid and the scale that brings the mean distance from it to √2. */
        private fun normalisation(p: List<Point>): Pair<Point, Double> {
            val mx = p.sumOf { it.x } / p.size
            val my = p.sumOf { it.y } / p.size
            val d = p.sumOf { hypot(it.x - mx, it.y - my) } / p.size
            return Point(mx, my) to sqrt(2.0) / max(d, 1e-12)
        }

        /** Gauss–Jordan elimination with partial pivoting; null when singular. */
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
                    val f = m[r][c] / m[c][c]
                    if (f != 0.0) for (k in c..n) m[r][k] -= f * m[c][k]
                }
            }
            return DoubleArray(n) { m[it][n] / m[it][it] }
        }
    }
}
