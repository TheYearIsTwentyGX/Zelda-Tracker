package gx.zelda.botw.armor.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.botw.armor.model.ArmorPrefs
import gx.zelda.botw.armor.model.ArmorSort
import gx.zelda.botw.armor.model.PieceProgress
import gx.zelda.core.createDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmorStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    private val legacyKey = stringPreferencesKey("botw_armor_progress")

    @Test
    fun legacyDataBecomesMasterModeAndIsActive() = runTest {
        val dataStore = newDataStore()
        dataStore.edit {
            it[legacyKey] = """{"pieces":{"climbers_bandanna":{"owned":true,"level":2}},"showAmiibo":false,"sort":"SET"}"""
        }
        val storage = ArmorStorage(dataStore)

        storage.migrateLegacy()

        assertEquals("master", storage.activeSaveFile.first())
        assertEquals(mapOf("climbers_bandanna" to PieceProgress(true, 2)), storage.pieces("master").first())
        assertEquals(emptyMap(), storage.pieces("normal_1").first())
        assertEquals(ArmorPrefs(showDlc = true, showAmiibo = false, sort = ArmorSort.SET), storage.prefs.first())
        assertFalse(legacyKey in dataStore.data.first(), "legacy key should be removed")
    }

    @Test
    fun migrationIsANoOpWithoutLegacyData() = runTest {
        val storage = ArmorStorage(newDataStore())
        storage.migrateLegacy()
        assertEquals("normal_1", storage.activeSaveFile.first(), "fresh installs start on Save File 1")
    }

    @Test
    fun migrationNeverOverwritesExistingMasterData() = runTest {
        val dataStore = newDataStore()
        val storage = ArmorStorage(dataStore)
        storage.updatePieces("master") { mapOf("zora_helm" to PieceProgress(true, 1)) }
        dataStore.edit { it[legacyKey] = """{"pieces":{"climbers_bandanna":{"owned":true}}}""" }

        storage.migrateLegacy()

        assertEquals(mapOf("zora_helm" to PieceProgress(true, 1)), storage.pieces("master").first())
    }

    @Test
    fun saveFilesAreIndependentAndPrefsAreShared() = runTest {
        val storage = ArmorStorage(newDataStore())
        storage.updatePieces("normal_1") { it + ("hylian_hood" to PieceProgress(true, 3)) }
        storage.updatePrefs { it.copy(sort = ArmorSort.BODY_PART) }
        storage.setActiveSaveFile("normal_2")

        assertTrue(storage.pieces("normal_2").first().isEmpty())
        assertEquals(PieceProgress(true, 3), storage.pieces("normal_1").first()["hylian_hood"])
        assertEquals(ArmorSort.BODY_PART, storage.prefs.first().sort)
        assertEquals("normal_2", storage.activeSaveFile.first())
    }

    @Test
    fun unknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = ArmorStorage(newDataStore())
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("normal_1", storage.activeSaveFile.first())
    }
}
