package gx.zelda.adventure

import gx.zelda.adventure.data.AdventureRepository
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.core.webpFormat
import gx.zelda.core.webpSize
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The art and ids tools/hwde/build_maps.py wrote for each HW:DE map: a lossless picture per square
 * at the size the JSON says, a preview, nothing stray, within the size budget, and every id the
 * lock file has ever recorded still there (progress is stored under them). The pictures are only
 * read by their headers: decoding needs Skia, which the JVM tests don't have.
 */
class HwdeAssetsTest {
    // Gradle runs tests from the module directory.
    private val resources = File("src/commonMain/composeResources")
    private val locks = File("../tools/hwde/ids")

    private val allMaps: List<AdventureMap> = Games.all.flatMap { g -> g.trackers.filter { it.kind == TrackerKind.ADVENTURE_MAP } }
        .map { AdventureRepository.parse(File(resources, AdventureRepository.path(it.dataFile!!)).readText(), strict = true) }

    /** The maps with art; the Rewards Map has none and is drawn as plain tiles. */
    private val maps: List<AdventureMap> = allMaps.filter { it.art != null }

    @Test
    fun everySquareHasALosslessPictureOfTheRightSize() {
        assertTrue(maps.isNotEmpty())
        for (map in maps) {
            for (square in map.squares) {
                val art = map.art!!
                val file = File(resources, art.squarePath(square))
                assertTrue(file.isFile, "$file")
                val bytes = file.readBytes()
                assertEquals("VP8L", webpFormat(bytes), "$file is lossless, so the pixels stay sharp")
                assertEquals(art.squareWidth to art.squareHeight, webpSize(bytes), "$file")
            }
        }
    }

    @Test
    fun eachMapHasAPreviewOfTheWholeMap() {
        for (map in maps) {
            val file = File(resources, assertNotNull(map.art!!.previewPath))
            val (w, h) = assertNotNull(webpSize(file.readBytes()), "$file")
            val ratio = map.cols * map.squareWidth.toFloat() / (map.rows * map.squareHeight)
            assertTrue(kotlin.math.abs(w.toFloat() / h - ratio) < 0.01f, "$file is ${w}x$h, the map's shape is $ratio")
        }
    }

    @Test
    fun thereAreNoStrayFiles() {
        for (map in maps) {
            val dir = File(resources, "files/${map.art!!.squares}")
            val expected = map.squares.map { "${it.id}.webp" }.toSet() + "preview.webp"
            assertEquals(expected, dir.listFiles()!!.map { it.name }.toSet(), "$dir")
        }
        for (map in allMaps.filter { it.art == null }) {
            assertTrue(!File(resources, "files/hwde/${map.id}").exists(), "${map.id} has no art, so no folder")
        }
        val drawables = File(resources, "drawable").listFiles()!!.map { it.name }.filter { it.startsWith("hwde_card_") }
        val cards = allMaps.flatMap { m -> m.cards.map { "hwde_card_${it.id}.png" } }.toSet()
        assertEquals(cards, drawables.toSet(), "an icon for each card, and only those")
    }

    @Test
    fun theArtStaysWithinItsBudget() {
        var total = 0L
        for (map in maps) {
            val size = File(resources, "files/${map.art!!.squares}").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            assertTrue(size <= 600_000, "${map.id}'s art is $size bytes")
            total += size
        }
        assertTrue(total <= 3_000_000, "HW:DE's art is $total bytes")
    }

    @Test
    fun noLockedIdHasGone() {
        for (map in allMaps) {
            val lock = File(locks, "${map.id}.txt")
            assertTrue(lock.isFile, "$lock")
            val ids = map.squares.flatMap { s -> listOf(s.id) + s.rewards.map { it.id } + s.skulltulas.map { it.id } + s.searches.map { it.id } }.toSet()
            val missing = lock.readLines().filter { it.isNotBlank() } - ids
            assertEquals(emptyList(), missing, "ids progress is stored under")
        }
    }
}
