package gx.zelda.art

import gx.zelda.adventure.data.AdventureRepository
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.map.data.MapTiles
import gx.zelda.map.data.TileManifest
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The asset catalogue against the art bundled with the app (pack 0): every id the app draws has
 * bundled art, every bundled drawable has an id, and the ids are well formed. Art packs are
 * measured against this same catalogue.
 */
class ArtCatalogTest {
    // Gradle runs tests from the module directory.
    private val resources = File("src/commonMain/composeResources")

    private suspend fun allIds(): Map<String, List<String>> = Games.all.associate { it.id to ArtCatalog.ids(it) }.filterValues { it.isNotEmpty() }

    @Test
    fun idsAreWellFormedAndUnique() = runTest {
        for ((game, ids) in allIds()) {
            assertEquals(ids.size, ids.toSet().size, "$game lists an id twice")
            for (id in ids) {
                assertTrue(ArtIds.isValid(id) && ArtIds.game(id) == game, "$game: bad id $id")
            }
        }
    }

    @Test
    fun theBundledArtCoversEveryIcon() = runTest {
        val missing = allIds().values.flatten().filter { ArtIds.kind(it) in ArtIds.ICON_KINDS && GameArt.icon(it) == null }
        assertTrue(missing.isEmpty(), "no bundled art for $missing")
    }

    @Test
    fun everyBundledDrawableHasAnId() = runTest {
        val named = allIds().values.flatten().mapNotNull(ArtIds::bundledName).toSet()
        val drawables = File(resources, "drawable").list()!!.map { it.substringBeforeLast('.') }
        assertEquals(emptyList(), drawables.filter { it !in named }.sorted(), "drawables no asset id reaches")
    }

    @Test
    fun theBundledMapsAreInTheCatalogue() = runTest {
        val botw = ArtCatalog.ids(Games.byId("botw"))
        assertTrue(ArtIds.BOTW_WORLD in botw)
        assertNotNull(GameArt.tile(ArtIds.BOTW_WORLD, 0, 0, 0, MapTiles.path(0, 0, 0)))

        val hwde = ArtCatalog.ids(Games.byId("hwde"))
        val maps = Games.byId("hwde").trackers.filter { it.kind == TrackerKind.ADVENTURE_MAP }.map { AdventureRepository.load(it.dataFile!!) }
        for (map in maps.filter { it.art != null }) {
            assertTrue(ArtIds.hwdeMap(map.id) in hwde, map.id)
            assertNotNull(GameArt.square(ArtIds.hwdeMap(map.id), null, map.art!!.previewPath), map.id)
        }
    }

    @Test
    fun theWorldTilesetMatchesTheBundledPyramid() {
        val manifest = TileManifest.parse(File(resources, "${MapTiles.DIR}/tiles.json").readText())
        val geometry = ArtIds.TILESETS.getValue(ArtIds.BOTW_WORLD)
        assertEquals(manifest.levels.size, geometry.levels)
        for (level in manifest.levels) {
            assertEquals(level.cols, geometry.cols(level.level), "level ${level.level} columns")
            assertEquals(level.rows, geometry.rows(level.level), "level ${level.level} rows")
        }
        val files = geometry.tiles().map { File(resources, "${MapTiles.DIR}/$it.webp") }
        assertEquals(emptyList(), files.filterNot { it.isFile })
    }

    @Test
    fun headersOfTheBundledPictures() {
        val tile = ImageHeader.read(File(resources, "${MapTiles.DIR}/3/0/0.webp").readBytes())
        assertEquals(ImageHeader("webp", 256, 256), tile)
        val square = ImageHeader.read(File(resources, "files/hwde/adventure_map/r1c1.webp").readBytes())
        assertEquals(ImageHeader("webp", 256, 176), square)
        val armor = ImageHeader.read(File(resources, "drawable/armor_hylian_hood.png").readBytes())
        assertEquals("png", armor?.format)
        val jpeg = File("src/jvmTest/resources/map-samples").walk().first { it.name.endsWith(".jpg") }
        val header = assertNotNull(ImageHeader.read(jpeg.readBytes()))
        assertEquals("jpeg", header.format)
        assertTrue(header.width > 100 && header.height > 100, "$header")
    }
}
