package gx.zelda.shrines.data

import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** TotK's shrines and Lightroots per save file, beside BotW's shrines in the same DataStore. */
class TotkShrineStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun saveFilesKeepTheirOwnShrinesAndLightroots() = runTest {
        val storage = ShrineStorage(newDataStore(), Games.TOTK)
        storage.setCompleted("file_1", "oromuwak", true)
        storage.setLightrootState("file_1", "kawumoro", DiscoveryState.COMPLETED)
        storage.setLightrootState("file_2", "kawumoro", DiscoveryState.FOUND)
        storage.setBought("file_2", hearts = 4, stamina = 1)

        val one = storage.progress("file_1").first()
        assertEquals(ShrineState.COMPLETED, one.state("oromuwak"))
        assertEquals(DiscoveryState.COMPLETED, one.lightroots.state("kawumoro"))
        assertEquals(0, one.heartsBought)

        val two = storage.progress("file_2").first()
        assertEquals(ShrineState.UNDISCOVERED, two.state("oromuwak"))
        assertEquals(DiscoveryState.FOUND, two.lightroots.state("kawumoro"))
        assertEquals(4, two.heartsBought)
        assertEquals(1, two.staminaBought)
    }

    @Test
    fun aLightrootIsTrackedApartFromItsShrine() = runTest {
        val storage = ShrineStorage(newDataStore(), Games.TOTK)
        storage.setLightrootState("file_1", "kawumoro", DiscoveryState.COMPLETED)
        assertEquals(ShrineState.UNDISCOVERED, storage.progress("file_1").first().state("oromuwak"), "lighting a root doesn't complete its shrine")

        storage.setCompleted("file_1", "oromuwak", true)
        storage.setLightrootState("file_1", "kawumoro", DiscoveryState.UNDISCOVERED)
        val p = storage.progress("file_1").first()
        assertEquals(ShrineState.COMPLETED, p.state("oromuwak"))
        assertEquals(DiscoveryState.UNDISCOVERED, p.lightroots.state("kawumoro"))
        assertEquals(emptySet(), p.lightroots.found)
    }

    @Test
    fun bossHeartsArePerSaveFileAndNeverNegative() = runTest {
        val storage = ShrineStorage(newDataStore(), Games.TOTK)
        storage.setBossHearts("file_1", 3)
        storage.setBossHearts("file_2", -2)
        assertEquals(3, storage.progress("file_1").first().bossHearts)
        assertEquals(0, storage.progress("file_2").first().bossHearts)
    }

    @Test
    fun totkKeysSitBesideBotwsAndBotwStorageIsUnchanged() = runTest {
        val dataStore = newDataStore()
        ShrineStorage(dataStore, Games.BOTW).setCompleted("normal_1", "oman_au", true)
        val totk = ShrineStorage(dataStore, Games.TOTK)
        totk.setLightrootState("file_2", "kawumoro", DiscoveryState.COMPLETED)
        totk.updatePrefs { it.copy(hideUndiscovered = true) }

        val prefs = dataStore.data.first()
        assertEquals(
            setOf("botw_shrines@normal_1", "totk_shrines@file_2", "totk_shrines_prefs"),
            prefs.asMap().keys.map { it.name }.toSet(),
        )
        // BotW's record has no Lightroot or boss-heart fields: defaults aren't written.
        assertEquals("""{"completed":["oman_au"],"found":["oman_au"]}""", prefs[stringPreferencesKey("botw_shrines@normal_1")])
        assertEquals(false, ShrineStorage(dataStore, Games.BOTW).prefs.first().hideUndiscovered, "each game has its own setting")
        assertEquals(ShrineProgress(), ShrineStorage(dataStore, Games.BOTW).progress("normal_2").first())
    }
}
