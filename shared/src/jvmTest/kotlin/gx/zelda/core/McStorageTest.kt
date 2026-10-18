package gx.zelda.core

import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.model.PieceProgress
import gx.zelda.hearts.data.HeartStorage
import gx.zelda.overview.overviewProviders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Minish Cap's three save files, and how its trackers sit beside BotW's and TotK's in the same DataStore. */
class McStorageTest {
    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun mcIsListedBeforeBotwWithThreeSaveFiles() {
        // By release date: 2004, 2017, 2023.
        assertEquals(listOf("mc", "botw", "totk"), Games.all.filterNot { it.comingSoon }.map { it.id })
        assertEquals(Games.MC, Games.byId("mc"))
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.MC.saveFiles.map { it.id })
        assertEquals(listOf("File 1", "File 2", "File 3"), Games.MC.saveFiles.map { it.label })
        assertTrue(Games.MC.saveFiles.none { it.masterMode })
        assertTrue(TrackerKind.HEART_PIECES in Games.MC.trackers.map { it.kind })
    }

    /** MC has none of BotW's trackers, so nothing BotW-only (Main Quest, Spirit Orbs) is built for it. */
    @Test
    fun overviewRowsAreOnlyMcsOwn() {
        val providers = overviewProviders(newDataStore(), Games.MC)
        assertEquals(Games.MC.trackers.map { it.kind }.filter { it != TrackerKind.OVERVIEW }, providers.map { it.kind })
    }

    @Test
    fun theFirstFileIsActiveOnAFreshInstall() = runTest {
        assertEquals("file_1", HeartStorage(newDataStore(), Games.MC).activeSaveFile.first())
    }

    @Test
    fun mcProgressIsKeptApartFromTheOtherGames() = runTest {
        val dataStore = newDataStore()
        val totk = ArmorStorage(dataStore, Games.TOTK)
        totk.setActiveSaveFile("file_2")
        totk.updatePieces("file_1") { it + ("hylian_hood" to PieceProgress(owned = true)) }

        val hearts = HeartStorage(dataStore, Games.MC)
        hearts.setActiveSaveFile("file_3")
        hearts.setCollected("file_1", "hyrule_town_bell", true)

        // Both games have a "file_1"; neither sees the other's.
        assertEquals("file_2", totk.activeSaveFile.first())
        assertEquals("file_3", hearts.activeSaveFile.first())
        assertEquals(setOf("hyrule_town_bell"), hearts.progress("file_1").first().collected)
        assertTrue(HeartStorage(dataStore, Games.TOTK).progress("file_1").first().collected.isEmpty())

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertTrue("mc_heart_pieces@file_1" in keys)
        assertTrue("mc_active_save_file" in keys)
        assertTrue("totk_active_save_file" in keys)
    }
}
