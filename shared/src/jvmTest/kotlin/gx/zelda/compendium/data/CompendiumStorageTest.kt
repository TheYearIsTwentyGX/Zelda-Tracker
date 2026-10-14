package gx.zelda.compendium.data

import gx.zelda.armor.data.ArmorStorage
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.fairies.data.FairyStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompendiumStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnPhotographs() = runTest {
        val storage = CompendiumStorage(newDataStore(), Games.BOTW)
        storage.setPhotographed("normal_1", "horse", true)
        storage.setPhotographed("normal_1", "lynel", true)
        storage.setPhotographed("master", "golden_lynel", true)
        storage.setPhotographed("normal_1", "lynel", false)

        assertEquals(setOf("horse"), storage.photographed("normal_1").first())
        assertEquals(setOf("golden_lynel"), storage.photographed("master").first())
        assertTrue(storage.photographed("normal_2").first().isEmpty())
    }

    @Test
    fun activeSaveFileIsSharedWithTheOtherTrackers() = runTest {
        val dataStore = newDataStore()
        val compendium = CompendiumStorage(dataStore, Games.BOTW)
        val armor = ArmorStorage(dataStore, Games.BOTW)
        val fairies = FairyStorage(dataStore, Games.BOTW)
        assertEquals("normal_1", compendium.activeSaveFile.first(), "fresh installs start on Save File 1")

        compendium.setActiveSaveFile("master")
        assertEquals("master", armor.activeSaveFile.first())
        assertEquals("master", fairies.activeSaveFile.first())

        fairies.setActiveSaveFile("normal_2")
        assertEquals("normal_2", compendium.activeSaveFile.first())
    }

    @Test
    fun unknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = CompendiumStorage(newDataStore(), Games.BOTW)
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("normal_1", storage.activeSaveFile.first())
    }

    @Test
    fun photographsAreKeptPerGameAndFile() = runTest {
        val dataStore = newDataStore()
        CompendiumStorage(dataStore, Games.BOTW).setPhotographed("master", "sky_octorok", true)

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("botw_compendium_photographed@master"), keys)
    }
}
