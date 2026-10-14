package gx.zelda.shrines.scan

import gx.zelda.core.Games
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.model.ShrineCatalog
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The scanner against real Switch screenshots (`src/jvmTest/resources/map-samples`):
 *
 * - `all-complete/1000002225` (north) and `…2224` (south): a save with every shrine completed.
 * - `partial/1000002226` (north) and `…2228` (south, taken in Hero's Path mode): an early/mid-game
 *   save. The user confirmed Tena Ko'sah is unfinished there.
 *
 * The `prototype-results*.csv` files are the Python prototype's output for the same shots
 * (`tools/shrine-map-spike` on branch `worktree-shrine-map-spike`); the south partial one was made
 * with `run.py --scale 0.135`, since the spike predates that shot.
 */
class ShrineMapGoldenTest {
    private object Samples {
        val catalog: ShrineCatalog by lazy { runBlocking { ShrineRepository.load(Games.BOTW) } }
        val scanner: ShrineMapScanner by lazy { ShrineMapScanner(runBlocking { ShrineMapRepository.load(Games.BOTW) }) }
        private val images = HashMap<String, MapImage>()
        private val scans = HashMap<String, ShotScan.Read>()

        fun image(name: String): MapImage = images.getOrPut(name) {
            val bytes = Samples::class.java.getResourceAsStream("/map-samples/$name.jpg")?.readBytes() ?: fail("missing sample $name")
            decodeMapImage(bytes) ?: fail("can't decode $name")
        }

        fun scan(name: String): ShotScan.Read = scans.getOrPut(name) { assertIs<ShotScan.Read>(scanner.scan(image(name)), name) }

        fun merged(vararg names: String): Map<String, SpotReading> = mergeShots(names.map(::scan), catalog.shrines.map { it.id })

        fun id(name: String): String = catalog.shrines.firstOrNull { it.name == name }?.id ?: fail("no shrine named $name")
    }

    private val north = "all-complete/1000002225"
    private val south = "all-complete/1000002224"
    private val partialNorth = "partial/1000002226"
    private val partialSouth = "partial/1000002228"
    private val all = listOf(north, south, partialNorth, partialSouth)

    @Test
    fun everyShotRegistersAtMinimumZoom() {
        for (name in all) {
            val reg = Samples.scan(name).registration
            assertTrue(reg.anchors >= 15, "$name: ${reg.anchors} anchors")
            assertTrue(reg.rmsPx < 1.0, "$name: rms ${reg.rmsPx}")
            assertTrue(abs(reg.fittedScale / 0.135 - 1) < 0.002, "$name: fitted scale ${reg.fittedScale}")
            // The halves are a pure vertical pan: same tx, ty 675 px apart.
            assertEquals(959.5, reg.tx, 0.5, name)
        }
        assertEquals(675.0, Samples.scan(north).registration.ty - Samples.scan(south).registration.ty, 1.0)
    }

    @Test
    fun allCompleteSaveIsCompletedExceptTheKnownUnreadableSpots() {
        val merged = Samples.merged(north, south)
        val unsure = merged.filterValues { it.verdict == ScanVerdict.UNSURE }
        assertEquals(setOf("hawa_koth", "akh_vaquot", "ree_dahee", "shee_venath"), unsure.keys)
        assertEquals(132, merged.count { it.value.verdict == ScanVerdict.COMPLETED })
        // The Travel Medallion sits on Hawa Koth and Vah Medoh on Akh Va'quot; the Dueling Peaks pair
        // are 4 px apart at this zoom, so one icon hides the other.
        assertEquals(UnsureReason.COVERED, unsure.getValue("hawa_koth").reason)
        assertEquals(UnsureReason.COVERED, unsure.getValue("akh_vaquot").reason)
        assertEquals(UnsureReason.STACKED, unsure.getValue("ree_dahee").reason)
        assertEquals(UnsureReason.STACKED, unsure.getValue("shee_venath").reason)
    }

    @Test
    fun partialSaveReadsTheUnfinishedShrines() {
        val merged = Samples.merged(partialNorth, partialSouth)
        assertEquals(
            setOf("tena_kosah", "qua_raym", "shae_mosah"),
            merged.filterValues { it.verdict == ScanVerdict.NOT_DONE }.keys,
        )
        // Between them the two halves cover every shrine.
        assertTrue(merged.values.none { it.reason == UnsureReason.NOT_IN_SHOT || it.reason == UnsureReason.SCREEN_EDGE })
        val counts = merged.values.groupingBy { it.verdict }.eachCount()
        assertEquals(mapOf(ScanVerdict.COMPLETED to 41, ScanVerdict.NOT_DONE to 3, ScanVerdict.NOT_PRESENT to 79, ScanVerdict.UNSURE to 13), counts)
        // Trail covers Kihiro Moh's spot in the south shot; the north shot sees it clearly empty.
        assertEquals(ScanVerdict.NOT_PRESENT, merged.getValue("kihiro_moh").verdict)
    }

    @Test
    fun heroesPathModeIsDetectedOnlyWhereItIsOn() {
        assertEquals(listOf(false, false, false, true), all.map { Samples.scan(it).heroesPath })
    }

    @Test
    fun heroesPathTrailNeverReadsAsEmpty() {
        val readings = Samples.scan(partialSouth).readings
        // Trail covers most of these spots' outlines, so they can't be called empty.
        for (name in listOf("Tawa Jinn", "Raqa Zunzo", "Kihiro Moh")) {
            val r = readings.getValue(Samples.id(name))
            assertEquals(ScanVerdict.UNSURE, r.verdict, name)
            assertEquals("Covered by the Hero's Path trail", r.note, name)
        }
        // Qukah Nata has trail through it too, but over half its outline is clear and empty.
        assertEquals(ScanVerdict.NOT_PRESENT, readings.getValue("qukah_nata").verdict)
        // Chest stamps sit on these three.
        for (name in listOf("Chaas Qeta", "Muwo Jeem", "Ka'o Makagh")) {
            assertEquals(UnsureReason.COVERED, readings.getValue(Samples.id(name)).reason, name)
        }
    }

    /**
     * Pastes the real Hero's Path trail from the partial south shot onto the all-complete south
     * shot, taken at the same pan, so trail crosses icons that are known to be there.
     */
    @Test
    fun iconsUnderTheHeroesPathTrailAreStillRead() {
        val trailShot = Samples.image(partialSouth)
        val base = Samples.image(south)
        val trail = MapMasks.of(trailShot)
        val pixels = base.argb.copyOf()
        for (y in ShrineMapScanner.VIEW_Y0 until ShrineMapScanner.VIEW_Y1) {
            for (x in ShrineMapScanner.VIEW_X0 until ShrineMapScanner.VIEW_X1) {
                if (trail.has(x, y, MapMasks.TRAIL)) pixels[y * base.width + x] = trailShot.argb[y * base.width + x]
            }
        }
        val withTrail = assertIs<ShotScan.Read>(Samples.scanner.scan(MapImage(base.width, base.height, pixels)))
        val plain = Samples.scan(south)
        val changed = plain.readings.filter { (id, r) -> withTrail.readings.getValue(id).verdict != r.verdict }
        // Shee Vaneer half-overlaps the Dueling Peaks pair, and trail covers the half that's left.
        assertEquals(setOf("shee_vaneer"), changed.keys, "changed: $changed")
        assertEquals(ScanVerdict.UNSURE, withTrail.readings.getValue("shee_vaneer").verdict)
        // These had trail across 40–70% of their outline.
        for (name in listOf("Tawa Jinn", "Raqa Zunzo", "Kihiro Moh", "Qukah Nata")) {
            assertEquals(ScanVerdict.COMPLETED, withTrail.readings.getValue(Samples.id(name)).verdict, name)
        }
    }

    /**
     * Per shrine and per shot, the port agrees with the prototype except where Hero's Path handling
     * was added: the prototype ignores the trail, so it calls three heavily trailed spots empty.
     */
    @Test
    fun matchesThePrototype() {
        val expectedDifferences = mapOf(
            partialSouth to setOf("Tawa Jinn", "Raqa Zunzo", "Kihiro Moh"),
        )
        val csvs = listOf(
            Triple(north, "all-complete/prototype-results.csv", 0),
            Triple(south, "all-complete/prototype-results.csv", 1),
            Triple(partialNorth, "partial/prototype-results-north.csv", 0),
            Triple(partialSouth, "partial/prototype-results-south.csv", 0),
        )
        for ((shot, csv, column) in csvs) {
            val prototype = prototypeVerdicts(csv, column)
            assertEquals(136, prototype.size)
            val readings = Samples.scan(shot).readings
            val differing = prototype.filter { (name, verdict) -> readings.getValue(Samples.id(name)).verdict != verdict }
            assertEquals(expectedDifferences[shot].orEmpty(), differing.keys, "$shot differs: $differing")
        }
    }

    @Test
    fun rejectsAResizedScreenshot() {
        val img = Samples.image(north)
        val w = 1280
        val h = 720
        val small = IntArray(w * h) { i -> img.argb[(i / w * 3 / 2) * img.width + i % w * 3 / 2] }
        val r = assertIs<ShotScan.Rejected>(Samples.scanner.scan(MapImage(w, h, small)))
        assertEquals(RejectReason.WRONG_SIZE, r.reason)
    }

    @Test
    fun rejectsAScreenshotWithNoIcons() {
        val blank = MapImage(1920, 1080, IntArray(1920 * 1080) { 0xFF15181C.toInt() })
        assertEquals(RejectReason.TOO_FEW_ICONS, assertIs<ShotScan.Rejected>(Samples.scanner.scan(blank)).reason)
    }

    /** Icons keep their size when the map zooms in, so spread them out to twice the spacing. */
    @Test
    fun rejectsAZoomedInMap() {
        val img = Samples.image(north)
        val reg = Samples.scan(north).registration
        val data = runBlocking { ShrineMapRepository.load(Games.BOTW) }
        val pixels = IntArray(img.width * img.height) { 0xFF15181C.toInt() }
        val (cx, cy) = 960 to 523
        for (p in data.shrines.values + data.towers) {
            val u = (reg.scale * p.x + reg.tx).roundToInt()
            val v = (reg.scale * p.z + reg.ty).roundToInt()
            val u2 = cx + 2 * (u - cx)
            val v2 = cy + 2 * (v - cy)
            if (!ShrineMapScanner.inView(u.toDouble(), v.toDouble(), 20) || !ShrineMapScanner.inView(u2.toDouble(), v2.toDouble(), 20)) continue
            for (dy in -20..20) for (dx in -20..20) {
                pixels[(v2 + dy) * img.width + u2 + dx] = img.argb[(v + dy) * img.width + u + dx]
            }
        }
        val r = assertIs<ShotScan.Rejected>(Samples.scanner.scan(MapImage(img.width, img.height, pixels)))
        assertEquals(RejectReason.NOT_MIN_ZOOM, r.reason, r.message)
    }

    @Test
    fun scansQuickly() {
        val img = Samples.image(north)
        Samples.scanner.scan(img) // warm up
        val start = System.nanoTime()
        Samples.scanner.scan(img)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("scan of $north: $ms ms")
        assertTrue(ms < 2_000, "scan took $ms ms")
    }

    /** Shrine name to verdict, from a prototype `results.csv`'s per-shot column. */
    private fun prototypeVerdicts(path: String, shot: Int): Map<String, ScanVerdict> {
        val text = javaClass.getResourceAsStream("/map-samples/$path")?.readBytes()?.decodeToString() ?: fail("missing $path")
        val rows = text.lines().drop(1).filter { it.isNotBlank() }.map(::splitCsv)
        return rows.associate { row ->
            val perShot = row[6].split(" | ")[shot]
            val verdict = when (perShot.substringBefore(" (")) {
                "Completed" -> ScanVerdict.COMPLETED
                "Not Done" -> ScanVerdict.NOT_DONE
                "Not present" -> ScanVerdict.NOT_PRESENT
                "Unsure" -> ScanVerdict.UNSURE
                else -> fail("unknown category in $perShot")
            }
            row[0] to verdict
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        for (c in line) {
            when {
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> out += cur.toString().also { cur.clear() }
                else -> cur.append(c)
            }
        }
        out += cur.toString()
        return out
    }
}
