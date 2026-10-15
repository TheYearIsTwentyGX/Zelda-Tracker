package gx.zelda.bosses.data

import gx.zelda.bosses.model.BossPrefs
import gx.zelda.bosses.model.BossProgress
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.koroks.data.KorokStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class BossStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnBosses() = runTest {
        val storage = BossStorage(newDataStore(), Games.BOTW)
        storage.setDefeated("normal_1", "1925665429", true)
        storage.setDefeated("normal_1", "73882430", true)
        storage.setDefeated("master", "1288562591", true)
        storage.setDefeated("normal_1", "73882430", false)

        val first = storage.progress("normal_1").first()
        assertEquals(setOf("1925665429"), first.defeated)
        assertEquals(DiscoveryState.FOUND, first.state("73882430"), "unticking a boss leaves it found")
        assertEquals(setOf("1288562591"), storage.progress("master").first().defeated)
        assertEquals(BossProgress(), storage.progress("normal_2").first())
    }

    @Test
    fun statesMoveBothWays() = runTest {
        val storage = BossStorage(newDataStore(), Games.BOTW)
        storage.setState("normal_2", "3098676163", DiscoveryState.FOUND)
        assertEquals(DiscoveryState.FOUND, storage.progress("normal_2").first().state("3098676163"))
        storage.setState("normal_2", "3098676163", DiscoveryState.COMPLETED)
        assertEquals(DiscoveryState.COMPLETED, storage.progress("normal_2").first().state("3098676163"))
        storage.setState("normal_2", "3098676163", DiscoveryState.UNDISCOVERED)
        assertEquals(BossProgress(), storage.progress("normal_2").first())
    }

    @Test
    fun prefsAreSharedByEverySaveFileAndSeparateFromKoroks() = runTest {
        val dataStore = newDataStore()
        BossStorage(dataStore, Games.BOTW).updatePrefs { it.copy(hideUndiscovered = true) }

        assertEquals(BossPrefs(hideUndiscovered = true), BossStorage(dataStore, Games.BOTW).prefs.first())
        assertEquals(false, KorokStorage(dataStore, Games.BOTW).prefs.first().hideUndiscovered)
    }

    @Test
    fun activeSaveFileIsSharedWithTheOtherTrackers() = runTest {
        val dataStore = newDataStore()
        KorokStorage(dataStore, Games.BOTW).setActiveSaveFile("master")
        assertEquals("master", BossStorage(dataStore, Games.BOTW).activeSaveFile.first())
    }
}
