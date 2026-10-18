package gx.zelda.zeldanotes

import gx.zelda.core.Games
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.screencam.live.KorokSpot
import gx.zelda.shrines.scan.MapImage
import gx.zelda.shrines.scan.decodeMapImage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Zelda Notes reader on the user's own captures (`src/jvmTest/resources/zelda-notes/`): three
 * Navigation screenshots and frames of a screen recording, all downscaled from the originals, plus
 * the Categories sheet. Expected verdicts were checked by eye against the screenshots.
 */
class NotesReaderGoldenTest {
    private val spots by lazy { runBlocking { KorokRepository.load(Games.BOTW) }.koroks.map { KorokSpot(it.id, it.x, it.z) } }
    private val template by lazy { runBlocking { DropletTemplate.load(Games.BOTW) } }
    private val reader by lazy { NotesReader(template, spots) }

    private fun image(name: String): MapImage {
        val f = File(javaClass.getResource("/zelda-notes/$name")?.toURI() ?: fail("no $name"))
        return decodeMapImage(f.readBytes()) ?: fail("can't decode $name")
    }

    private fun frames(): List<Pair<String, MapImage>> {
        val dir = File(javaClass.getResource("/zelda-notes/video")?.toURI() ?: fail("no video frames"))
        return dir.listFiles()!!.sortedBy { it.name }.map { it.name to (decodeMapImage(it.readBytes()) ?: fail("can't decode ${it.name}")) }
    }

    private fun byVerdict(read: NotesRead.Placed): Map<NotesVerdict, Set<String>> =
        read.verdicts.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.toSet() }

    @Test
    fun aMidZoomShotIsPlacedAndItsMissingKoroksReadCollected() {
        val read = assertIs<NotesRead.Placed>(reader.read(image("shot-114500.jpg")))
        assertEquals(null, read.note)
        // Tabantha Frontier down to the Gerudo Highlands, where the point-set prototype placed it.
        assertTrue(abs(read.view.x0 - -4479) < 30 && abs(read.view.x1 - -3028) < 30, "view ${read.view}")
        val v = byVerdict(read)
        // Clear map where each of these four would be.
        assertEquals(setOf("G11", "R28", "T26", "T34"), v[NotesVerdict.ABSENT])
        // One droplet drawn for each of these close pairs: which one is missing can't be told.
        assertTrue(v[NotesVerdict.UNSURE].orEmpty().all { it in setOf("G05", "T33") }, "unsure ${v[NotesVerdict.UNSURE]}")
        // Droplets drawn whole, including a pair drawn overlapping.
        assertTrue(v[NotesVerdict.PRESENT]!!.containsAll(listOf("R12", "R20", "R34", "R35", "T24", "T31", "G01", "G04", "G08")))
    }

    @Test
    fun aZoomedOutShotIsPlacedButNotJudged() {
        val read = assertIs<NotesRead.Placed>(reader.read(image("shot-114452.jpg")))
        // 0.27 px per unit on the 1224 px phone, in work px.
        assertTrue(abs(read.pose.scale - 0.27 * template.workFactor) < 0.005, "scale ${read.pose.scale}")
        assertTrue(read.verdicts.isEmpty())
        assertTrue(read.note!!.startsWith("Zoomed out too far"), read.note)
    }

    @Test
    fun aViewWithTooFewDropletsIsRejectedWithAReason() {
        val read = assertIs<NotesRead.Rejected>(reader.read(image("shot-114505.jpg")))
        assertTrue(read.reason.startsWith("Couldn't place this on the map"), read.reason)
    }

    @Test
    fun theCategoriesSheetIsRejected() {
        assertIs<NotesRead.Rejected>(reader.read(image("categories.jpg")))
    }

    @Test
    fun withHideDiscoveredOffNothingIsJudged() {
        val shot = image("shot-114500.jpg")
        // Grey out the switch's teal pill, as the app draws it off.
        val argb = shot.argb.copyOf()
        for (i in argb.indices) {
            val c = argb[i]
            if (((c shr 16) and 0xff) < 40 && ((c shr 8) and 0xff) in 105..155 && (c and 0xff) in 140..190) argb[i] = 0xff808080.toInt()
        }
        val read = assertIs<NotesRead.Placed>(reader.read(MapImage(shot.width, shot.height, argb)))
        assertTrue(read.verdicts.isEmpty())
        assertTrue(read.note!!.startsWith("Turn on Hide Discovered Destinations"), read.note)
    }

    @Test
    fun aRecordingCarriesItsPoseThroughSparseFrames() {
        var pose: NotesPose? = null
        var alone = 0
        var tracked = 0
        val all = frames()
        val evidence = NotesEvidence()
        val seen = HashMap<String, MutableSet<NotesVerdict>>()
        for ((_, frame) in all) {
            if (reader.read(frame) is NotesRead.Placed) alone++
            val read = reader.read(frame, pose)
            if (read is NotesRead.Placed) {
                tracked++
                pose = read.pose
                for ((id, v) in read.verdicts) seen.getOrPut(id) { mutableSetOf() } += v
            }
            evidence.add(read)
        }
        // Frames 231–315 cross the Gerudo Highlands with 4–11 droplets in view. Alone most can't be
        // placed; carried from the frame before, all but the two with almost none can.
        assertTrue(alone <= 15, "alone $alone of ${all.size}")
        assertTrue(tracked >= all.size - 2, "tracked $tracked of ${all.size}")
        // A Korok is never drawn in one frame and missing in another.
        val conflicts = seen.filter { NotesVerdict.PRESENT in it.value && NotesVerdict.ABSENT in it.value }.keys
        assertTrue(conflicts.isEmpty(), "conflicts $conflicts")
        // Checked by eye: clear map at each.
        assertEquals(setOf("E08", "E14", "E21", "H30", "H70", "K28"), evidence.verdicts().filterValues { it == NotesVerdict.ABSENT }.keys)
        // H64's droplet is under a shrine icon and H49's in a tight cluster: never called collected.
        assertEquals(NotesVerdict.UNSURE, evidence.verdicts()["H64"])
        assertEquals(NotesVerdict.UNSURE, evidence.verdicts()["H49"])
        assertTrue(evidence.views.isNotEmpty())
    }

    @Test
    fun theTemplateIsBuiltFromTheReferenceDroplet() {
        assertEquals(DropletTemplate.WORK_WIDTH, template.width)
        assertTrue(template.fillWidth in 70..80, "fill ${template.fillWidth}")
    }
}
