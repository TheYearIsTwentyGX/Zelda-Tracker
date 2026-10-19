package gx.zelda.figurines.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.figurines.data.FigurineStorage
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mark all complete and clear all on Carlov's figurines: they change only what's owned, even for
 * figurines the story hasn't unlocked yet, and never the checkpoint, side tasks or shells.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FigurineBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = FigurineStorage(dataStore, Games.MC)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): FigurineViewModel {
        val vm = main.track(FigurineViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun progress(file: String = "file_1") = storage.progress(file).first()

    private val kingPossessed = setOf(5, 22, 33, 34, 35, 53, 65, 100, 101, 114)

    @Test
    fun allCheckpointsMarksEveryFigurineInOneWriteAndLeavesTheRestOfTheFile() = runTest {
        storage.setShells("file_1", 120)
        storage.setRequirementMet("file_1", 10, true)
        storage.setOwned("file_1", 1, true)
        val vm = viewModel()
        vm.state.filterNotNull().first { it.progress.shells == 120 && it.progress.owned.isNotEmpty() }
        val scope = vm.state.value!!.bulkScope()
        assertEquals("All checkpoints", scope.label)
        assertEquals(
            "87 of them aren't available from Carlov yet on this file; they're marked owned anyway. " +
                "The story checkpoint, side tasks and Mysterious Shells stay as they are.",
            scope.note,
        )

        val before = dataStore.writes
        vm.setAllOwned(null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        val after = progress()
        assertEquals(136, after.owned.size)
        assertEquals(120, after.shells, "shells left alone")
        assertEquals(setOf(10), after.requirementsMet)
        assertNull(after.checkpoint)
        assertEquals("Marked 135 figurines complete on File 1", vm.bulkChange.first { it != null }!!.message)

        vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        vm.setAllOwned(null, false)!!.join()
        assertEquals(emptySet(), progress().owned)
        assertEquals(120, progress().shells)
    }

    @Test
    fun aCheckpointCardTakesOnlyItsFigurines() = runTest {
        val vm = viewModel()
        assertEquals("Vaati takes the king", vm.state.value!!.bulkScope("king_possessed").label)
        vm.setAllOwned("king_possessed", true)!!.join()
        assertEquals(kingPossessed, progress().owned)
    }

    @Test
    fun filtersNarrowItToTheFigurinesShown() = runTest {
        val vm = viewModel()
        vm.toggleCategory("bosses")
        vm.state.filterNotNull().first { it.filters.categories.isNotEmpty() }
        vm.setAllOwned(null, true)!!.join()
        assertEquals(17, progress().owned.size)

        vm.toggleCategory("bosses")
        vm.setShow(FigurineShow.TO_WIN)
        val toWin = vm.state.filterNotNull().first { it.filters.categories.isEmpty() && it.filters.show == FigurineShow.TO_WIN }
        val shown = toWin.bulkFigurines()
        assertTrue(shown.all { toWin.isAvailable(it) && !toWin.progress.isOwned(it.number) })
        assertEquals("The story checkpoint, side tasks and Mysterious Shells stay as they are.", toWin.bulkScope().note, "none of them is locked")
        val expected = progress().owned + shown.map { it.number }
        vm.setAllOwned(null, true)!!.join()
        assertEquals(expected, progress().owned)
    }

    @Test
    fun undoPutsBackExactlyWhatWasThereAndKeepsLaterEdits() = runTest {
        storage.setOwned("file_1", 5, true)
        storage.setOwned("file_1", 22, true)
        val vm = viewModel()
        vm.state.filterNotNull().first { it.progress.owned.size == 2 }

        vm.setAllOwned("king_possessed", false)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Cleared 2 figurines on File 1", change.message)
        vm.setOwned(1, true)!!.join()
        vm.setShells(7)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf(1, 5, 22), progress().owned)
        assertEquals(7, progress().shells)
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel()
        vm.setAllOwned("king_possessed", true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.dismissBulkChange(change)
        assertNull(vm.undoBulkChange(change))
        assertEquals(kingPossessed, progress().owned)
    }

    @Test
    fun aHiddenCheckpointAndItsFigurinesAreNeverNamed() = runTest {
        val vm = viewModel()
        vm.setHideUnavailable(true).join()
        val state = vm.state.filterNotNull().first { it.prefs.hideUnavailable }
        val scope = state.bulkScope("king_possessed")
        assertEquals("Later in the story", scope.label)
        assertEquals(10, scope.total, "hidden rows are in the scope")
        val names = state.bulkFigurines("king_possessed").flatMap { listOf(it.name, it.pictured).filter(String::isNotEmpty) } +
            listOf("Vaati takes the king", state.catalog.checkpoints.first { it.id == "king_possessed" }.detail)
        val text = listOf(scope.label, scope.confirmTitle, scope.confirmText("File 1"), scope.contentDescription, scope.actionLabel)
        assertFalse(text.any { line -> names.any { it in line } }, "nothing named in $text")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only file 2 has, so the switch can be waited for by its progress, not just the file.
        storage.setShells("file_2", 3)
        val vm = viewModel()
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.progress.shells == 3 }

        vm.setAllOwned("king_possessed", true)!!.join()
        assertEquals(kingPossessed, progress("file_2").owned)
        assertEquals(emptySet(), progress("file_1").owned)
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 10 figurines complete on File 2", change.message)

        vm.setSaveFile("file_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_1" && it.progress.shells == 0 }
        vm.undoBulkChange(change)!!.join()
        assertEquals(emptySet(), progress("file_2").owned)
        assertEquals(3, progress("file_2").shells)
    }

    /** [FigurineViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: FigurineViewModel) = launch { vm.state.collect {} }
}
