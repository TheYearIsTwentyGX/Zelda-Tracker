package gx.zelda.checklist.data

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.ChecklistTestGame
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind
import gx.zelda.core.createDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChecklistStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = ChecklistTestGame
    private fun item(id: String) = ChecklistFixture.catalog.item(id)!!

    @Test
    fun saveFilesKeepTheirOwnProgress() = runTest {
        val storage = ChecklistStorage(newDataStore(), game, "test/hearts.json")
        storage.setDone("file_1", item("field_cave"), true)
        storage.setLevel("file_1", item("sword"), 2)
        storage.setDone("file_2", item("boss_jabul"), true)
        storage.setLevel("file_1", item("sword"), 7)

        assertEquals(mapOf("field_cave" to 1, "sword" to 3), storage.progress("file_1").first().levels)
        assertEquals(mapOf("boss_jabul" to 1), storage.progress("file_2").first().levels)
        storage.setDone("file_1", item("sword"), false)
        assertEquals(mapOf("field_cave" to 1), storage.progress("file_1").first().levels)
    }

    @Test
    fun eachChecklistHasItsOwnKeys() = runTest {
        val dataStore = newDataStore()
        ChecklistStorage(dataStore, game, "test/hearts.json").setDone("file_2", item("field_cave"), true)
        ChecklistStorage(dataStore, game, "test/echoes.json").updatePrefs { it.copy(hideUnchecked = true) }

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("test_checklist_hearts@file_2", "test_checklist_echoes_prefs"), keys)
        assertEquals(ChecklistProgress(), ChecklistStorage(dataStore, game, "test/echoes.json").progress("file_2").first())
        assertEquals(ChecklistPrefs(), ChecklistStorage(dataStore, game, "test/hearts.json").prefs.first())
    }

    @Test
    fun prefsAreSharedByEverySaveFileAndTheActiveFileByEveryTracker() = runTest {
        val dataStore = newDataStore()
        ChecklistStorage(dataStore, game, "test/hearts.json").updatePrefs { it.copy(hideUnchecked = true) }
        assertEquals(ChecklistPrefs(hideUnchecked = true), ChecklistStorage(dataStore, game, "test/hearts.json").prefs.first())

        ChecklistStorage(dataStore, game, "test/hearts.json").setActiveSaveFile("file_2")
        assertEquals("file_2", ChecklistStorage(dataStore, game, "test/echoes.json").activeSaveFile.first())
        ChecklistStorage(dataStore, game, "test/echoes.json").setActiveSaveFile("normal_1")
        assertEquals("file_1", ChecklistStorage(dataStore, game, "test/hearts.json").activeSaveFile.first(), "an unknown file falls back to the first")
    }

    @Test
    fun onlyAChecklistHasADataFile() {
        assertFailsWith<IllegalArgumentException> { Tracker(TrackerKind.CHECKLIST, "No file", "") }
        assertFailsWith<IllegalArgumentException> { Tracker(TrackerKind.ARMOR, "Armor", "", dataFile = "x.json") }
    }
}
