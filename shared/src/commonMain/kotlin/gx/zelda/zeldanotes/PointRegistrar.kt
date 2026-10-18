package gx.zelda.zeldanotes

import gx.zelda.screencam.live.KorokSpot
import kotlin.math.abs
import kotlin.math.floor

/**
 * Where a view sits on the world map: a Korok at world (x, z) has its droplet's centre at
 * work px (scale·x + tx, scale·z + ty). Zelda Notes never rotates or tilts its map.
 */
data class NotesPose(val scale: Double, val tx: Double, val ty: Double) {
    fun u(x: Double) = scale * x + tx
    fun v(z: Double) = scale * z + ty
    fun worldX(u: Double) = (u - tx) / scale
    fun worldZ(v: Double) = (v - ty) / scale
}

/** A pose and how well the droplets back it. */
data class Registration(val pose: NotesPose, val inliers: Int, val droplets: Int)

/**
 * Places droplets on the map by matching them to the known Korok positions: every (droplet, Korok)
 * pair votes for a translation at each of a fine ladder of scales, the best-supported (scale,
 * translation) is refined by least squares on its nearest-neighbour matches, and kept only if most
 * droplets agree. Found Koroks are missing from the screen, which costs votes but not correctness.
 */
class PointRegistrar(spots: List<KorokSpot>) {
    private val kx = DoubleArray(spots.size) { spots[it].x }
    private val kz = DoubleArray(spots.size) { spots[it].z }

    /** Open-addressed (bin → votes) table, reused across scales. */
    private val keys = LongArray(TABLE)
    private val counts = IntArray(TABLE)
    private val used = IntArray(TABLE)

    /**
     * The whole-map search. With [near], only poses close to that one are tried: scale within 25%
     * and the world point at (screenW / 2, screenH / 2) moved by less than [maxShift] work px. That's how a
     * recording carries its pose into frames with only a few droplets.
     */
    fun register(droplets: List<Droplet>, screenW: Int, screenH: Int, near: NotesPose? = null, maxShift: Double = 0.0): Registration? {
        if (droplets.size < (if (near == null) MIN_INLIERS else MIN_TRACKED_INLIERS)) return null
        val voters = droplets.sortedByDescending { it.score }.take(VOTERS)
        val lo = near?.let { it.scale / 1.25 } ?: MIN_SCALE
        val hi = near?.let { it.scale * 1.25 } ?: MAX_SCALE
        val cx = screenW / 2.0
        val cy = screenH / 2.0
        val centreX = near?.worldX(cx)
        val centreZ = near?.worldZ(cy)
        // Best few (votes, scale, tx, ty) across the ladder.
        val best = ArrayList<DoubleArray>()
        var s = lo
        while (s <= hi) {
            clear()
            for (d in voters) for (k in kx.indices) {
                val tx = d.x - s * kx[k]
                val ty = d.y - s * kz[k]
                if (centreX != null && (abs(tx - (cx - s * centreX)) > maxShift || abs(ty - (cy - s * centreZ!!)) > maxShift)) continue
                val bx = floor(tx / BIN).toLong()
                val by = floor(ty / BIN).toLong()
                // Each pair votes for the 2×2 bins around it, so a peak on a bin edge isn't split.
                vote(bx, by); vote(bx + 1, by); vote(bx, by + 1); vote(bx + 1, by + 1)
            }
            var top = 0
            var topKey = 0L
            for (j in 0 until usedCount) {
                val slot = used[j]
                if (counts[slot] > top) {
                    top = counts[slot]; topKey = keys[slot]
                }
            }
            if (top > 0) {
                val bx = (topKey shr 32) - OFFSET
                val by = (topKey and 0xffffffffL) - OFFSET
                best += doubleArrayOf(top.toDouble(), s, bx * BIN, by * BIN)
                best.sortByDescending { it[0] }
                if (best.size > CANDIDATES) best.removeAt(best.size - 1)
            }
            s *= STEP
        }
        var winner: Registration? = null
        for (c in best) {
            val r = refine(droplets, NotesPose(c[1], c[2], c[3])) ?: continue
            if (winner == null || r.inliers > winner.inliers) winner = r
        }
        val w = winner ?: return null
        val minInliers = if (near == null) MIN_INLIERS else MIN_TRACKED_INLIERS
        return w.takeIf { it.inliers >= minInliers && it.inliers >= MIN_RATIO * droplets.size }
    }

    /** Nearest-Korok matching and a least-squares (scale, tx, ty), a few rounds, tightening. */
    internal fun refine(droplets: List<Droplet>, start: NotesPose): Registration? {
        var pose = start
        var inliers = 0
        for (radius in doubleArrayOf(8.0, 6.0, 4.0, 4.0)) {
            var sxx = 0.0; var sx = 0.0; var su = 0.0; var sxu = 0.0
            var szz = 0.0; var sz = 0.0; var sv = 0.0; var szv = 0.0
            var n = 0
            for (d in droplets) {
                val k = nearest(pose, d, radius) ?: continue
                sxx += kx[k] * kx[k]; sx += kx[k]; su += d.x; sxu += kx[k] * d.x
                szz += kz[k] * kz[k]; sz += kz[k]; sv += d.y; szv += kz[k] * d.y
                n++
            }
            inliers = n
            if (n < 3) return null
            // u = s·x + tx and v = s·z + ty share s: solve the 3×3 normal equations.
            val a = arrayOf(
                doubleArrayOf(sxx + szz, sx, sz, sxu + szv),
                doubleArrayOf(sx, n.toDouble(), 0.0, su),
                doubleArrayOf(sz, 0.0, n.toDouble(), sv),
            )
            val sol = solve3(a) ?: return null
            if (sol[0] <= 0) return null
            pose = NotesPose(sol[0], sol[1], sol[2])
        }
        return Registration(pose, inliers, droplets.size)
    }

    private fun nearest(pose: NotesPose, d: Droplet, radius: Double): Int? {
        var best = -1
        var bestD = radius * radius
        for (k in kx.indices) {
            val du = pose.u(kx[k]) - d.x
            if (abs(du) > radius) continue
            val dv = pose.v(kz[k]) - d.y
            val dd = du * du + dv * dv
            if (dd <= bestD) {
                bestD = dd; best = k
            }
        }
        return best.takeIf { it >= 0 }
    }

    private var usedCount = 0

    private fun clear() {
        for (j in 0 until usedCount) counts[used[j]] = 0
        usedCount = 0
    }

    private fun vote(bx: Long, by: Long) {
        val key = ((bx + OFFSET) shl 32) or ((by + OFFSET) and 0xffffffffL)
        var slot = ((key * -7046029254386353131L) ushr (64 - TABLE_BITS)).toInt()
        while (true) {
            if (counts[slot] == 0) {
                keys[slot] = key
                counts[slot] = 1
                used[usedCount++] = slot
                return
            }
            if (keys[slot] == key) {
                counts[slot]++
                return
            }
            slot = (slot + 1) and (TABLE - 1)
        }
    }

    companion object {
        /**
         * The scale ladder, in work px per world unit. 0.067 is about as far out as the app's map shows
         * with droplets still apart enough to match (a third of 0.2 px/unit on the reference phone);
         * below it chance alignments of crowded Koroks beat the real one.
         */
        const val MIN_SCALE = 0.067
        const val MAX_SCALE = 1.4

        /** 1.1% per rung keeps the error at a screen's edge under one bin. */
        private const val STEP = 1.011
        private const val BIN = 4.0
        private const val VOTERS = 40
        private const val CANDIDATES = 4
        const val MIN_INLIERS = 8
        const val MIN_TRACKED_INLIERS = 4
        const val MIN_RATIO = 0.6

        private const val TABLE_BITS = 19
        private const val TABLE = 1 shl TABLE_BITS
        private const val OFFSET = 1L shl 30

        private fun solve3(m: Array<DoubleArray>): DoubleArray? {
            for (c in 0 until 3) {
                val p = (c until 3).maxBy { abs(m[it][c]) }
                if (abs(m[p][c]) < 1e-12) return null
                val tmp = m[c]; m[c] = m[p]; m[p] = tmp
                for (r in 0 until 3) if (r != c) {
                    val f = m[r][c] / m[c][c]
                    for (k in c..3) m[r][k] -= f * m[c][k]
                }
            }
            return DoubleArray(3) { m[it][3] / m[it][it] }
        }
    }
}
