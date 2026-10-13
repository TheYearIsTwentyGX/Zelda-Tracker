package gx.zelda.armor.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.armor.model.ArmorGroupBy
import gx.zelda.armor.model.ArmorOrder
import gx.zelda.armor.model.ArmorPrefs
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.Games
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
        val storage = ArmorStorage(dataStore, Games.BOTW)

        storage.migrateLegacy()

        assertEquals("master", storage.activeSaveFile.first())
        assertEquals(mapOf("climbers_bandanna" to PieceProgress(true, 2)), storage.pieces("master").first())
        assertEquals(emptyMap(), storage.pieces("normal_1").first())
        assertEquals(
            ArmorPrefs(showDlc = true, showAmiibo = false, order = ArmorOrder.IN_GAME, group = ArmorGroupBy.SET),
            storage.prefs.first(),
        )
        assertFalse(legacyKey in dataStore.data.first(), "legacy key should be removed")
    }

    @Test
    fun migrationIsANoOpWithoutLegacyData() = runTest {
        val storage = ArmorStorage(newDataStore(), Games.BOTW)
        storage.migrateLegacy()
        assertEquals("normal_1", storage.activeSaveFile.first(), "fresh installs start on Save File 1")
    }

    @Test
    fun migrationNeverOverwritesExistingMasterData() = runTest {
        val dataStore = newDataStore()
        val storage = ArmorStorage(dataStore, Games.BOTW)
        storage.updatePieces("master") { mapOf("zora_helm" to PieceProgress(true, 1)) }
        dataStore.edit { it[legacyKey] = """{"pieces":{"climbers_bandanna":{"owned":true}}}""" }

        storage.migrateLegacy()

        assertEquals(mapOf("zora_helm" to PieceProgress(true, 1)), storage.pieces("master").first())
    }

    @Test
    fun saveFilesAreIndependentAndPrefsAreShared() = runTest {
        val storage = ArmorStorage(newDataStore(), Games.BOTW)
        storage.updatePieces("normal_1") { it + ("hylian_hood" to PieceProgress(true, 3)) }
        storage.updatePrefs { it.copy(order = ArmorOrder.IN_GAME, group = ArmorGroupBy.NONE) }
        storage.setActiveSaveFile("normal_2")

        assertTrue(storage.pieces("normal_2").first().isEmpty())
        assertEquals(PieceProgress(true, 3), storage.pieces("normal_1").first()["hylian_hood"])
        assertEquals(ArmorGroupBy.NONE, storage.prefs.first().group)
        assertEquals("normal_2", storage.activeSaveFile.first())
    }

    @Test
    fun oldSingleSortSettingConvertsToOrderAndGroup() = runTest {
        val expected = mapOf(
            "ALPHABETICAL" to (ArmorOrder.ALPHABETICAL to ArmorGroupBy.SET),
            "BODY_PART" to (ArmorOrder.IN_GAME to ArmorGroupBy.NONE),
            "SET" to (ArmorOrder.IN_GAME to ArmorGroupBy.SET),
        )
        for ((old, converted) in expected) {
            val dataStore = newDataStore()
            dataStore.edit { it[stringPreferencesKey("botw_armor_prefs")] = """{"showDlc":false,"sort":"$old"}""" }
            val storage = ArmorStorage(dataStore, Games.BOTW)

            val prefs = storage.prefs.first()
            assertEquals(converted, prefs.order to prefs.group, "converting $old")
            assertFalse(prefs.showDlc, "other prefs survive the conversion")

            // The next write stores the converted value and drops the old field.
            storage.updatePrefs { it.copy(showAmiibo = false) }
            val raw = dataStore.data.first()[stringPreferencesKey("botw_armor_prefs")]!!
            assertFalse("\"sort\"" in raw, "old field should be gone after a write: $raw")
            assertEquals(converted, storage.prefs.first().let { it.order to it.group })
        }
    }

    @Test
    fun unknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = ArmorStorage(newDataStore(), Games.BOTW)
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("normal_1", storage.activeSaveFile.first())
    }
}
