package gx.zelda.fairies.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.fairies.data.FairyStorage
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

/** Opening every Great Fairy Fountain at once in BotW and TotK, and its undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class FairyBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val botw = FairyStorage(dataStore, Games.BOTW)
    private val totk = FairyStorage(dataStore, Games.TOTK)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(storage: FairyStorage): FairyViewModel {
        val vm = main.track(FairyViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private val all = setOf("cotera", "kaysa", "mija", "tera")

    @Test
    fun botwOpensEveryFountainInOneWriteAndTheCostsStop() = runTest {
        botw.setUnlocked("normal_1", "mija", true)
        val vm = viewModel(botw)
        val scope = vm.state.value!!.bulkScope
        assertEquals("Great Fairy Fountains", scope.label)
        assertEquals(3, scope.changes)
        assertEquals("Once every fountain is open there are no opening costs left: the 11,500 rupees still to pay drops to nothing.", scope.note)

        val before = dataStore.writes
        vm.setAllUnlocked(true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(all, botw.unlocked("normal_1").first())
        assertEquals("Marked 3 fountains complete on Save File 1", vm.bulkChange.first { it != null }!!.message)
        val open = vm.state.filterNotNull().first { it.unlockedCount == 4 }
        assertNull(open.catalog.nextCost(open.unlockedCount), "nothing left to pay")
        assertEquals(BulkAction.CLEAR, open.bulkScope.action)
        assertEquals("With none open, the next fountain costs 100 rupees again.", open.bulkScope.note)
    }

    @Test
    fun totkFreesEveryFairyAndUndoPutsBackWhatWasThere() = runTest {
        totk.setUnlocked("file_1", "tera", true)
        val vm = viewModel(totk)
        vm.setAllUnlocked(true)!!.join()
        assertEquals(all, totk.unlocked("file_1").first())
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 3 Great Fairies complete on Save File 1", change.message)
        assertNull(vm.state.value!!.bulkScope.note, "no costs in TotK")

        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf("tera"), totk.unlocked("file_1").first())
        assertNull(vm.undoBulkChange(change), "an undo only works once")
        assertEquals(emptySet(), botw.unlocked("normal_1").first(), "BotW keeps its own fountains")
    }

    @Test
    fun aClearClosesEveryFountainAndADismissedSnackbarDropsItsUndo() = runTest {
        botw.setUnlocked("normal_1", "kaysa", true)
        botw.setUnlocked("normal_1", "cotera", true)
        botw.setUnlocked("normal_1", "mija", true)
        botw.setUnlocked("normal_1", "tera", true)
        val vm = viewModel(botw)
        vm.setAllUnlocked(false)!!.join()
        assertEquals(emptySet(), botw.unlocked("normal_1").first())
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Cleared 4 fountains on Save File 1", change.message)
        vm.dismissBulkChange(change)
        assertNull(vm.undoBulkChange(change), "a dismissed snackbar drops the undo")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only the Master Mode file has, so the switch can be waited for by its progress, not just the file.
        val marker = "not_a_fairy"
        botw.setUnlocked("master", marker, true)
        botw.setUnlocked("master", "cotera", true)
        val vm = viewModel(botw)
        vm.setSaveFile("master").join()
        vm.state.filterNotNull().first { it.saveFile.id == "master" && marker in it.unlocked }

        vm.setAllUnlocked(true)!!.join()
        assertEquals(all + marker, botw.unlocked("master").first())
        assertEquals(emptySet(), botw.unlocked("normal_1").first())
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 3 fountains complete on Master Mode", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" && marker !in it.unlocked }
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf(marker, "cotera"), botw.unlocked("master").first())
        assertEquals(emptySet(), botw.unlocked("normal_1").first())
    }

    /** [FairyViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: FairyViewModel) = launch { vm.state.collect {} }
}
