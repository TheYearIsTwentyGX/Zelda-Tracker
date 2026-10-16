package gx.zelda.screencam.live

import gx.zelda.core.Games
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.screencam.ScreenChromeRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The live Korok scan on real clips: `src/jvmTest/resources/korok-clips/<clip>/`, made by
 * `tools/korok-clips/extract.py` from a phone video of the max-zoom map, with a hand-checked
 * `expected.json`. Every clip folder found is run, so a new clip (a docked TV, say) only needs the
 * script and its expected outcomes.
 */
class KorokClipGoldenTest {
    @Serializable
    private class Expected(
        /** Koroks whose marker is visible in the clip: must read Found. */
        val found: List<String>,
        /** Koroks in view with no marker: must never read Found. */
        val empty: List<String>,
        val note: String = "",
    )

    @Serializable
    private class ClipInfo(val source: String, val sourceFps: Double, val step: Int, val frames: Int, val width: Int)

    private class Clip(val name: String, val dir: File, val info: ClipInfo, val expected: Expected?) {
        fun frames(): Sequence<GrayImage> = (0 until info.frames).asSequence().map { i ->
            decodeGrayImage(File(dir, "frames/%04d.jpg".format(i)).readBytes()) ?: fail("can't decode $name frame $i")
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TEMPLATE_W = 28
        const val TEMPLATE_H = 18
    }

    private fun clips(): List<Clip> {
        val root = File(javaClass.getResource("/korok-clips")?.toURI() ?: fail("no korok-clips"))
        return root.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }.map { dir ->
            val info = json.decodeFromString(ClipInfo.serializer(), File(dir, "clip.json").readText())
            val expected = File(dir, "expected.json").takeIf { it.exists() }?.let { json.decodeFromString(Expected.serializer(), it.readText()) }
            Clip(dir.name, dir, info, expected)
        }
    }

    private val spots by lazy { runBlocking { KorokRepository.load(Games.BOTW) }.koroks.map { KorokSpot(it.id, it.x, it.z) } }

    private fun scanner(template: GrayImage) = runBlocking {
        LiveKorokScanner(
            ScreenChromeRepository.load(Games.BOTW),
            ChromeTracker(ChromeTracker.loadReference(Games.BOTW)),
            MapLocator(TileMosaic(BundledTiles)),
            KorokMarkerReader(template, spots),
        )
    }

    private class Run(val results: List<LiveFrameResult>, val scanner: LiveKorokScanner)

    private fun run(clip: Clip, template: GrayImage): Run {
        val s = scanner(template)
        val results = runBlocking { clip.frames().toList().map { s.process(it) } }
        return Run(results, s)
    }

    /** A plain bright hump on a darker ground, for runs before the real template exists. */
    private fun placeholderTemplate(): GrayImage = GrayImage(28, 18, FloatArray(28 * 18) { i ->
        val dx = (i % 28 - 13.5) / 10.0
        val dy = (i / 28 - 8.5) / 5.5
        if (dx * dx + dy * dy <= 1) 230f else 90f
    })

    private fun template(): GrayImage = runCatching { runBlocking { KorokMarkerTemplate.load(Games.BOTW) } }.getOrElse { placeholderTemplate() }

    /**
     * `korok_marker.json`: the confirmed markers of every clip, cut from tracked frames, aligned to
     * their mean and averaged. Rerun with REGENERATE_KOROK_MARKER=1 after changing tracking or adding a
     * clip; with KOROK_SHEET=<dir> it also writes a sheet of every Korok spot seen, for checking
     * expected.json by eye.
     */
    @Test
    fun markerTemplate() {
        val regenerate = System.getenv("REGENERATE_KOROK_MARKER") == "1"
        val sheetDir = System.getenv("KOROK_SHEET")
        if (!regenerate && sheetDir == null) {
            val t = template()
            assertEquals(TEMPLATE_W to TEMPLATE_H, t.width to t.height)
            return
        }
        val crops = ArrayList<GrayImage>()
        val spotsById = spots.associateBy { it.id }
        val sheet = LinkedHashMap<String, GrayImage>()
        for (clip in clips()) {
            val expected = clip.expected ?: continue
            val frames = clip.frames().toList()
            val run = run(clip, placeholderTemplate())
            for ((i, r) in run.results.withIndex()) {
                val h = r.screenToFrame ?: continue
                val o = r.mapOrigin ?: continue
                if (!r.tracked) continue
                for (reading in r.markers) {
                    val sp = spotsById.getValue(reading.id)
                    val cx = MapLocator.MAX_ZOOM_SCALE * (sp.x - o.x)
                    val cy = MapLocator.MAX_ZOOM_SCALE * (sp.z - o.y)
                    if (reading.id !in sheet) KorokMarkerReader.patch(frames[i], h, cx, cy, 64, 40)?.let { sheet[reading.id] = it }
                    if (reading.id in expected.found) {
                        KorokMarkerReader.patch(frames[i], h, cx, cy, TEMPLATE_W + 8, TEMPLATE_H + 8)?.let { crops += it }
                    }
                }
            }
        }
        if (sheetDir != null) writeSheet(sheet, File(sheetDir, "korok-sheet.png"))
        if (!regenerate) return
        assertTrue(crops.size >= 20, "only ${crops.size} marker crops")
        // Mean of the centres, then each crop re-cut where it best matches that mean, twice.
        var mean = average(crops.map { crop(it, 4, 4) })
        repeat(2) {
            mean = average(crops.map { c -> KorokMarkerReader.bestMatch(c, mean)!!.let { (x, y, _) -> crop(c, x, y) } })
        }
        File("src/commonMain/composeResources/${KorokMarkerTemplate.path(Games.BOTW)}").writeText(KorokMarkerTemplate.encode(mean))
        println("korok_marker.json from ${crops.size} crops")
    }

    private fun crop(img: GrayImage, x0: Int, y0: Int) = GrayImage(TEMPLATE_W, TEMPLATE_H, FloatArray(TEMPLATE_W * TEMPLATE_H) { img[x0 + it % TEMPLATE_W, y0 + it / TEMPLATE_W] })

    private fun average(imgs: List<GrayImage>): GrayImage {
        // Each crop normalised to zero mean, unit spread first, so exposure doesn't weight them.
        val out = FloatArray(TEMPLATE_W * TEMPLATE_H)
        for (img in imgs) {
            val m = img.pixels.average()
            val sd = kotlin.math.sqrt(img.pixels.sumOf { (it - m) * (it - m) } / img.pixels.size).coerceAtLeast(1.0)
            for (k in out.indices) out[k] += ((img.pixels[k] - m) / sd).toFloat()
        }
        // Back to a 0–255 range for storage: mean 128, about 40 per spread.
        return GrayImage(TEMPLATE_W, TEMPLATE_H, FloatArray(out.size) { (128 + 40 * out[it] / imgs.size).coerceIn(0f, 255f) })
    }

    private fun writeSheet(patches: Map<String, GrayImage>, file: File) {
        val cols = 8
        val scale = 3
        val w = 64 * scale
        val h = 40 * scale + 14
        val rows = (patches.size + cols - 1) / cols
        val img = java.awt.image.BufferedImage(cols * w, rows * h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((k, entry) in patches.entries.withIndex()) {
            val (id, p) = entry
            val ox = (k % cols) * w
            val oy = (k / cols) * h
            for (y in 0 until 40 * scale) for (x in 0 until 64 * scale) {
                val v = p[x / scale, y / scale].toInt().coerceIn(0, 255)
                img.setRGB(ox + x, oy + 14 + y, (v shl 16) or (v shl 8) or v)
            }
            g.color = java.awt.Color.YELLOW
            g.drawString(id, ox + 4, oy + 12)
        }
        javax.imageio.ImageIO.write(img, "png", file)
    }

    /** Median and 90th-percentile ms per stage, on this machine's JVM, for comparing with the phone's stats overlay. */
    private fun benchmark(results: List<LiveFrameResult>): String {
        fun stat(label: String, values: List<Double>): String {
            val v = values.sorted()
            if (v.isEmpty()) return "$label -"
            return "$label median %.1f p90 %.1f".format(v[v.size / 2], v[(v.size * 9) / 10])
        }
        val tracking = results.filter { it.times.acquire == 0.0 }
        return listOf(
            stat("total", tracking.map { it.times.total }),
            stat("track", tracking.map { it.times.track }),
            stat("view", tracking.map { it.times.view }),
            stat("locate", tracking.map { it.times.locate }),
            stat("markers", tracking.map { it.times.markers }),
            stat("acquire (first frames)", results.filter { it.times.acquire > 0 }.map { it.times.total }),
        ).joinToString(" | ", prefix = "ms per frame (desktop JVM): ")
    }

    @Test
    fun everyClipTracksAndReadsItsMarkers() {
        val log = System.getenv("CLIP_LOG")?.let { StringBuilder() }
        for (clip in clips()) {
            val expected = clip.expected ?: continue
            val run = run(clip, template())
            val r = run.results
            val tracked = r.count { it.tracked }
            val verdicts = run.scanner.evidence.verdicts()
            log?.appendLine("== ${clip.name}: tracked $tracked/${r.size}; ${run.scanner.stats.summary()}")
            if (log != null) {
                r.forEachIndexed { i, f ->
                    log.appendLine("$i ${f.phase} chrome ${"%.2f".format(f.chromeCorrelation)} peak ${"%.2f".format(f.mapPeak)} ratio ${"%.2f".format(f.mapRatio)} origin ${f.mapOrigin?.let { "(%.0f,%.0f)".format(it.x, it.y) }} tracked ${f.tracked} ${f.markers.joinToString { "${it.id}:%.2f".format(it.score) }} | %.0f ms".format(f.times.total))
                }
                for ((id, v) in verdicts) log.appendLine("verdict $id $v seen ${run.scanner.evidence.framesSeen(id)}")
                File(System.getenv("CLIP_LOG")).writeText(log.toString())
            }
            println("${clip.name}: tracked $tracked of ${r.size} frames")
            println(benchmark(r))
            assertTrue(tracked >= 0.95 * r.size, "${clip.name}: tracked $tracked of ${r.size}")
            val missed = expected.found.filter { verdicts[it] != KorokVerdict.FOUND }
            assertEquals(emptyList(), missed, "${clip.name}: markers not read as Found (${missed.map { it to verdicts[it] }})")
            val falseFound = expected.empty.filter { verdicts[it] == KorokVerdict.FOUND }
            assertEquals(emptyList(), falseFound, "${clip.name}: empty spots read as Found")
        }
    }
}
