package gx.zelda.screencam.live

import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A Korok seed's world position (x east, z south). */
data class KorokSpot(val id: String, val x: Double, val z: Double)

/**
 * Reads found-Korok markers. At max zoom the game draws a seed marker on every Korok you've found,
 * and nothing on the rest. A spot is cut from the screen at half canonical resolution (one pixel
 * per two screenshot pixels) and matched with the marker's grey template within a few pixels.
 */
class KorokMarkerReader(private val template: GrayImage, spots: List<KorokSpot>) {
    private val spots = spots.toList()

    /** Pairs drawn on top of each other at max zoom: neither can be read alone. */
    val stacked: Set<String> = buildSet {
        for (a in spots) for (b in spots) {
            if (a.id != b.id && hypot(a.x - b.x, a.z - b.z) * MapLocator.MAX_ZOOM_SCALE < STACK_PX) add(a.id)
        }
    }

    class Reading(val id: String, val canonical: Point, val score: Double)

    /**
     * Scores every Korok whose marker would be fully inside the readable part of the screen, given
     * the map's world position at canonical (0, 0).
     */
    fun read(frame: GrayImage, screenToFrame: Homography, origin: Point): List<Reading> {
        val out = ArrayList<Reading>()
        for (s in spots) {
            val cx = MapLocator.MAX_ZOOM_SCALE * (s.x - origin.x)
            val cy = MapLocator.MAX_ZOOM_SCALE * (s.z - origin.y)
            if (!readable(cx, cy)) continue
            out += Reading(s.id, Point(cx, cy), score(frame, screenToFrame, cx, cy) ?: continue)
        }
        return out
    }

    /** Best template match within [SEARCH] half-canonical pixels of (cx, cy); null if off the frame. */
    fun score(frame: GrayImage, screenToFrame: Homography, cx: Double, cy: Double): Double? =
        bestMatch(patch(frame, screenToFrame, cx, cy, template.width + 2 * SEARCH, template.height + 2 * SEARCH) ?: return null, template)?.third

    companion object {
        /**
         * A [pw]×[ph] patch of the screen centred on canonical (cx, cy), at half canonical
         * resolution; null if any of it is off the frame.
         */
        fun patch(frame: GrayImage, screenToFrame: Homography, cx: Double, cy: Double, pw: Int, ph: Int): GrayImage? {
            val out = FloatArray(pw * ph)
            val m = screenToFrame.m
            for (j in 0 until ph) for (i in 0 until pw) {
                val x = cx + (i - pw / 2.0 + 0.5) * 2
                val y = cy + (j - ph / 2.0 + 0.5) * 2
                val w = m[6] * x + m[7] * y + m[8]
                val v = frame.sample((m[0] * x + m[1] * y + m[2]) / w - 0.5, (m[3] * x + m[4] * y + m[5]) / w - 0.5)
                if (v.isNaN()) return null
                out[j * pw + i] = v
            }
            return GrayImage(pw, ph, out)
        }

        /** The offset in [patch] where [template] matches best, and its zero-mean NCC. */
        fun bestMatch(patch: GrayImage, template: GrayImage): Triple<Int, Int, Double>? {
            val pw = patch.width
            val tMean = template.pixels.average().toFloat()
            val tz = FloatArray(template.pixels.size) { template.pixels[it] - tMean }
            val tNorm = sqrt(tz.sumOf { it.toDouble() * it })
            val n = tz.size
            var best: Triple<Int, Int, Double>? = null
            for (oy in 0..patch.height - template.height) for (ox in 0..pw - template.width) {
                var sm = 0.0
                var smm = 0.0
                var smt = 0.0
                for (ty in 0 until template.height) {
                    val po = (oy + ty) * pw + ox
                    val to = ty * template.width
                    for (tx in 0 until template.width) {
                        val v = patch.pixels[po + tx].toDouble()
                        sm += v
                        smm += v * v
                        smt += v * tz[to + tx]
                    }
                }
                val vm = smm - sm * sm / n
                if (vm > 1e-6) {
                    val c = smt / (sqrt(vm) * tNorm)
                    if (best == null || c > best.third) best = Triple(ox, oy, c)
                }
            }
            return best
        }

        /** Search radius, in half-canonical pixels (so ±16 screenshot pixels). */
        const val SEARCH = 8

        /** Korok pairs closer than this on the screen at max zoom overlap. */
        const val STACK_PX = 25.0

        /** Inside the map's viewport with a margin, away from the readouts and the cursor. */
        fun readable(cx: Double, cy: Double): Boolean {
            if (cx < 270 || cx > 1670 || cy < 160 || cy > 890) return false
            if (cx < 280 && cy > 740) return false // completion %, clock, temperature
            if (cx > 1500 && cy > 850) return false // Sheikah Sensor
            if (abs(cx - 960) < 50 && abs(cy - 540) < 50) return false // the cursor
            return true
        }
    }
}

/** How sure a Korok's reading is after one or more frames. */
enum class KorokVerdict { FOUND, EMPTY, UNSURE }

/** Marker scores gathered across frames, Korok by Korok. */
class KorokEvidence(private val stacked: Set<String> = emptySet()) {
    private class Tally(var seen: Int = 0, var hits: Int = 0, var misses: Int = 0, var best: Double = -1.0)

    private val tallies = LinkedHashMap<String, Tally>()

    fun add(id: String, score: Double) {
        val t = tallies.getOrPut(id) { Tally() }
        t.seen++
        if (score >= MARKER) t.hits++
        if (score <= NO_MARKER) t.misses++
        t.best = max(t.best, score)
    }

    val ids: Set<String> get() = tallies.keys

    fun framesSeen(id: String): Int = tallies[id]?.seen ?: 0

    fun verdict(id: String): KorokVerdict {
        val t = tallies[id] ?: return KorokVerdict.UNSURE
        if (id in stacked) return KorokVerdict.UNSURE
        return when {
            t.hits >= MIN_HITS && t.hits >= HIT_SHARE * t.seen -> KorokVerdict.FOUND
            t.misses >= MIN_MISSES && t.hits == 0 -> KorokVerdict.EMPTY
            else -> KorokVerdict.UNSURE
        }
    }

    fun verdicts(): Map<String, KorokVerdict> = tallies.keys.associateWith(::verdict)

    companion object {
        /** A frame scoring this or more shows a marker. */
        const val MARKER = 0.6

        /** A frame scoring this or less shows none. */
        const val NO_MARKER = 0.45
        const val MIN_HITS = 3
        const val HIT_SHARE = 0.7
        const val MIN_MISSES = 4
    }
}

object KorokMarkerTemplate {
    fun path(game: Game) = "files/${game.id}/korok_marker.json"

    @Serializable
    private class TemplateJson(val _comment: String = COMMENT, val width: Int, val height: Int, val rows: List<String>)

    private const val COMMENT =
        "Generated by KorokClipGoldenTest from the handheld clip's confirmed markers; rerun it with REGENERATE_KOROK_MARKER=1."

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun parse(text: String): GrayImage {
        val t = json.decodeFromString(TemplateJson.serializer(), text)
        val v = t.rows.flatMap { r -> r.trim().split(' ').map { it.toFloat() } }
        return GrayImage(t.width, t.height, v.toFloatArray())
    }

    fun encode(template: GrayImage): String = json.encodeToString(
        TemplateJson.serializer(),
        TemplateJson(
            width = template.width,
            height = template.height,
            rows = (0 until template.height).map { y -> (0 until template.width).joinToString(" ") { x -> template[x, y].roundToInt().toString() } },
        ),
    ) + "\n"

    suspend fun load(game: Game): GrayImage = parse(Res.readBytes(path(game)).decodeToString())
}
