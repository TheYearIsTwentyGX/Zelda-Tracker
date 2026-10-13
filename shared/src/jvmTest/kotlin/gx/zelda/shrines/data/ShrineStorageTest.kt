package gx.zelda.shrines.data

import gx.zelda.armor.data.ArmorStorage
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.shrines.model.ShrineProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShrineStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnShrines() = runTest {
        val storage = ShrineStorage(newDataStore(), Games.BOTW)
        storage.setCompleted("normal_1", "oman_au", true)
        storage.setCompleted("normal_1", "ja_baij", true)
        storage.setCompleted("master", "keh_namut", true)
        storage.setCompleted("normal_1", "ja_baij", false)

        assertEquals(setOf("oman_au"), storage.progress("normal_1").first().completed)
        assertEquals(setOf("keh_namut"), storage.progress("master").first().completed)
        assertEquals(ShrineProgress(), storage.progress("normal_2").first())
    }

    @Test
    fun purchasesAreKeptPerSaveFileAlongsideShrines() = runTest {
        val storage = ShrineStorage(newDataStore(), Games.BOTW)
        storage.setCompleted("normal_2", "oman_au", true)
        storage.setBought("normal_2", hearts = 5, stamina = 2)
        storage.setBought("master", hearts = -1, stamina = 3)

        assertEquals(ShrineProgress(setOf("oman_au"), heartsBought = 5, staminaBought = 2), storage.progress("normal_2").first())
        assertEquals(ShrineProgress(heartsBought = 0, staminaBought = 3), storage.progress("master").first())
        assertEquals(ShrineProgress(), storage.progress("normal_1").first())
    }

    @Test
    fun prefsAreSharedByEverySaveFile() = runTest {
        val dataStore = newDataStore()
        val storage = ShrineStorage(dataStore, Games.BOTW)
        assertTrue(storage.prefs.first().showDlc, "DLC shows by default")

        storage.updatePrefs { it.copy(showDlc = false) }
        storage.setActiveSaveFile("master")
        assertEquals(false, ShrineStorage(dataStore, Games.BOTW).prefs.first().showDlc)
    }

    @Test
    fun activeSaveFileIsSharedWithTheOtherTrackers() = runTest {
        val dataStore = newDataStore()
        val shrines = ShrineStorage(dataStore, Games.BOTW)
        val armor = ArmorStorage(dataStore, Games.BOTW)
        val fairies = FairyStorage(dataStore, Games.BOTW)
        assertEquals("normal_1", shrines.activeSaveFile.first(), "fresh installs start on Save File 1")

        shrines.setActiveSaveFile("master")
        assertEquals("master", armor.activeSaveFile.first())
        assertEquals("master", fairies.activeSaveFile.first())

        fairies.setActiveSaveFile("normal_2")
        assertEquals("normal_2", shrines.activeSaveFile.first())
    }

    @Test
    fun unknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = ShrineStorage(newDataStore(), Games.BOTW)
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("normal_1", storage.activeSaveFile.first())

        storage.setCompleted("does_not_exist", "oman_au", true)
        assertEquals(setOf("oman_au"), storage.progress("normal_1").first().completed)
    }

    @Test
    fun keysArePrefixedWithTheGameId() = runTest {
        val dataStore = newDataStore()
        ShrineStorage(dataStore, Games.BOTW).setCompleted("master", "oman_au", true)
        ShrineStorage(dataStore, Games.BOTW).updatePrefs { it.copy(showDlc = false) }

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("botw_shrines@master", "botw_shrines_prefs"), keys)
    }
}
