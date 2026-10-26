package gx.zelda.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Per-file settings: Skyward Sword's Hero Mode flag is kept per save file, survives a reopen, and is one write. */
class SaveFileSettingsTest {
    private val dir = Files.createTempDirectory("zgx").toString()

    @Test
    fun heroModeIsPerFileAndPersists() = runTest {
        val dataStore = CountingDataStore(createDataStore(dir))
        val storage = SaveFileSettingsStorage(dataStore, Games.SS)
        assertFalse(storage.settings("file_2").first().heroMode, "a file starts as Normal")
        val before = dataStore.writes
        storage.update("file_2") { it.copy(heroMode = true) }
        assertEquals(1, dataStore.writes - before)
        assertTrue(storage.settings("file_2").first().heroMode)
        assertFalse(storage.settings("file_1").first().heroMode, "other files keep their own mode")
        assertFalse(storage.settings("file_3").first().heroMode)
        assertEquals("ss_file_settings@file_2", SaveFileSettingsStorage.key("ss", "file_2"))
    }

    @Test
    fun anUnknownFileFallsBackToTheFirst() = runTest {
        val storage = SaveFileSettingsStorage(createDataStore(dir), Games.SS)
        storage.update("file_9") { it.copy(heroMode = true) }
        assertTrue(storage.settings("file_1").first().heroMode)
    }
}
