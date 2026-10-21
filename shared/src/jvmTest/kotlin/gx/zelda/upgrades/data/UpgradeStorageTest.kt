package gx.zelda.upgrades.data

import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradeStorageTest {
    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = UpgradeStorage(dataStore, Games.SS)

    @Test
    fun saveFilesAreIndependentAndPrefsAreShared() = runTest {
        storage.update("file_1") { it.withCopy("beetle") { p -> p.copy(owned = true, level = 2) } }
        storage.updatePrefs { it.copy(hiddenSections = setOf("potions"), materialSort = MaterialSort.IN_GAME) }
        storage.setActiveSaveFile("file_2")

        assertEquals(UpgradeProgress(), storage.progress("file_2").first())
        assertEquals(PieceProgress(true, 2), storage.progress("file_1").first().of("beetle"))
        assertEquals(setOf("potions"), storage.prefs.first().hiddenSections)
        assertEquals("file_2", storage.activeSaveFile.first())
    }

    /** A copy's state and the copy counts live together, so each change is one write under one key. */
    @Test
    fun eachChangeIsOneWrite() = runTest {
        var before = dataStore.writes
        storage.update("file_1") { it.copy(copies = mapOf("quiver" to 2), items = mapOf("quiver#2" to PieceProgress(true, 1))) }
        assertEquals(1, dataStore.writes - before)
        val raw = dataStore.data.first()[stringPreferencesKey("ss_upgrades@file_1")]!!
        assertTrue("quiver#2" in raw && "copies" in raw, raw)

        before = dataStore.writes
        val snapshot = storage.setAllOwned("file_1", listOf("beetle", "quiver", "quiver#2"), owned = true)
        assertEquals(1, dataStore.writes - before)
        assertEquals(mapOf("beetle" to null, "quiver" to null, "quiver#2" to PieceProgress(true, 1)), snapshot.before)
        val marked = storage.progress("file_1").first()
        assertEquals(PieceProgress(true, 1), marked.of("quiver#2"), "levels stay")
        assertTrue(marked.of("beetle").owned)

        before = dataStore.writes
        storage.restore("file_1", snapshot)
        assertEquals(1, dataStore.writes - before)
        val restored = storage.progress("file_1").first()
        assertEquals(mapOf("quiver#2" to PieceProgress(true, 1)), restored.items)
        assertEquals(mapOf("quiver" to 2), restored.copies, "the undo leaves the copy count alone")
    }

    @Test
    fun anUnknownSaveFileFallsBackToTheFirst() = runTest {
        storage.setActiveSaveFile("does_not_exist")
        assertEquals("file_1", storage.activeSaveFile.first())
    }
}
