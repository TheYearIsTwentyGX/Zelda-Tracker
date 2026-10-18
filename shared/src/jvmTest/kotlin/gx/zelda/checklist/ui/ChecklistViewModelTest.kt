package gx.zelda.checklist.ui

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.ChecklistTestGame
import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun storage() = ChecklistStorage(createDataStore(Files.createTempDirectory("zgx").toString()), ChecklistTestGame, "test/hearts.json")

    private fun viewModel(storage: ChecklistStorage = storage()) = main.track(ChecklistViewModel(storage) { ChecklistFixture.catalog })

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = main.track(ChecklistViewModel(storage()) { ChecklistRepository.parse("""{"sections": "not a list"}""") })
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = main.track(ChecklistViewModel(storage()) { ChecklistRepository.load("test/missing.json") })
        assertTrue(vm.loadFailed.first { it })
    }

    /** Ticks go to the active file, and the picker's line for each file shows its own count. */
    @Test
    fun tickingCountsOnTheActiveFile() = runTest {
        val storage = storage()
        val vm = viewModel(storage)
        backgroundScope.launchCollect(vm)
        val start = vm.state.filterNotNull().first()
        assertFalse(vm.loadFailed.value)
        assertEquals(listOf("file_1", "file_2"), start.saveFiles.map { it.file.id })

        vm.setDone("field_cave", true)!!.join()
        vm.setLevel("sword", 2)!!.join()
        val after = vm.state.filterNotNull().first { it.count.done == 3 }
        assertEquals(listOf(3, 0), after.saveFiles.map { it.count.done })
        assertEquals("Hearts 3 / 5", after.summaries.single().text)

        vm.setSaveFile("file_2").join()
        val second = vm.state.filterNotNull().first { it.saveFile.id == "file_2" }
        assertEquals(0, second.count.done)
        assertEquals(mapOf("field_cave" to 1, "sword" to 2), storage.progress("file_1").first().levels)
        assertNull(vm.setDone("no_such_item", true), "an unknown id writes nothing")
    }

    @Test
    fun hidingUncheckedCoversTheRowsUntilRevealed() = runTest {
        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        // Let the state load before writing: on a fresh DataStore, a collector that first subscribes
        // while an edit from Main (unconfined in tests) is in flight can miss that edit for good.
        val cave = vm.state.filterNotNull().first().catalog.item("field_cave")!!
        vm.setHideUnchecked(true).join()
        assertTrue(vm.state.filterNotNull().first { it.prefs.hideUnchecked }.isHidden(cave))

        vm.reveal(cave.id)
        assertFalse(vm.state.filterNotNull().first { cave.id in it.filters.revealed }.isHidden(cave))
    }

    @Test
    fun filtersResetPerViewModelAndPickASection() = runTest {
        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        vm.setSection("upgrades")
        vm.setIncompleteOnly(true)
        vm.setQuery("sword")
        val s = vm.state.filterNotNull().first { it.filters.query == "sword" }
        assertEquals("upgrades", s.section?.id)
        assertEquals(listOf("sword"), s.groups.flatMap { it.second }.map { it.id })
        vm.setMissableOnly(true)
        assertTrue(vm.state.filterNotNull().first { it.filters.missableOnly }.groups.isEmpty())
    }

    /** [ChecklistViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: ChecklistViewModel) = launch { vm.state.collect {} }
}
