package gx.zelda.hearts.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.hearts.data.HeartStorage
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

/** Mark all complete and clear all on The Minish Cap's Heart Pieces: what they write, where, and their undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = HeartStorage(dataStore, Games.MC)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): HeartViewModel {
        val vm = main.track(HeartViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun collected(file: String = "file_1") = storage.progress(file).first().collected

    private val crenel = setOf(
        "mount_crenel_base_northwest_bomb_cave", "mount_crenel_wall_fairy_cave", "mount_crenel_grayblade_dojo",
        "mount_crenel_melari_beanstalk", "mount_crenel_hermit_dig_cave",
    )

    @Test
    fun allRegionsCollectsEverythingInOneWriteThenClears() = runTest {
        storage.setCollected("file_1", "container_deepwood_shrine", true)
        val vm = viewModel()
        assertEquals("All regions", vm.state.value!!.bulkScope().label)

        val before = dataStore.writes
        vm.setAllCollected(null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(50, collected().size)
        assertEquals("Marked 49 hearts complete on File 1", vm.bulkChange.first { it != null }!!.message)

        val all = vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        assertEquals(50, all.bulkScope().changes)
        vm.setAllCollected(null, false)!!.join()
        assertEquals(emptySet(), collected())
    }

    @Test
    fun aRegionCardTakesOnlyItsEntries() = runTest {
        val vm = viewModel()
        vm.setAllCollected("Mount Crenel", true)!!.join()
        assertEquals(crenel, collected())
        assertEquals("Marked 5 Pieces of Heart complete on File 1", vm.bulkChange.first { it != null }!!.message)
    }

    @Test
    fun aFilterNarrowsItToTheEntriesShown() = runTest {
        val vm = viewModel()
        vm.toggleRequirement(HeartRequirement.CONTAINER)
        val containers = vm.state.filterNotNull().first { it.filters.requirements.isNotEmpty() }
        assertEquals("Mark 6 shown complete", containers.bulkScope().actionLabel)
        vm.setAllCollected(null, true)!!.join()
        assertEquals(6, collected().size)
        assertEquals("Marked 6 Heart Containers complete on File 1", vm.bulkChange.first { it != null }!!.message)

        vm.toggleRequirement(HeartRequirement.CONTAINER)
        vm.setQuery("crenel")
        vm.state.filterNotNull().first { it.filters.requirements.isEmpty() && it.filters.query == "crenel" }
        vm.setAllCollected(null, true)!!.join()
        assertEquals(11, collected().size)
    }

    @Test
    fun undoPutsBackExactlyWhatWasThereAndKeepsLaterEdits() = runTest {
        storage.setCollected("file_1", "mount_crenel_grayblade_dojo", true)
        val vm = viewModel()
        vm.state.filterNotNull().first { it.total.pieces == 1 }

        vm.setAllCollected("Mount Crenel", false)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Cleared 1 Piece of Heart on File 1", change.message)
        vm.setCollected("hyrule_town_bell", true)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf("mount_crenel_grayblade_dojo", "hyrule_town_bell"), collected())
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel()
        vm.setAllCollected("Mount Crenel", true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.dismissBulkChange(change)
        assertNull(vm.undoBulkChange(change))
        assertEquals(crenel, collected())
    }

    @Test
    fun hiddenEntriesAreMarkedButNeverNamed() = runTest {
        val vm = viewModel()
        vm.setHideUncollected(true).join()
        val state = vm.state.filterNotNull().first { it.prefs.hideUncollected }
        val names = state.bulkEntries("Mount Crenel").flatMap { listOf(it.name, it.area) }
        val scope = state.bulkScope("Mount Crenel")
        assertEquals(5, scope.total)
        val text = listOf(scope.label, scope.confirmTitle, scope.confirmText("File 1"), scope.contentDescription, scope.actionLabel)
        assertFalse(text.any { line -> names.any { it in line } }, "no entry named in $text")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only file 2 has, so the switch can be waited for by its progress, not just the file.
        val marker = "not_a_heart"
        storage.setCollected("file_2", marker, true)
        val vm = viewModel()
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.progress.isCollected(marker) }

        vm.setAllCollected("Mount Crenel", true)!!.join()
        assertEquals(crenel + marker, collected("file_2"))
        assertEquals(emptySet(), collected("file_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 5 Pieces of Heart complete on File 2", change.message)

        vm.setSaveFile("file_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_1" && !it.progress.isCollected(marker) }
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf(marker), collected("file_2"))
        assertEquals(emptySet(), collected("file_1"))
    }

    /** [HeartViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: HeartViewModel) = launch { vm.state.collect {} }
}
