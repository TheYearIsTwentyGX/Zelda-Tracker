package gx.zelda.hearts.data

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.hearts.model.HeartPrefs
import gx.zelda.hearts.model.HeartProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class HeartStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnHearts() = runTest {
        val storage = HeartStorage(newDataStore(), Games.MC)
        storage.setCollected("file_1", "hyrule_town_bell", true)
        storage.setCollected("file_1", "container_deepwood_shrine", true)
        storage.setCollected("file_3", "lake_hylia_south_ledge", true)
        storage.setCollected("file_1", "container_deepwood_shrine", false)

        assertEquals(setOf("hyrule_town_bell"), storage.progress("file_1").first().collected)
        assertEquals(HeartProgress(), storage.progress("file_2").first())
        assertEquals(setOf("lake_hylia_south_ledge"), storage.progress("file_3").first().collected)
    }

    @Test
    fun keysArePrefixedWithTheGameAndFile() = runTest {
        val dataStore = newDataStore()
        HeartStorage(dataStore, Games.MC).setCollected("file_2", "hyrule_town_bell", true)
        HeartStorage(dataStore, Games.MC).updatePrefs { it.copy(hideUncollected = true) }

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("mc_heart_pieces@file_2", "mc_heart_pieces_prefs"), keys)
    }

    @Test
    fun prefsAreSharedByEverySaveFile() = runTest {
        val dataStore = newDataStore()
        HeartStorage(dataStore, Games.MC).updatePrefs { it.copy(hideUncollected = true) }
        assertEquals(HeartPrefs(hideUncollected = true), HeartStorage(dataStore, Games.MC).prefs.first())
    }

    @Test
    fun anUnknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = HeartStorage(newDataStore(), Games.MC)
        storage.setActiveSaveFile("normal_1")
        assertEquals("file_1", storage.activeSaveFile.first())
        storage.setActiveSaveFile("file_3")
        assertEquals("file_3", storage.activeSaveFile.first())
    }
}
