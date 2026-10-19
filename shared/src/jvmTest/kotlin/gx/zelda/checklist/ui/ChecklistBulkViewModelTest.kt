package gx.zelda.checklist.ui

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.ChecklistTestGame
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mark all complete and clear all on a checklist: what they write, where, and their undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = ChecklistStorage(dataStore, ChecklistTestGame, "test/hearts.json")
    private fun item(id: String) = ChecklistFixture.catalog.item(id)!!

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(catalog: ChecklistCatalog = ChecklistFixture.catalog): ChecklistViewModel {
        val vm = main.track(ChecklistViewModel(storage) { catalog })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun ChecklistViewModel.showSection(id: String?) {
        setSection(id)
        state.filterNotNull().first { it.section?.id == id }
    }

    private suspend fun levels(file: String = "file_1") = storage.progress(file).first().levels

    @Test
    fun aTabMarksAllItsRowsInOneWriteThenClearsThem() = runTest {
        storage.setDone("file_1", item("boss_jabul"), true)
        storage.setDone("file_1", item("field_cave"), true)
        val vm = viewModel()
        vm.showSection("pieces")

        val before = dataStore.writes
        vm.setAllDone(null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        val pieces = listOf("field_cave", "field_race", "zora_cove", "river_zora", "field_tree")
        assertEquals(pieces.associateWith { 1 } + ("boss_jabul" to 1), levels())
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 4 pieces complete on File 1", change.message, "the one already done isn't counted")

        val done = vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        assertEquals(5, done.bulkScope().changes)
        vm.setAllDone(null, false)!!.join()
        assertEquals(mapOf("boss_jabul" to 1), levels(), "another tab's row stays")
        assertEquals("Cleared 5 pieces on File 1", vm.bulkChange.first { it != change }!!.message)
    }

    @Test
    fun aRegionCardMarksOnlyItsRows() = runTest {
        val vm = viewModel()
        vm.setAllDone("Jabul Waters", true)!!.join()
        assertEquals(mapOf("zora_cove" to 1, "river_zora" to 1, "boss_jabul" to 1), levels(), "on All, every section's rows there")

        vm.showSection("pieces")
        vm.setAllDone("Jabul Waters", false)!!.join()
        assertEquals(mapOf("boss_jabul" to 1), levels(), "on a tab, only that tab's rows there")
    }

    @Test
    fun tiersGoToTheirTopAndClearToNone() = runTest {
        storage.setLevel("file_1", item("sword"), 1)
        val vm = viewModel()
        vm.showSection("upgrades")
        vm.setAllDone(null, true)!!.join()
        assertEquals(mapOf("sword" to 3), levels())
        vm.setAllDone(null, false)!!.join()
        assertEquals(emptyMap(), levels())
    }

    @Test
    fun allMarksEveryCountedSectionButNeverASeparateOne() = runTest {
        val vm = viewModel(ChecklistFixture.versions)
        vm.setAllDone(null, true)!!.join()
        assertEquals(mapOf("well" to 1, "maze" to 1), levels())

        vm.showSection("remake_pieces")
        vm.setAllDone(null, true)!!.join()
        assertEquals(5, levels().size)

        vm.showSection(null)
        vm.setAllDone(null, false)!!.join()
        assertEquals(mapOf("remake_well" to 1, "remake_pond" to 1, "remake_tree" to 1), levels(), "clearing All leaves the remake's tab")
    }

    @Test
    fun aFilterNarrowsItToTheRowsShown() = runTest {
        val vm = viewModel()
        vm.setQuery("jabul")
        vm.state.filterNotNull().first { it.filters.query == "jabul" }
        vm.setAllDone(null, true)!!.join()
        assertEquals(mapOf("zora_cove" to 1, "river_zora" to 1, "boss_jabul" to 1), levels())

        vm.setQuery("")
        vm.setMissableOnly(true)
        vm.state.filterNotNull().first { it.filters.query.isEmpty() && it.filters.missableOnly }
        vm.setAllDone(null, true)!!.join()
        assertEquals(4, levels().size)
        assertEquals(1, levels()["field_race"])
    }

    @Test
    fun undoPutsBackExactlyWhatWasThere() = runTest {
        storage.setDone("file_1", item("field_cave"), true)
        storage.setLevel("file_1", item("sword"), 1)
        val vm = viewModel()
        val prior = levels()

        vm.setAllDone(null, true)!!.join()
        assertEquals(7, levels().size)
        val change = vm.bulkChange.first { it != null }!!
        vm.undoBulkChange(change)!!.join()
        assertEquals(prior, levels(), "sword back at 1 of 3, not cleared")
        assertNull(vm.bulkChange.value)
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun undoKeepsLaterEditsOutsideItsScope() = runTest {
        val vm = viewModel()
        vm.showSection("pieces")
        vm.setAllDone(null, true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.setLevel("sword", 2)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(mapOf("sword" to 2), levels())
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel()
        vm.setAllDone("Hyrule Field", true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.dismissBulkChange(change)
        assertNull(vm.bulkChange.value)
        assertNull(vm.undoBulkChange(change))
        assertEquals(3, levels().size)
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only file 2 has, so the switch can be waited for by its progress.
        storage.setDone("file_2", item("boss_jabul"), true)
        val vm = viewModel()
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.progress.isDone(item("boss_jabul")) }

        vm.setAllDone(null, true)!!.join()
        assertEquals(7, levels("file_2").size)
        assertEquals(emptyMap(), levels("file_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 6 hearts complete on File 2", change.message)

        vm.setSaveFile("file_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_1" }
        vm.undoBulkChange(change)!!.join()
        assertEquals(mapOf("boss_jabul" to 1), levels("file_2"))
        assertEquals(emptyMap(), levels("file_1"))
    }

    @Test
    fun nothingToMarkWritesNothing() = runTest {
        val vm = viewModel()
        vm.setQuery("nothing like this")
        vm.state.filterNotNull().first { it.filters.query.isNotEmpty() }
        val before = dataStore.writes
        assertNull(vm.setAllDone(null, true))
        assertEquals(before, dataStore.writes)
    }

    /** [ChecklistViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: ChecklistViewModel) = launch { vm.state.collect {} }
}
