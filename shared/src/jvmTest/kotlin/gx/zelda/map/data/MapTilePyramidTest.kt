package gx.zelda.map.data

import gx.zelda.map.model.MapProjection
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tile pyramid `tools/map-tiles/build_tiles.py` wrote is complete, matches what the app
 * expects, and stays within the APK size budget.
 */
class MapTilePyramidTest {
    // Gradle runs tests from the module directory.
    private val dir = File("src/commonMain/composeResources/${MapTiles.DIR}")
    private val manifest = TileManifest.parse(File(dir, "tiles.json").readText())

    @Test
    fun manifestMatchesTheApp() {
        assertEquals(MapTiles.TILE_SIZE, manifest.tileSize)
        assertEquals("webp", manifest.format)
        assertEquals(MapTiles.FULL_WIDTH, manifest.fullWidth)
        assertEquals(MapTiles.FULL_HEIGHT, manifest.fullHeight)
        assertEquals(MapTiles.LEVELS, manifest.levels.size)
        with(manifest.world) {
            assertEquals(MapProjection.MIN_X to MapProjection.MAX_X, minX to maxX)
            assertEquals(MapProjection.MIN_Z to MapProjection.MAX_Z, minZ to maxZ)
        }
    }

    @Test
    fun eachLevelHalvesTheOneAbove() {
        manifest.levels.forEachIndexed { i, level ->
            val div = 1 shl (MapTiles.LEVELS - 1 - i)
            assertEquals(i, level.level)
            assertEquals(ceil(MapTiles.FULL_WIDTH / div.toDouble()).toInt(), level.width)
            assertEquals(ceil(MapTiles.FULL_HEIGHT / div.toDouble()).toInt(), level.height)
            assertEquals(ceil(level.width / 256.0).toInt(), level.cols)
            assertEquals(ceil(level.height / 256.0).toInt(), level.rows)
        }
    }

    @Test
    fun everyTileIsPresentAndNothingElse() {
        val expected = manifest.levels.flatMap { l ->
            (0 until l.rows).flatMap { row -> (0 until l.cols).map { col -> MapTiles.path(l.level, row, col) } }
        }.toSet()
        val actual = dir.walkTopDown().filter { it.isFile && it.name != "tiles.json" }
            .map { "${MapTiles.DIR}/" + it.relativeTo(dir).invariantSeparatorsPath }.toSet()
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
        assertTrue(total <= 15 * 1024 * 1024, "pyramid is $total bytes")
    }

    @Test
    fun providerServesBundledTilesOnly() = runTest {
        assertNotNull(MapTiles.provider.getTileStream(0, 0, 0))
        val top = manifest.levels.last()
        assertNotNull(MapTiles.provider.getTileStream(top.rows - 1, top.cols - 1, top.level))
        assertNull(MapTiles.provider.getTileStream(top.rows, 0, top.level))
        assertNull(MapTiles.provider.getTileStream(0, 0, MapTiles.LEVELS))
    }

    private fun tiles(): List<File> = dir.walkTopDown().filter { it.isFile && it.extension == "webp" }.toList()

    /** Width and height from a WebP's RIFF header, for the lossy, lossless and extended formats. */
    private fun webpSize(b: ByteArray): Pair<Int, Int>? {
        fun u(i: Int) = b[i].toInt() and 0xff
        if (b.size < 30 || String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WEBP") return null
        return when (String(b, 12, 4)) {
            "VP8 " -> {
                if (u(23) != 0x9d || u(24) != 0x01 || u(25) != 0x2a) return null
                ((u(26) or (u(27) shl 8)) and 0x3fff) to ((u(28) or (u(29) shl 8)) and 0x3fff)
            }
            "VP8L" -> {
                val bits = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24)
                ((bits and 0x3fff) + 1) to (((bits shr 14) and 0x3fff) + 1)
            }
            "VP8X" -> ((u(24) or (u(25) shl 8) or (u(26) shl 16)) + 1) to ((u(27) or (u(28) shl 8) or (u(29) shl 16)) + 1)
            else -> null
        }
    }
}
