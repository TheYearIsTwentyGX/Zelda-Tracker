package gx.zelda.kinstones.data

import gx.zelda.bosses.data.BossStorage
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.kinstones.model.KinstonePrefs
import gx.zelda.kinstones.model.KinstoneProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class KinstoneStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun theThreeSaveFilesKeepTheirOwnFusions() = runTest {
        val storage = KinstoneStorage(newDataStore(), Games.MC)
        storage.setFused("file_1", "mayor_hagen", true)
        storage.setFused("file_1", "smith_1", true)
        storage.setFused("file_3", "tina", true)
        storage.setFused("file_1", "smith_1", false)

        assertEquals(setOf("mayor_hagen"), storage.progress("file_1").first().fused)
        assertEquals(KinstoneProgress(), storage.progress("file_2").first())
        assertEquals(setOf("tina"), storage.progress("file_3").first().fused)
    }

    @Test
    fun fusingTwiceIsTheSameAsOnce() = runTest {
        val storage = KinstoneStorage(newDataStore(), Games.MC)
        storage.setFused("file_2", "gale", true)
        storage.setFused("file_2", "gale", true)
        assertEquals(setOf("gale"), storage.progress("file_2").first().fused)
        storage.setFused("file_2", "gale", false)
        assertEquals(KinstoneProgress(), storage.progress("file_2").first())
    }

    @Test
    fun anUnknownSaveFileFallsBackToTheFirst() = runTest {
        val storage = KinstoneStorage(newDataStore(), Games.MC)
        storage.setFused("no_such_file", "fifi", true)
        assertEquals(setOf("fifi"), storage.progress("file_1").first().fused)
    }

    @Test
    fun prefsAreSharedByEverySaveFile() = runTest {
        val dataStore = newDataStore()
        KinstoneStorage(dataStore, Games.MC).updatePrefs { it.copy(hideResults = true) }
        assertEquals(KinstonePrefs(hideResults = true), KinstoneStorage(dataStore, Games.MC).prefs.first())
    }

    @Test
    fun activeSaveFileIsTheGames() = runTest {
        val dataStore = newDataStore()
        val storage = KinstoneStorage(dataStore, Games.MC)
        assertEquals("file_1", storage.activeSaveFile.first())
        storage.setActiveSaveFile("file_3")
        assertEquals("file_3", KinstoneStorage(dataStore, Games.MC).activeSaveFile.first())
        // Other games keep their own.
        assertEquals("normal_1", BossStorage(dataStore, Games.BOTW).activeSaveFile.first())
    }
}
