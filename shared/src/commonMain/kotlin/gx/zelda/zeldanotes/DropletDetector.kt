package gx.zelda.zeldanotes

import kotlin.math.sqrt

/** A droplet found whole on screen: its centre in work px and how well it matched. */
data class Droplet(val x: Double, val y: Double, val score: Double)

/**
 * Finds droplets drawn whole (not partly under another icon) by masked normalised cross-correlation
 * with the template. Only spots where one of the template's fill probes lands on tan are tried, on a 2 px grid first.
 */
class DropletDetector(private val template: DropletTemplate) {
    fun detect(image: NotesImage): List<Droplet> {
        val t = template
        val maxX = image.width - t.width
        val minY = image.top
        val maxY = image.bottom - t.height
        if (maxX < 0 || maxY < minY) return emptyList()
        val coarse = mutableListOf<Triple<Int, Int, Double>>()
        for (oy in minY..maxY step 2) for (ox in 0..maxX step 2) {
            if (t.probes.all { p -> image.tan[(oy + p / t.width) * image.width + ox + p % t.width] < 0.3f }) continue
            val s = score(image, ox, oy)
            if (s >= COARSE_SCORE) coarse += Triple(ox, oy, s)
        }
        // Refine each coarse hit on the 1 px grid, then keep local maxima.
        val refined = coarse.map { (ox, oy, s0) ->
            var best = Triple(ox, oy, s0)
            for (dy in -1..1) for (dx in -1..1) {
                val x = ox + dx
                val y = oy + dy
                if ((dx == 0 && dy == 0) || x < 0 || x > maxX || y < minY || y > maxY) continue
                val s = score(image, x, y)
                if (s > best.third) best = Triple(x, y, s)
            }
            best
        }.sortedByDescending { it.third }
        val out = mutableListOf<Droplet>()
        for ((ox, oy, s) in refined) {
            if (s < MIN_SCORE) break
            val x = (ox + t.cx).toDouble()
            val y = (oy + t.cy).toDouble()
            if (out.none { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) < NMS_RADIUS * NMS_RADIUS }) out += Droplet(x, y, s)
        }
        return out
    }

    /** Masked NCC with the template's top-left at (ox, oy). */
    internal fun score(image: NotesImage, ox: Int, oy: Int): Double {
        val t = template
        val g = image.grey
        val w = image.width
        var s = 0.0
        var ss = 0.0
        var st = 0.0
        for (i in t.nccT.indices) {
            val v = g[(oy + t.nccY[i]) * w + ox + t.nccX[i]].toDouble()
            s += v
            ss += v * v
            st += v * t.nccT[i]
        }
        val n = t.nccT.size
        val variance = ss - s * s / n
        if (variance <= 1e-6) return 0.0
        return st / (t.nccNorm * sqrt(variance))
    }

    companion object {
        const val COARSE_SCORE = 0.45
        const val MIN_SCORE = 0.6

        /** Two droplets closer than this (work px) are one. */
        const val NMS_RADIUS = 6.0
    }
}
