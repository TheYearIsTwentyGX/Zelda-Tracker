package gx.zelda.mainquest.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.mainquest.model.MainQuestProgress
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Minish Cap's three save files on the shared main quest storage, beside BotW's. */
class McMainQuestStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun theMinishCapHasThreeSaveFiles() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.MC.saveFiles.map { it.id })
    }

    @Test
    fun saveFilesKeepTheirOwnProgress() = runTest {
        val storage = MainQuestStorage(newDataStore(), Games.MC)
        storage.setDone("file_1", MainQuestSection.DUNGEONS, "deepwood_shrine", true)
        storage.setDone("file_1", MainQuestSection.ITEMS, "gust_jar", true)
        storage.setDone("file_1", MainQuestSection.TIGER_SCROLLS, "spin_attack", true)
        storage.setDone("file_3", MainQuestSection.TIGER_SCROLLS, "faster_spin", true)
        storage.setDone("file_1", MainQuestSection.ITEMS, "gust_jar", false)

        assertEquals(
            MainQuestProgress(dungeons = setOf("deepwood_shrine"), tigerScrolls = setOf("spin_attack")),
            storage.progress("file_1").first(),
        )
        assertEquals(MainQuestProgress(), storage.progress("file_2").first())
        assertEquals(MainQuestProgress(tigerScrolls = setOf("faster_spin")), storage.progress("file_3").first())
    }

    @Test
    fun theMinishCapIsKeptApartFromBotw() = runTest {
        val dataStore = newDataStore()
        val mc = MainQuestStorage(dataStore, Games.MC)
        val botw = MainQuestStorage(dataStore, Games.BOTW)
        mc.setActiveSaveFile("file_3")
        mc.setDone("file_3", MainQuestSection.DUNGEONS, "cave_of_flames", true)
        botw.setDone("normal_1", MainQuestSection.TOWERS, "great_plateau", true)

        assertEquals("normal_1", botw.activeSaveFile.first())
        assertEquals("file_3", mc.activeSaveFile.first())
        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("mc_main_quest@file_3", "botw_main_quest@normal_1"), keys.filter { "main_quest" in it }.toSet())
    }

    @Test
    fun botwProgressSavedBeforeTheMinishCapStillReads() = runTest {
        val dataStore = newDataStore()
        // What BotW stored before the Minish Cap fields existed.
        dataStore.edit {
            it[stringPreferencesKey("botw_main_quest@normal_1")] = """{"towers":["hateno"],"divineBeasts":["vah_ruta"],"memories":["memory_1"]}"""
        }
        assertEquals(
            MainQuestProgress(towers = setOf("hateno"), divineBeasts = setOf("vah_ruta"), memories = setOf("memory_1")),
            MainQuestStorage(dataStore, Games.BOTW).progress("normal_1").first(),
        )
    }
}
