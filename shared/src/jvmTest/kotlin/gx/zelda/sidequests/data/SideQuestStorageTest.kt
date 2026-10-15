package gx.zelda.sidequests.data

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.sidequests.model.QuestState
import gx.zelda.sidequests.model.SideQuestProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SideQuestStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnQuests() = runTest {
        val storage = SideQuestStorage(newDataStore(), Games.BOTW)
        storage.setCompleted("normal_1", "hylian_homeowner", true)
        storage.setState("normal_1", "the_jewel_trade", QuestState.FOUND)
        storage.setCompleted("master", "the_stolen_heirloom", true)

        val one = storage.progress("normal_1").first()
        assertEquals(QuestState.COMPLETED, one.state("hylian_homeowner"))
        assertEquals(QuestState.FOUND, one.state("the_jewel_trade"))
        assertEquals(QuestState.UNDISCOVERED, one.state("the_stolen_heirloom"))
        assertEquals(setOf("the_stolen_heirloom"), storage.progress("master").first().completed)
        assertEquals(SideQuestProgress(), storage.progress("normal_2").first())
    }

    @Test
    fun uncompletingLeavesInProgressAndNotStartedClearsBoth() = runTest {
        val storage = SideQuestStorage(newDataStore(), Games.BOTW)
        storage.setCompleted("normal_1", "hylian_homeowner", true)
        storage.setCompleted("normal_1", "hylian_homeowner", false)
        assertEquals(QuestState.FOUND, storage.progress("normal_1").first().state("hylian_homeowner"))

        storage.setState("normal_1", "hylian_homeowner", QuestState.COMPLETED)
        storage.setState("normal_1", "hylian_homeowner", QuestState.UNDISCOVERED)
        assertEquals(SideQuestProgress(), storage.progress("normal_1").first())
    }

    @Test
    fun hideUndiscoveredIsOffByDefaultAndSharedByEverySaveFile() = runTest {
        val dataStore = newDataStore()
        val storage = SideQuestStorage(dataStore, Games.BOTW)
        assertFalse(storage.prefs.first().hideUndiscovered)

        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        storage.setActiveSaveFile("master")
        assertTrue(SideQuestStorage(dataStore, Games.BOTW).prefs.first().hideUndiscovered)
    }

    @Test
    fun hidingQuestsIsSeparateFromHidingShrines() = runTest {
        val dataStore = newDataStore()
        SideQuestStorage(dataStore, Games.BOTW).updatePrefs { it.copy(hideUndiscovered = true) }
        assertFalse(ShrineStorage(dataStore, Games.BOTW).prefs.first().hideUndiscovered)
    }

    @Test
    fun completingAShrineQuestLeavesTheShrineAlone() = runTest {
        val dataStore = newDataStore()
        SideQuestStorage(dataStore, Games.BOTW).setCompleted("normal_1", "the_stolen_heirloom", true)
        assertEquals(QuestState.UNDISCOVERED, ShrineStorage(dataStore, Games.BOTW).progress("normal_1").first().state("lakna_rokee"))
    }

    @Test
    fun activeSaveFileIsSharedWithTheOtherTrackers() = runTest {
        val dataStore = newDataStore()
        val quests = SideQuestStorage(dataStore, Games.BOTW)
        val shrines = ShrineStorage(dataStore, Games.BOTW)
        quests.setActiveSaveFile("master")
        assertEquals("master", shrines.activeSaveFile.first())
        shrines.setActiveSaveFile("normal_2")
        assertEquals("normal_2", quests.activeSaveFile.first())
    }

    @Test
    fun keysArePrefixedWithTheGameId() = runTest {
        val dataStore = newDataStore()
        SideQuestStorage(dataStore, Games.BOTW).setCompleted("master", "hylian_homeowner", true)
        SideQuestStorage(dataStore, Games.BOTW).updatePrefs { it.copy(hideUndiscovered = true) }

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("botw_side_quests@master", "botw_side_quests_prefs"), keys)
    }
}
