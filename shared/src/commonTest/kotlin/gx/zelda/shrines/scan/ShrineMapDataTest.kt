package gx.zelda.shrines.scan

import gx.zelda.core.Games
import gx.zelda.shrines.data.ShrineRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bundled shrine_map.json lines up with shrines.json and holds what the scanner needs. */
class ShrineMapDataTest {
    @Test
    fun everyShrineHasAMapPosition() = runTest {
        val catalog = ShrineRepository.load(Games.BOTW)
        val map = ShrineMapRepository.load(Games.BOTW)
        assertEquals(catalog.shrines.map { it.id }.toSet(), map.shrines.keys)
        assertEquals(15, map.towers.size)
        assertEquals(0.135, map.minZoomScale)
    }

    @Test
    fun everythingFitsOnTheMap() = runTest {
        val map = ShrineMapRepository.load(Games.BOTW)
        // The world runs from -5000 to 5000 east–west and -4000 to 4000 north–south.
        for (p in map.shrines.values + map.towers) {
            assertTrue(p.x in -5000.0..5000.0 && p.z in -4000.0..4000.0, "off the map: $p")
        }
    }

    @Test
    fun templatesAreSquareIconSilhouettes() = runTest {
        val map = ShrineMapRepository.load(Games.BOTW)
        for (t in listOf(map.shrineTemplate, map.towerTemplate)) {
            assertEquals(33, t.size)
            assertTrue(t.values.all { it in 0f..1f })
            assertTrue(t.values.max() == 1f)
        }
    }

    @Test
    fun onlyTheDuelingPeaksPairIsStacked() = runTest {
        val scanner = ShrineMapScanner(ShrineMapRepository.load(Games.BOTW))
        assertEquals(mapOf("ree_dahee" to "shee_venath", "shee_venath" to "ree_dahee"), scanner.stacked)
    }
}
