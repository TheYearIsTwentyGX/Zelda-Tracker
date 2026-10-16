package gx.zelda.screencam

import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.shrines.scan.MapImage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The map screen's frame: the ornaments, the Runes / Map / Album header and the button bar around
 * the map. It's the same in every screenshot, so a photo is checked against it twice: its grey
 * [thumb] tells a screen from a lit room and says which way up the screen is, and its colour
 * [histograms] calibrate the camera, much like a colour chart.
 *
 * Positions are canonical: the 1920×1080 frame of a Switch screenshot.
 */
class ScreenChrome(
    /** Grey frame at [THUMB_W]×[THUMB_H], 0–255, NaN where the cell isn't frame. */
    val thumb: FloatArray,
    /** Red, green and blue histograms of the frame's pixels. */
    val histograms: List<IntArray>,
    /** The same in Hero's Path mode, which turns the frame's ornaments green. */
    val heroesPathHistograms: List<IntArray>,
) {
    init {
        require(thumb.size == THUMB_W * THUMB_H) { "thumbnail must be $THUMB_W × $THUMB_H" }
        require(histograms.size == 3 && heroesPathHistograms.size == 3) { "one histogram per channel" }
    }

    /**
     * Where the screen is in [photo]: the canonical → photo homography of the [ScreenFinder]
     * outline, with each corner tried as top left, whose frame looks most like this one, and its score.
     */
    fun locate(photo: MapImage): Pair<Homography, Double>? {
        var best: Pair<Homography, Double>? = null
        for (outline in ScreenFinder.candidates(photo)) for (turn in 0..3) {
            val corners = List(4) { outline[(it + turn) % 4] }
            val h = Homography.fit(CORNERS, corners) ?: continue
            val score = frameScore(Rectifier.rectify(photo, h, THUMB_W, THUMB_H, THUMB_W / WIDTH.toDouble()))
            if (score > (best?.second ?: -2.0)) best = h to score
        }
        return best
    }

    /** How much [screen] (any size, canonical layout) looks like the frame, −1 to 1. */
    fun frameScore(screen: MapImage): Double = ncc(thumbnail(screen), thumb)

    /**
     * Maps each channel of [screen] so its frame's levels match the reference frame's: a straight
     * line between each pair of matching percentiles. Matching every level instead would bend colours
     * the frame hardly has, like the orange of an unfinished shrine.
     */
    fun matchColours(screen: MapImage, heroesPath: Boolean): MapImage {
        val ref = (if (heroesPath) heroesPathHistograms else histograms).map(::cdf)
        val cur = histograms(screen).map(::cdf)
        val luts = (0..2).map { ch ->
            // (camera level, reference level) at each percentile, then through the ends of the range.
            val knots = QUANTILES.map { q -> level(cur[ch], q) to level(ref[ch], q) }
                .fold(mutableListOf<Pair<Double, Double>>()) { acc, k -> if (acc.isEmpty() || k.first > acc.last().first + 0.5) acc += k; acc }
            IntArray(256) { v -> interpolate(knots, v.toDouble()).roundToInt().coerceIn(0, 255) }
        }
        val px = screen.argb
        val out = IntArray(px.size) { i ->
            val c = px[i]
            (0xff shl 24) or (luts[0][(c shr 16) and 0xff] shl 16) or (luts[1][(c shr 8) and 0xff] shl 8) or luts[2][c and 0xff]
        }
        return MapImage(screen.width, screen.height, out)
    }

    companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val THUMB_W = 192
        const val THUMB_H = 108

        /** The four canonical corners, clockwise from top left. */
        val CORNERS = listOf(Point(0.0, 0.0), Point(WIDTH.toDouble(), 0.0), Point(WIDTH.toDouble(), HEIGHT.toDouble()), Point(0.0, HEIGHT.toDouble()))

        /** True for canonical pixels of the frame: outside the map, minus the clock and Sheikah Sensor readouts. */
        fun isFrame(x: Int, y: Int): Boolean {
            if (y in 92..995 && x in 170..1750) return false
            if (x in 40..260 && y in 760..960) return false
            if (x in 1530..1820 && y in 870..960) return false
            return true
        }

        /** Grey thumbnail of [screen]'s frame, [THUMB_W] wide; cells that are mostly map are NaN. */
        fun thumbnail(screen: MapImage): FloatArray {
            val f = screen.width / THUMB_W
            require(f >= 1 && screen.height / f >= THUMB_H) { "screen must be at least $THUMB_W × $THUMB_H" }
            val toCanonical = WIDTH.toDouble() / screen.width
            val out = FloatArray(THUMB_W * THUMB_H)
            for (ty in 0 until THUMB_H) for (tx in 0 until THUMB_W) {
                var sum = 0f
                var n = 0
                for (dy in 0 until f) for (dx in 0 until f) {
                    val x = tx * f + dx
                    val y = ty * f + dy
                    if (!isFrame(((x + 0.5) * toCanonical).toInt(), ((y + 0.5) * toCanonical).toInt())) continue
                    val c = screen.argb[y * screen.width + x]
                    sum += 0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
                    n++
                }
                out[ty * THUMB_W + tx] = if (2 * n > f * f) sum / n else Float.NaN
            }
            return out
        }

        /** Red, green and blue histograms of a canonical 1920×1080 [screen]'s frame, every other pixel. */
        fun histograms(screen: MapImage): List<IntArray> {
            require(screen.width == WIDTH && screen.height == HEIGHT) { "screen must be $WIDTH × $HEIGHT" }
            val h = List(3) { IntArray(256) }
            for (y in 0 until HEIGHT step 2) for (x in 0 until WIDTH step 2) {
                if (!isFrame(x, y)) continue
                val c = screen.argb[y * WIDTH + x]
                h[0][(c shr 16) and 0xff]++
                h[1][(c shr 8) and 0xff]++
                h[2][c and 0xff]++
            }
            return h
        }

        /** The reference from real screenshots: thumbnails averaged, histograms summed. */
        fun of(screenshots: List<MapImage>, heroesPathScreenshots: List<MapImage>): ScreenChrome {
            val thumbs = screenshots.map(::thumbnail)
            val thumb = FloatArray(THUMB_W * THUMB_H) { i ->
                if (thumbs[0][i].isNaN()) Float.NaN else thumbs.sumOf { it[i].toDouble() }.toFloat() / thumbs.size
            }.map { if (it.isNaN()) it else it.roundToInt().toFloat() }.toFloatArray()
            fun sum(shots: List<MapImage>) = shots.map(::histograms).reduce { a, b -> List(3) { ch -> IntArray(256) { a[ch][it] + b[ch][it] } } }
            return ScreenChrome(thumb, sum(screenshots), sum(heroesPathScreenshots))
        }

        private val QUANTILES = doubleArrayOf(0.02, 0.1, 0.25, 0.5, 0.75, 0.9, 0.97, 0.995)

        /** The level below which a [q] share of pixels fall, interpolated between whole levels. */
        private fun level(cdf: DoubleArray, q: Double): Double {
            var j = 0
            while (j < 255 && cdf[j] < q) j++
            val below = if (j == 0) 0.0 else cdf[j - 1]
            val span = cdf[j] - below
            return if (span > 0) j - 1 + (q - below) / span + 0.5 else j.toDouble()
        }

        /** Piecewise-linear through [knots], extended past the first and last with their segments' slopes. */
        private fun interpolate(knots: List<Pair<Double, Double>>, x: Double): Double {
            if (knots.size == 1) return x - knots[0].first + knots[0].second
            val i = when (val above = knots.indexOfFirst { it.first >= x }) {
                -1 -> knots.size - 1
                0 -> 1
                else -> above
            }
            val (x0, y0) = knots[i - 1]
            val (x1, y1) = knots[i]
            return y0 + (x - x0) * (y1 - y0) / (x1 - x0)
        }

        private fun cdf(h: IntArray): DoubleArray {
            val total = h.sum().coerceAtLeast(1).toDouble()
            var acc = 0.0
            return DoubleArray(256) { acc += h[it]; acc / total }
        }

        /** Normalised cross-correlation over the cells both arrays have. */
        fun ncc(a: FloatArray, b: FloatArray): Double {
            var n = 0
            var sa = 0.0
            var sb = 0.0
            var saa = 0.0
            var sbb = 0.0
            var sab = 0.0
            for (i in a.indices) {
                if (a[i].isNaN() || b[i].isNaN()) continue
                n++
                sa += a[i]
                sb += b[i]
                saa += a[i] * a[i]
                sbb += b[i] * b[i]
                sab += a[i] * b[i]
            }
            if (n < 10) return 0.0
            val va = saa - sa * sa / n
            val vb = sbb - sb * sb / n
            return if (va * vb > 1e-9) (sab - sa * sb / n) / sqrt(va * vb) else 0.0
        }
    }
}

object ScreenChromeRepository {
    /** Each game's frame reference lives at `files/<game id>/map_chrome.json`. */
    fun path(game: Game): String = "files/${game.id}/map_chrome.json"

    @Serializable
    private class ChromeJson(
        @SerialName("_comment") val comment: String = COMMENT,
        val thumbWidth: Int,
        val thumbHeight: Int,
        /** One row per line, space-separated, −1 where the cell isn't frame. */
        val thumb: List<String>,
        /** Red, green, blue: 256 space-separated counts each. */
        val histograms: List<String>,
        val heroesPathHistograms: List<String>,
    )

    private const val COMMENT =
        "Generated by ScreenChromeReferenceTest from the golden map screenshots; rerun it with REGENERATE_SCREEN_CHROME=1."

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun parse(text: String): ScreenChrome {
        val c = json.decodeFromString(ChromeJson.serializer(), text)
        require(c.thumbWidth == ScreenChrome.THUMB_W && c.thumbHeight == ScreenChrome.THUMB_H) { "unexpected thumbnail size" }
        val thumb = c.thumb.flatMap { row -> row.trim().split(' ').map { v -> v.toInt().let { if (it < 0) Float.NaN else it.toFloat() } } }
        fun hist(rows: List<String>) = rows.map { row -> row.trim().split(' ').map(String::toInt).toIntArray() }
        return ScreenChrome(thumb.toFloatArray(), hist(c.histograms), hist(c.heroesPathHistograms))
    }

    fun encode(chrome: ScreenChrome): String = json.encodeToString(
        ChromeJson.serializer(),
        ChromeJson(
            thumbWidth = ScreenChrome.THUMB_W,
            thumbHeight = ScreenChrome.THUMB_H,
            thumb = (0 until ScreenChrome.THUMB_H).map { y ->
                (0 until ScreenChrome.THUMB_W).joinToString(" ") { x ->
                    chrome.thumb[y * ScreenChrome.THUMB_W + x].let { if (it.isNaN()) "-1" else it.roundToInt().toString() }
                }
            },
            histograms = chrome.histograms.map { it.joinToString(" ") },
            heroesPathHistograms = chrome.heroesPathHistograms.map { it.joinToString(" ") },
        ),
    ) + "\n"

    suspend fun load(game: Game): ScreenChrome = parse(Res.readBytes(path(game)).decodeToString())
}
