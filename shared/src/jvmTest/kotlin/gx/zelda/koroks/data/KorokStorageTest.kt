package gx.zelda.koroks.data

import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
import gx.zelda.shrines.data.ShrineStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KorokStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnSeeds() = runTest {
        val storage = KorokStorage(newDataStore(), Games.BOTW)
        storage.setCompleted("normal_1", "P01", true)
        storage.setCompleted("normal_1", "P02", true)
        storage.setCompleted("master", "Z07", true)
        storage.setCompleted("normal_1", "P02", false)

        val first = storage.progress("normal_1").first()
        assertEquals(setOf("P01"), first.completed)
        assertEquals(DiscoveryState.FOUND, first.state("P02"), "unticking a seed leaves the Korok found")
        assertEquals(setOf("Z07"), storage.progress("master").first().completed)
        assertEquals(KorokProgress(), storage.progress("normal_2").first())
    }

    @Test
    fun statesMoveBothWays() = runTest {
        val storage = KorokStorage(newDataStore(), Games.BOTW)
        storage.setState("normal_1", "F18", DiscoveryState.FOUND)
        assertEquals(DiscoveryState.FOUND, storage.progress("normal_1").first().state("F18"))
        storage.setState("normal_1", "F18", DiscoveryState.COMPLETED)
        assertEquals(DiscoveryState.COMPLETED, storage.progress("normal_1").first().state("F18"))
        storage.setState("normal_1", "F18", DiscoveryState.UNDISCOVERED)
        assertEquals(KorokProgress(), storage.progress("normal_1").first())
    }

    @Test
    fun hestuPurchasesArePerSaveFileAndNeverNegative() = runTest {
        val storage = KorokStorage(newDataStore(), Games.BOTW)
        storage.setBought("normal_2", "weapon", 3)
        storage.setBought("normal_2", "shield", -2)
        storage.setBought("master", "bow", 1)

        assertEquals(mapOf("weapon" to 3, "shield" to 0), storage.progress("normal_2").first().hestu)
        assertEquals(mapOf("bow" to 1), storage.progress("master").first().hestu)
        assertEquals(emptyMap(), storage.progress("normal_1").first().hestu)
    }

    @Test
    fun prefsAreSharedByEverySaveFileAndSeparateFromShrines() = runTest {
        val dataStore = newDataStore()
        val storage = KorokStorage(dataStore, Games.BOTW)
        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        storage.setActiveSaveFile("master")

        assertEquals(KorokPrefs(hideUndiscovered = true), KorokStorage(dataStore, Games.BOTW).prefs.first())
        assertEquals(false, ShrineStorage(dataStore, Games.BOTW).prefs.first().hideUndiscovered)
    }

    @Test
    fun activeSaveFileIsSharedWithTheOtherTrackers() = runTest {
        val dataStore = newDataStore()
        ShrineStorage(dataStore, Games.BOTW).setActiveSaveFile("normal_2")
        assertEquals("normal_2", KorokStorage(dataStore, Games.BOTW).activeSaveFile.first())

        KorokStorage(dataStore, Games.BOTW).setActiveSaveFile("not_a_file")
        assertTrue(ShrineStorage(dataStore, Games.BOTW).activeSaveFile.first() == "normal_1", "unknown ids fall back to the first file")
    }
}
