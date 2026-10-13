package gx.zelda.fairies.data

import gx.zelda.armor.data.ArmorStorage
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FairyStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnFountains() = runTest {
        val storage = FairyStorage(newDataStore(), Games.BOTW)
        storage.setUnlocked("normal_1", "cotera", true)
        storage.setUnlocked("normal_1", "kaysa", true)
        storage.setUnlocked("master", "tera", true)
        storage.setUnlocked("normal_1", "kaysa", false)

        assertEquals(setOf("cotera"), storage.unlocked("normal_1").first())
        assertEquals(setOf("tera"), storage.unlocked("master").first())
        assertTrue(storage.unlocked("normal_2").first().isEmpty())
    }

    @Test
    fun activeSaveFileIsSharedWithTheArmorTracker() = runTest {
        val dataStore = newDataStore()
        val fairies = FairyStorage(dataStore, Games.BOTW)
        val armor = ArmorStorage(dataStore, Games.BOTW)
        assertEquals("normal_1", fairies.activeSaveFile.first(), "fresh installs start on Save File 1")

        fairies.setActiveSaveFile("master")
        assertEquals("master", armor.activeSaveFile.first())

        armor.setActiveSaveFile("normal_2")
        assertEquals("normal_2", fairies.activeSaveFile.first())
    }

    @Test
    fun unknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = FairyStorage(newDataStore(), Games.BOTW)
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("normal_1", storage.activeSaveFile.first())
    }
}
