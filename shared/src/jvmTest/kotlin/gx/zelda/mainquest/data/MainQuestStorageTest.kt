package gx.zelda.mainquest.data

import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.core.createDataStore
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.mainquest.model.MainQuestProgress
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainQuestStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun onlyBotwHasTheMainQuestTracker() {
        assertTrue(TrackerKind.MAIN_QUEST in Games.BOTW.trackers.map { it.kind })
        assertFalse(TrackerKind.MAIN_QUEST in Games.TOTK.trackers.map { it.kind })
    }

    @Test
    fun saveFilesKeepTheirOwnProgress() = runTest {
        val storage = MainQuestStorage(newDataStore(), Games.BOTW)
        storage.setDone("normal_1", MainQuestSection.TOWERS, "great_plateau", true)
        storage.setDone("normal_1", MainQuestSection.TOWERS, "hateno", true)
        storage.setDone("normal_1", MainQuestSection.DIVINE_BEASTS, "vah_ruta", true)
        storage.setDone("master", MainQuestSection.MEMORIES, "memory_17", true)
        storage.setDone("normal_1", MainQuestSection.TOWERS, "hateno", false)

        assertEquals(
            MainQuestProgress(towers = setOf("great_plateau"), divineBeasts = setOf("vah_ruta")),
            storage.progress("normal_1").first(),
        )
        assertEquals(MainQuestProgress(memories = setOf("memory_17")), storage.progress("master").first())
        assertEquals(MainQuestProgress(), storage.progress("normal_2").first())
    }

    @Test
    fun activeSaveFileIsSharedWithTheOtherTrackers() = runTest {
        val dataStore = newDataStore()
        val mainQuest = MainQuestStorage(dataStore, Games.BOTW)
        val fairies = FairyStorage(dataStore, Games.BOTW)
        assertEquals("normal_1", mainQuest.activeSaveFile.first(), "fresh installs start on Save File 1")

        mainQuest.setActiveSaveFile("master")
        assertEquals("master", fairies.activeSaveFile.first())

        fairies.setActiveSaveFile("normal_2")
        assertEquals("normal_2", mainQuest.activeSaveFile.first())
    }

    @Test
    fun unknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = MainQuestStorage(newDataStore(), Games.BOTW)
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("normal_1", storage.activeSaveFile.first())
    }

    @Test
    fun progressIsStoredUnderTheGamesOwnKeys() = runTest {
        val dataStore = newDataStore()
        MainQuestStorage(dataStore, Games.BOTW).setDone("normal_2", MainQuestSection.TOWERS, "akkala", true)

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("botw_main_quest@normal_2"), keys)
    }
}
