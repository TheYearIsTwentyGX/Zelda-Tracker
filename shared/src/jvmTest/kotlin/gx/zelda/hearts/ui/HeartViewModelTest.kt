package gx.zelda.hearts.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.hearts.data.HeartRepository
import gx.zelda.hearts.data.HeartStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HeartViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = HeartStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.MC)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = HeartViewModel(storage()) { HeartRepository.parse("""{"entries": "not a list"}""") }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = HeartViewModel(storage()) { throw IllegalStateException("files/mc/heart_pieces.json not found") }
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = HeartViewModel(storage()) { HeartRepository.parse("""{"startingHearts":3,"regions":[],"entries":[]}""") }
        assertFalse(vm.loadFailed.value)
    }

    /** Ticks go to the active file, and the picker's line for each file shows its own hearts. */
    @Test
    fun collectingAddsUpOnTheActiveFile() = runTest {
        val storage = storage()
        val vm = HeartViewModel(storage)
        backgroundScope.launchCollect(vm)
        val start = vm.state.filterNotNull().first()
        assertEquals(3, start.total.hearts)
        assertEquals(listOf("file_1", "file_2", "file_3"), start.saveFiles.map { it.file.id })

        val pieces = start.catalog.pieces.take(5).map { it.id }
        pieces.forEach { vm.setCollected(it, true) }
        vm.setCollected("container_deepwood_shrine", true)
        val after = vm.state.filterNotNull().first { it.progress.collected.size == 6 }
        assertEquals(5, after.total.hearts)
        assertEquals(1, after.total.quarters)
        assertEquals("5 hearts + 1/4", after.saveFiles.first { it.file.id == "file_1" }.total.heartsLabel)
        assertEquals("3 hearts", after.saveFiles.first { it.file.id == "file_2" }.total.heartsLabel)

        vm.setSaveFile("file_2")
        val second = vm.state.filterNotNull().first { it.saveFile.id == "file_2" }
        assertTrue(second.progress.collected.isEmpty())
        assertEquals(setOf("container_deepwood_shrine") + pieces, storage.progress("file_1").first().collected)
    }

    @Test
    fun hidingUncollectedCoversTheRowsUntilRevealed() = runTest {
        val vm = HeartViewModel(storage())
        backgroundScope.launchCollect(vm)
        vm.setHideUncollected(true)
        val hidden = vm.state.filterNotNull().first { it.prefs.hideUncollected }
        val bell = hidden.catalog.entries.first { it.id == "hyrule_town_bell" }
        assertTrue(hidden.isHidden(bell))

        vm.reveal(bell.id)
        assertFalse(vm.state.filterNotNull().first { bell.id in it.filters.revealed }.isHidden(bell))
    }

    /** [HeartViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: HeartViewModel) = launch { vm.state.collect {} }
}
