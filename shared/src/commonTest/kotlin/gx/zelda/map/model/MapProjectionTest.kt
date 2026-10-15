package gx.zelda.map.model

import gx.zelda.core.Games
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.map.data.MapTiles
import gx.zelda.shrines.scan.ShrineMapRepository
import gx.zelda.shrines.scan.WorldPoint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** World positions land where the bundled map draws them. */
class MapProjectionTest {
    @Test
    fun cornersAndCentre() {
        assertEquals(MapPoint(0.0, 0.0), MapProjection.toMap(WorldPoint(-5000.0, -4000.0)))
        assertEquals(MapPoint(1.0, 1.0), MapProjection.toMap(WorldPoint(5000.0, 4000.0)))
        assertEquals(MapPoint(0.5, 0.5), MapProjection.toMap(WorldPoint(0.0, 0.0)))
        // North-east: east is +x, north is −z, so the top-right corner.
        assertEquals(MapPoint(1.0, 0.0), MapProjection.toMap(WorldPoint(5000.0, -4000.0)))
    }

    @Test
    fun roundTrip() {
        for (p in listOf(WorldPoint(-560.04, 1694.86), WorldPoint(4934.23, -3802.42), WorldPoint(0.0, 0.0))) {
            val back = MapProjection.toWorld(MapProjection.toMap(p))
            assertEquals(p.x, back.x, 1e-9)
            assertEquals(p.z, back.z, 1e-9)
        }
    }

    @Test
    fun greatPlateauTowerIsInItsTopLevelTile() = runTest {
        val tower = ShrineMapRepository.loadTowerPositions(Games.BOTW).getValue("Great Plateau Tower")
        val at = MapProjection.toMap(tower)
        // The top level is one pixel per world unit: (−560, 1695) is pixel (4440, 5695), tile column 17, row 22.
        val col = (at.x * MapTiles.FULL_WIDTH / MapTiles.TILE_SIZE).toInt()
        val row = (at.y * MapTiles.FULL_HEIGHT / MapTiles.TILE_SIZE).toInt()
        assertEquals(17 to 22, col to row)
    }

    @Test
    fun everyShrineTowerAndKorokIsOnTheMap() = runTest {
        val shrineMap = ShrineMapRepository.load(Games.BOTW)
        val koroks = KorokRepository.load(Games.BOTW).koroks.map { WorldPoint(it.x, it.z) }
        val points = shrineMap.shrines.values + shrineMap.towers + koroks
        assertEquals(136 + 15 + 900, points.size)
        for (p in points) {
            val at = MapProjection.toMap(p)
            assertTrue(at.x in 0.0..1.0 && at.y in 0.0..1.0, "off the map: $p")
        }
    }

    @Test
    fun towerPositionsKeepTheirNames() = runTest {
        val named = ShrineMapRepository.loadTowerPositions(Games.BOTW)
        assertEquals(15, named.size)
        assertEquals(ShrineMapRepository.load(Games.BOTW).towers.toSet(), named.values.toSet())
    }
}
