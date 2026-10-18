package gx.zelda.map.data

import gx.zelda.bosses.data.BossStorage
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.map.model.MarkerIcon
import gx.zelda.map.model.MarkerKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Bosses map layer over the bundled bosses.json: one marker per spawn, named for the save file's mode. */
class BossLayerTest {
    private val game = Games.BOTW
    private val storage = BossStorage(createDataStore(Files.createTempDirectory("zgx").toString()), game)
    private val layer = BossLayer(storage)

    private suspend fun entries(file: String) = layer.entries(file).first().associateBy { it.id }

    @Test
    fun oneMarkerPerSpawnOnEachFile() = runTest {
        assertEquals(108, entries("normal_1").size)
        // Master Mode adds the Great Plateau's White-Maned Lynel.
        val master = entries("master")
        assertEquals(109, master.size)
        assertEquals("White-Maned Lynel", master.getValue("1288562591").name)
        assertTrue("Master Mode only" in master.getValue("1288562591").subtitle)
        assertEquals(MarkerKind.BOSS, layer.layer.kind)
        assertEquals("Hide unfound bosses", layer.layer.spoilerSetting)
    }

    /** The reported Taluses: Luminous on Normal Mode, Rare on Master Mode, under the same id. */
    @Test
    fun namesFollowTheSaveFilesMode() = runTest {
        val rutimala = "3900382371"
        with(entries("normal_1").getValue(rutimala)) {
            assertEquals("Stone Talus (Luminous)", name)
            assertEquals("Gerudo", region)
            assertEquals("Near Rutimala Hill", subtitle)
            assertEquals(MarkerIcon.TALUS, icon)
        }
        with(entries("master").getValue(rutimala)) {
            assertEquals("Stone Talus (Rare)", name)
            assertEquals("Near Rutimala Hill · Ranked up by Master Mode", subtitle)
        }
    }

    @Test
    fun eachKindHasItsIconAndPosition() = runTest {
        val all = entries("normal_1")
        assertEquals(MarkerIcon.HINOX, all.getValue("1925665429").icon) // Eventide Island
        assertEquals(MarkerIcon.HINOX, all.getValue("73882430").icon) // the Lockup's Stalnox
        assertEquals(MarkerIcon.LYNEL, all.getValue("4200912374").icon) // Coliseum Ruins
        with(all.getValue("1755179653")) { // the Molduking
            assertEquals(MarkerIcon.MOLDUGA, icon)
            assertTrue(dlc)
            assertEquals("Near East Barrens · The Champions' Ballad", subtitle)
        }
        with(all.getValue("1925665429").world) {
            assertEquals(4537.11, x)
            assertEquals(3701.95, z)
        }
    }

    @Test
    fun statesAndSpoilersAreTheBossTrackers() = runTest {
        layer.setState("master", "3900382371", DiscoveryState.COMPLETED)
        assertTrue(storage.progress("master").first().isDefeated("3900382371"))
        assertEquals(DiscoveryState.COMPLETED, entries("master").getValue("3900382371").state)
        assertEquals(DiscoveryState.UNDISCOVERED, entries("normal_1").getValue("3900382371").state)

        assertFalse(layer.hideUndiscovered.first())
        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        assertTrue(layer.hideUndiscovered.first())
    }
}
