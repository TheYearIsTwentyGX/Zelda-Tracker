package gx.zelda.figurines.data

import gx.zelda.core.Games
import gx.zelda.core.activeSaveFileStore
import gx.zelda.core.createDataStore
import gx.zelda.figurines.model.FigurinePrefs
import gx.zelda.figurines.model.FigurineProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FigurineStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnCollections() = runTest {
        val storage = FigurineStorage(newDataStore(), Games.MC)
        storage.setOwned("file_1", 1, true)
        storage.setOwned("file_1", 2, true)
        storage.setOwned("file_3", 136, true)
        storage.setOwned("file_1", 2, false)
        storage.setRequirementMet("file_1", 10, true)
        storage.setCheckpoint("file_1", "fortress_of_winds")

        assertEquals(
            FigurineProgress(owned = setOf(1), requirementsMet = setOf(10), checkpoint = "fortress_of_winds"),
            storage.progress("file_1").first(),
        )
        assertEquals(setOf(136), storage.progress("file_3").first().owned)
        assertEquals(FigurineProgress(), storage.progress("file_2").first())
    }

    @Test
    fun shellsStayWithinWhatLinkCanCarry() = runTest {
        val storage = FigurineStorage(newDataStore(), Games.MC)
        storage.setShells("file_2", 120)
        assertEquals(120, storage.progress("file_2").first().shells)
        storage.setShells("file_2", 5000)
        assertEquals(999, storage.progress("file_2").first().shells)
        storage.setShells("file_2", -3)
        assertEquals(0, storage.progress("file_2").first().shells)
        assertEquals(0, storage.progress("file_1").first().shells, "another save file")
    }

    @Test
    fun prefsAreSharedByEverySaveFile() = runTest {
        val dataStore = newDataStore()
        FigurineStorage(dataStore, Games.MC).updatePrefs { it.copy(hideUnavailable = true) }
        assertEquals(FigurinePrefs(hideUnavailable = true), FigurineStorage(dataStore, Games.MC).prefs.first())
    }

    @Test
    fun activeSaveFileIsTheGamesSharedOne() = runTest {
        val dataStore = newDataStore()
        assertEquals("file_1", FigurineStorage(dataStore, Games.MC).activeSaveFile.first())
        FigurineStorage(dataStore, Games.MC).setActiveSaveFile("file_3")
        assertEquals("file_3", activeSaveFileStore(dataStore, Games.MC).data.first())
        FigurineStorage(dataStore, Games.MC).setActiveSaveFile("no_such_file")
        assertEquals("file_1", activeSaveFileStore(dataStore, Games.MC).data.first(), "an unknown id falls back to the first file")
    }
}
