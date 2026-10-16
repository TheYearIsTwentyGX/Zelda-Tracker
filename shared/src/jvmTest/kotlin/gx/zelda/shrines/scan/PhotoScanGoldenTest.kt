package gx.zelda.shrines.scan

import gx.zelda.core.Games
import gx.zelda.screencam.FakeCamera
import gx.zelda.screencam.Point
import gx.zelda.screencam.ScreenChromeRepository
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.model.ShrineCatalog
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [PhotoShotReader] on photos of the screen, each compared spot by spot with the scanner's reading
 * of the screenshot it shows. A photo may leave more spots Unsure, but must never give a definite
 * reading the screenshot contradicts.
 *
 * - `photos/all-complete-north-handheld.jpg`: a real phone photo (HDR) of a Switch in handheld mode,
 *   cropped to the screen, showing the same save and pan as `all-complete/1000002225`. The screen is
 *   on its side in the photo, and the photo has strong moiré banding and bloom.
 * - [FakeCamera] photos of the four golden screenshots: keystoned, turned, colour-shifted, blurred.
 */
class PhotoScanGoldenTest {
    private object Samples {
        val catalog: ShrineCatalog by lazy { runBlocking { ShrineRepository.load(Games.BOTW) } }
        val data: ShrineMapData by lazy { runBlocking { ShrineMapRepository.load(Games.BOTW) } }
        val scanner by lazy { ShrineMapScanner(data) }
        val reader by lazy { PhotoShotReader(scanner, data, runBlocking { ScreenChromeRepository.load(Games.BOTW) }) }
        private val images = HashMap<String, MapImage>()

        fun image(name: String): MapImage = images.getOrPut(name) {
            val bytes = Samples::class.java.getResourceAsStream("/map-samples/$name.jpg")?.readBytes() ?: fail("missing sample $name")
            decodeMapImage(bytes) ?: fail("can't decode $name")
        }

        fun truth(name: String): ShotScan.Read = assertIs<ShotScan.Read>(scanner.scan(image(name)), name)

        fun name(id: String): String = catalog.shrines.first { it.id == id }.name
    }

    /** Spots where the photo's definite reading differs from the screenshot's, and definite spots it left Unsure. */
    private class Diff(val contradictions: List<String>, val lost: List<String>)

    private fun diff(photo: ShotScan.Read, screenshot: ShotScan.Read): Diff {
        val contradictions = mutableListOf<String>()
        val lost = mutableListOf<String>()
        for ((id, s) in screenshot.readings) {
            val p = photo.readings.getValue(id)
            if (!s.isDefinite || p.verdict == s.verdict) continue
            if (p.isDefinite) contradictions += "${Samples.name(id)}: ${s.verdict} → ${p.verdict}" else lost += Samples.name(id)
        }
        return Diff(contradictions, lost)
    }

    private fun timed(block: () -> ShotScan): Pair<ShotScan, Long> {
        val start = System.nanoTime()
        val scan = block()
        return scan to (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun realPhotoReadsLikeTheScreenshot() {
        val photo = Samples.image("photos/all-complete-north-handheld")
        val screenshot = Samples.truth("all-complete/1000002225")
        // As taken (screen on its side), and turned so the screen is upright.
        for ((label, image) in listOf("as taken" to photo, "turned" to FakeCamera.rotate(photo))) {
            val (scan, ms) = timed { Samples.reader.read(image) }
            val read = assertIs<ShotScan.Read>(scan, "$label: $scan")
            val reg = read.registration
            println("real photo ($label, ${image.width}×${image.height}): ${reg.anchors} anchors, rms ${"%.2f".format(reg.rmsPx)} px, $ms ms")
            assertTrue(reg.anchors >= 60, "$label: ${reg.anchors} anchors")
            assertTrue(reg.rmsPx < 1.5, "$label: rms ${reg.rmsPx}")
            assertEquals(0.135, reg.fittedScale, 0.002, label)
            // Same pan as the screenshot.
            assertEquals(Samples.truth("all-complete/1000002225").registration.ty, reg.ty, 6.0, label)
            val d = diff(read, screenshot)
            assertEquals(emptyList(), d.contradictions, label)
            // The screenshot reads 93 Completed; Sha Warvo and Noe Rajee sit under a stamp in the photo.
            assertTrue(d.lost.size <= 5, "$label lost: ${d.lost}")
            assertTrue(read.readings.values.none { it.verdict == ScanVerdict.NOT_DONE }, label)
            assertTrue(!read.heroesPath, label)
            assertTrue(ms < 15_000, "$label took $ms ms")
        }
    }

    /** [maxLost]: the share of the screenshot's definite spots the photo may leave unsure. */
    private class Case(val shot: String, val label: String, val camera: FakeCamera.Shot, val maxLost: Double = 0.2)

    @Test
    fun syntheticPhotosNeverContradictTheScreenshot() {
        val cases = listOf(
            Case("all-complete/1000002225", "keystoned", FakeCamera.Shot(4032, 3024, 0.75, yawDeg = 25.0, pitchDeg = 12.0, rollDeg = 4.0, shift = Point(80.0, -60.0), samples = 8)),
            Case(
                "all-complete/1000002224", "portrait, warm and bright",
                FakeCamera.Shot(3024, 4032, 0.9, yawDeg = 4.0, pitchDeg = 6.0, rollDeg = 91.0, gains = doubleArrayOf(1.15, 1.0, 0.8), exposure = 1.2, gamma = 0.85, samples = 8),
            ),
            Case("partial/1000002226", "shaky", FakeCamera.Shot(4032, 3024, 0.75, yawDeg = 5.0, pitchDeg = 3.0, rollDeg = 1.0, blur = 1.2, motion = 3.0, noise = 3.0, samples = 8)),
            // Few icons, and the trail covers many spots: the hardest shot.
            Case("partial/1000002228", "Hero's Path", FakeCamera.Shot(4032, 3024, 0.75, yawDeg = 5.0, pitchDeg = 3.0, rollDeg = 1.0), maxLost = 0.35),
        )
        for (c in cases) {
            val screenshot = Samples.truth(c.shot)
            val photo = FakeCamera.shoot(Samples.image(c.shot), c.camera)
            val (scan, ms) = timed { Samples.reader.read(photo) }
            val read = assertIs<ShotScan.Read>(scan, "${c.label}: $scan")
            val d = diff(read, screenshot)
            val definite = screenshot.readings.values.count { it.isDefinite }
            println("${c.shot} (${c.label}): ${read.registration.anchors} anchors, ${d.lost.size} of $definite definite left unsure, $ms ms")
            assertEquals(emptyList(), d.contradictions, c.label)
            assertTrue(d.lost.size <= c.maxLost * definite, "${c.label} lost ${d.lost}")
            assertEquals(screenshot.heroesPath, read.heroesPath, c.label)
        }
    }

    @Test
    fun readsUnfinishedShrinesFromAPhoto() {
        val shot = "partial/1000002226"
        val read = assertIs<ShotScan.Read>(Samples.reader.read(FakeCamera.shoot(Samples.image(shot), FakeCamera.Shot(4032, 3024, 0.75, yawDeg = 8.0, samples = 8))))
        val notDone = read.readings.filterValues { it.verdict == ScanVerdict.NOT_DONE }.keys
        val expected = Samples.truth(shot).readings.filterValues { it.verdict == ScanVerdict.NOT_DONE }.keys
        assertEquals(setOf("tena_kosah", "qua_raym", "shae_mosah"), expected)
        // The small orange glyph dulls in a photo. Qua Raym and Shae Mo'sah, on Eldin's lava and beside a
        // stable, are left unsure; none is invented.
        assertTrue(expected.containsAll(notDone) && "tena_kosah" in notDone, "read $notDone")
    }

    @Test
    fun rejectsPhotosItCantRead() {
        val north = Samples.image("all-complete/1000002225")
        val small = FakeCamera.Shot(1600, 1200, 0.5, samples = 4)
        assertEquals(RejectReason.SCREEN_TOO_SMALL, assertIs<ShotScan.Rejected>(Samples.reader.read(FakeCamera.shoot(north, small))).reason)

        // A resized screenshot fills the whole image: told to use the original, as before.
        val w = 1280
        val h = 720
        val resized = MapImage(w, h, IntArray(w * h) { i -> north.argb[(i / w * 3 / 2) * north.width + i % w * 3 / 2] })
        assertEquals(RejectReason.WRONG_SIZE, assertIs<ShotScan.Rejected>(Samples.reader.read(resized)).reason)

        // Just the map, stretched across the screen: no frame, so no game screen.
        val mapOnly = MapImage(1920, 1080, IntArray(1920 * 1080) { i -> north.argb[(300 + i / 1920 * 600 / 1080) * 1920 + 400 + i % 1920 * 1100 / 1920] })
        assertEquals(RejectReason.NO_SCREEN, assertIs<ShotScan.Rejected>(Samples.reader.read(FakeCamera.shoot(mapOnly, FakeCamera.Shot(2000, 1500, 0.9, samples = 4)))).reason)
    }

    /** The frame of the north screenshot around its icons spread to twice the spacing, as when zoomed in. */
    @Test
    fun rejectsAPhotoOfAZoomedInMap() {
        val img = Samples.image("all-complete/1000002225")
        val reg = Samples.truth("all-complete/1000002225").registration
        val pixels = img.argb.copyOf()
        for (y in ShrineMapScanner.VIEW_Y0 until ShrineMapScanner.VIEW_Y1) for (x in ShrineMapScanner.VIEW_X0 until ShrineMapScanner.VIEW_X1) {
            pixels[y * img.width + x] = 0xFF15181C.toInt()
        }
        val (cx, cy) = 960 to 523
        for (p in Samples.data.shrines.values + Samples.data.towers) {
            val u = (reg.scale * p.x + reg.tx).roundToInt()
            val v = (reg.scale * p.z + reg.ty).roundToInt()
            val u2 = cx + 2 * (u - cx)
            val v2 = cy + 2 * (v - cy)
            if (!ShrineMapScanner.inView(u.toDouble(), v.toDouble(), 20) || !ShrineMapScanner.inView(u2.toDouble(), v2.toDouble(), 20)) continue
            for (dy in -20..20) for (dx in -20..20) pixels[(v2 + dy) * img.width + u2 + dx] = img.argb[(v + dy) * img.width + u + dx]
        }
        val photo = FakeCamera.shoot(MapImage(img.width, img.height, pixels), FakeCamera.Shot(4032, 3024, 0.75, yawDeg = 5.0, samples = 8))
        val r = assertIs<ShotScan.Rejected>(Samples.reader.read(photo))
        assertEquals(RejectReason.NOT_MIN_ZOOM, r.reason, r.message)
    }
}
