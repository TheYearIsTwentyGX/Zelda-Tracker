package gx.zelda.kinstones.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.kinstones.data.KinstoneStorage
import gx.zelda.kinstones.model.KinstoneColor
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

/** Mark all complete and clear all on The Minish Cap's Kinstone Fusions: what they write, where, and their undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class KinstoneBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = KinstoneStorage(dataStore, Games.MC)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): KinstoneViewModel {
        val vm = main.track(KinstoneViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun fused(file: String = "file_1") = storage.progress(file).first().fused

    private val minishWoods = setOf(
        "wall_minish_woods", "lily_pad_1", "lily_pad_2", "lily_pad_3", "business_scrub_tree_1", "business_scrub_tree_2",
        "belari", "gentari", "picori_village_lake_1", "picori_village_lake_2",
    )

    @Test
    fun allRegionsFusesEverythingInOneWriteThenClears() = runTest {
        storage.setFused("file_1", "belari", true)
        val vm = viewModel()

        val before = dataStore.writes
        vm.setAllFused(null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(100, fused().size)
        assertEquals("Marked 99 fusions complete on File 1", vm.bulkChange.first { it != null }!!.message)

        vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        vm.setAllFused(null, false)!!.join()
        assertEquals(emptySet(), fused())
        assertEquals("Cleared 100 fusions on File 1", vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!.message)
    }

    @Test
    fun aRegionCardTakesOnlyItsFusions() = runTest {
        val vm = viewModel()
        assertEquals("Minish Woods", vm.state.value!!.bulkScope("Minish Woods").label)
        vm.setAllFused("Minish Woods", true)!!.join()
        assertEquals(minishWoods, fused())
    }

    @Test
    fun filtersNarrowItToTheFusionsShown() = runTest {
        val vm = viewModel()
        vm.toggleColor(KinstoneColor.GOLD)
        vm.state.filterNotNull().first { it.filters.colors.isNotEmpty() }
        vm.setAllFused(null, true)!!.join()
        assertEquals(9, fused().size, "the nine Gold fusions")

        vm.clearFilters()
        vm.setRegion("Minish Woods")
        val woods = vm.state.filterNotNull().first { it.filters.region == "Minish Woods" && it.filters.colors.isEmpty() }
        assertEquals("Minish Woods", woods.bulkScope().label, "the region chip names the scope")
        vm.setAllFused(null, true)!!.join()
        assertEquals(19, fused().size)
    }

    @Test
    fun undoPutsBackExactlyWhatWasThereAndKeepsLaterEdits() = runTest {
        storage.setFused("file_1", "belari", true)
        storage.setFused("file_1", "gentari", true)
        val vm = viewModel()
        vm.state.filterNotNull().first { it.fused == 2 }

        vm.setAllFused("Minish Woods", true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.setFused("mayor_hagen", true)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf("belari", "gentari", "mayor_hagen"), fused())
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel()
        vm.setAllFused("Minish Woods", true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.dismissBulkChange(change)
        assertNull(vm.undoBulkChange(change))
        assertEquals(minishWoods, fused())
    }

    @Test
    fun hiddenResultsAreMarkedButNeverNamed() = runTest {
        val vm = viewModel()
        vm.setHideResults(true).join()
        val state = vm.state.filterNotNull().first { it.prefs.hideResults }
        val results = state.bulkFusions("Minish Woods").map { it.result }
        val scope = state.bulkScope("Minish Woods")
        assertEquals(10, scope.total)
        val text = listOf(scope.label, scope.confirmTitle, scope.confirmText("File 1"), scope.contentDescription, scope.actionLabel)
        assertFalse(text.any { line -> results.any { it in line } }, "no result in $text")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only file 2 has, so the switch can be waited for by its progress, not just the file.
        val marker = "not_a_fusion"
        storage.setFused("file_2", marker, true)
        val vm = viewModel()
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.progress.isFused(marker) }

        vm.setAllFused("Minish Woods", true)!!.join()
        assertEquals(minishWoods + marker, fused("file_2"))
        assertEquals(emptySet(), fused("file_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 10 fusions complete on File 2", change.message)

        vm.setSaveFile("file_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_1" && !it.progress.isFused(marker) }
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf(marker), fused("file_2"))
        assertEquals(emptySet(), fused("file_1"))
    }

    /** [KinstoneViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: KinstoneViewModel) = launch { vm.state.collect {} }
}
