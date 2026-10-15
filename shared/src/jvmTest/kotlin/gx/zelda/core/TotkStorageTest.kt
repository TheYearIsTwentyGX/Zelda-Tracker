package gx.zelda.core

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.model.PieceProgress
import gx.zelda.fairies.data.FairyStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TotK's two save files, and how its trackers sit beside BotW's in the same DataStore. */
class TotkStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun totkHasTwoSaveFiles() {
        assertEquals(listOf("file_1", "file_2"), Games.TOTK.saveFiles.map { it.id })
        assertEquals(listOf(TrackerKind.ARMOR, TrackerKind.FAIRIES, TrackerKind.SHRINES), Games.TOTK.trackers.map { it.kind })
    }

    @Test
    fun saveFilesKeepTheirOwnArmorAndFairies() = runTest {
        val dataStore = newDataStore()
        val armor = ArmorStorage(dataStore, Games.TOTK)
        val fairies = FairyStorage(dataStore, Games.TOTK)

        armor.updatePieces("file_1") { it + ("hylian_hood" to PieceProgress(owned = true, level = 2)) }
        fairies.setUnlocked("file_2", "kaysa", true)

        assertEquals(PieceProgress(owned = true, level = 2), armor.pieces("file_1").first()["hylian_hood"])
        assertTrue(armor.pieces("file_2").first().isEmpty())
        assertTrue(fairies.unlocked("file_1").first().isEmpty())
        assertEquals(setOf("kaysa"), fairies.unlocked("file_2").first())
    }

    @Test
    fun bothTotkTrackersFollowTheSameSaveFile() = runTest {
        val dataStore = newDataStore()
        val armor = ArmorStorage(dataStore, Games.TOTK)
        val fairies = FairyStorage(dataStore, Games.TOTK)
        assertEquals("file_1", armor.activeSaveFile.first(), "fresh installs start on Save File 1")

        fairies.setActiveSaveFile("file_2")
        assertEquals("file_2", armor.activeSaveFile.first())

        armor.setActiveSaveFile("file_1")
        assertEquals("file_1", fairies.activeSaveFile.first())
    }

    @Test
    fun totkProgressIsKeptApartFromBotw() = runTest {
        val dataStore = newDataStore()
        val botw = ArmorStorage(dataStore, Games.BOTW)
        val totk = ArmorStorage(dataStore, Games.TOTK)

        botw.setActiveSaveFile("master")
        totk.setActiveSaveFile("file_2")
        totk.updatePieces("file_2") { it + ("hylian_hood" to PieceProgress(owned = true)) }
        FairyStorage(dataStore, Games.TOTK).setUnlocked("file_2", "mija", true)

        assertEquals("master", botw.activeSaveFile.first())
        Games.BOTW.saveFiles.forEach { assertTrue(botw.pieces(it.id).first().isEmpty()) }
        Games.BOTW.saveFiles.forEach { assertTrue(FairyStorage(dataStore, Games.BOTW).unlocked(it.id).first().isEmpty()) }

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertTrue("totk_armor_pieces@file_2" in keys)
        assertTrue("totk_fairies_unlocked@file_2" in keys)
        assertTrue("totk_active_save_file" in keys)
    }

    @Test
    fun legacyBotwDataIsNotMigratedIntoTotk() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("botw_armor_progress")] = """{"pieces":{"climbers_bandanna":{"owned":true}}}""" }
        val totk = ArmorStorage(dataStore, Games.TOTK)
        totk.migrateLegacy()

        assertTrue(Games.TOTK.saveFiles.all { totk.pieces(it.id).first().isEmpty() })
        assertTrue(stringPreferencesKey("botw_armor_progress") in dataStore.data.first(), "left for BotW to migrate")
    }
}
