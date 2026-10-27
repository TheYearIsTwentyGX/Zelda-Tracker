package gx.zelda.map.data

import gx.zelda.core.webpSize
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BotW's pyramid, from `tools/map-tiles/build_tiles.py`. */
class MapTilePyramidTest : TilePyramidTest(MapSpecs.BOTW, budget = 15 * 1024 * 1024)

/** Echoes of Wisdom's pyramid, from `tools/map-tiles/build_eow_tiles.py`. */
class EowMapTilePyramidTest : TilePyramidTest(MapSpecs.EOW, budget = 3 * 1024 * 1024)

/**
 * The tile pyramid a build script wrote for [spec] is complete, matches what the app expects, and
 * stays within its share of the APK, [budget] bytes.
 */
abstract class TilePyramidTest(private val spec: MapSpec, private val budget: Long) {
    private val tilesSpec = spec.tiles

    // Gradle runs tests from the module directory.
    private val dir = File("src/commonMain/composeResources/${tilesSpec.dir}")
    private val manifest = TileManifest.parse(File(dir, "tiles.json").readText())

    @Test
    fun manifestMatchesTheApp() {
        assertEquals(tilesSpec.tileSize, manifest.tileSize)
        assertEquals("webp", manifest.format)
        assertEquals(tilesSpec.fullWidth, manifest.fullWidth)
        assertEquals(tilesSpec.fullHeight, manifest.fullHeight)
        assertEquals(tilesSpec.levels, manifest.levels.size)
        with(manifest.world) {
            assertEquals(spec.world.minX to spec.world.maxX, minX to maxX)
            assertEquals(spec.world.minZ to spec.world.maxZ, minZ to maxZ)
        }
    }

    @Test
    fun eachLevelHalvesTheOneAbove() {
        manifest.levels.forEachIndexed { i, level ->
            val div = 1 shl (tilesSpec.levels - 1 - i)
            assertEquals(i, level.level)
            assertEquals(ceil(tilesSpec.fullWidth / div.toDouble()).toInt(), level.width)
            assertEquals(ceil(tilesSpec.fullHeight / div.toDouble()).toInt(), level.height)
            assertEquals(ceil(level.width / 256.0).toInt(), level.cols)
            assertEquals(ceil(level.height / 256.0).toInt(), level.rows)
        }
    }

    @Test
    fun everyTileIsPresentAndNothingElse() {
        val expected = manifest.levels.flatMap { l ->
            (0 until l.rows).flatMap { row -> (0 until l.cols).map { col -> tilesSpec.path(l.level, row, col) } }
        }.toSet()
        // tiles.json, and EoW's markers.json, sit beside the tiles.
        val actual = dir.walkTopDown().filter { it.isFile && it.extension != "json" }
            .map { "${tilesSpec.dir}/" + it.relativeTo(dir).invariantSeparatorsPath }.toSet()
        assertEquals(emptySet(), expected - actual, "missing tiles")
        assertEquals(emptySet(), actual - expected, "unexpected files")
    }

    @Test
    fun everyTileIsA256PxWebp() {
        for (file in tiles()) {
            assertEquals(256 to 256, webpSize(file.readBytes()), "$file")
        }
    }

    @Test
    fun tilesStayWithinTheSizeBudget() {
        val sizes = tiles().map { it.length() }
        val biggest = sizes.max()
        val total = sizes.sum()
        assertTrue(biggest <= 48 * 1024, "largest tile is $biggest bytes")
        assertTrue(total <= budget, "pyramid is $total bytes")
    }

    @Test
    fun providerServesBundledTilesOnly() = runTest {
        assertNotNull(tilesSpec.provider.getTileStream(0, 0, 0))
        val top = manifest.levels.last()
        assertNotNull(tilesSpec.provider.getTileStream(top.rows - 1, top.cols - 1, top.level))
        assertNull(tilesSpec.provider.getTileStream(top.rows, 0, top.level))
        assertNull(tilesSpec.provider.getTileStream(0, 0, tilesSpec.levels))
    }

    private fun tiles(): List<File> = dir.walkTopDown().filter { it.isFile && it.extension == "webp" }.toList()
}
