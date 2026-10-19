package gx.zelda.koroks.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.HestuCosts
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokData
import gx.zelda.koroks.model.KorokProgress
import gx.zelda.koroks.model.KorokPuzzleType
import gx.zelda.koroks.model.KorokPuzzleTypes
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
import kotlin.test.assertTrue

/** Mark all collected and clear all on the Korok tracker: the whole list, a region, filters and the undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class KorokBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = KorokStorage(dataStore, Games.BOTW)

    private fun korok(id: String, region: String, type: String) =
        Korok(id, 0, "A-1", region, 0.0, 0.0, 0.0, "Place $id", "Rock Lift", puzzleType = type, typeSource = "internal")

    private fun type(id: String, name: String) = KorokPuzzleType(id, name, "Rocks", "", listOf("rule"), emptyList(), emptyList())

    private val data = KorokData(
        KorokCatalog(
            coordinates = "",
            regions = listOf("Great Plateau", "Lake"),
            hestu = HestuCosts(emptyList()),
            koroks = listOf(
                korok("P01", "Great Plateau", "lift_rock"),
                korok("P02", "Great Plateau", "race"),
                korok("L01", "Lake", "lift_rock"),
                korok("L02", "Lake", "race"),
                korok("L03", "Lake", "other"),
            ),
        ),
        KorokPuzzleTypes(listOf("Rocks"), listOf(type("lift_rock", "Lone rock"), type("race", "Race to the ring"), type("other", "Other"))),
    )
    private val all = setOf("P01", "P02", "L01", "L02", "L03")

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): KorokViewModel {
        val vm = main.track(KorokViewModel(storage) { data })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun now(file: String = "normal_1"): KorokProgress = storage.progress(file).first()

    @Test
    fun theListCollectsEverySeedInOneWriteThenClearsThemBackToFound() = runTest {
        storage.setState("normal_1", "P01", DiscoveryState.COMPLETED)
        storage.setBought("normal_1", "weapon", 2)
        val vm = viewModel()

        val before = dataStore.writes
        vm.setAllCompleted(null, completed = true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(all, now().completed)
        assertEquals(all, now().found)
        assertEquals("Marked 4 seeds complete on Save File 1", vm.bulkChange.first { it != null }!!.message)

        val done = vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        assertTrue(done.bulkScope().note!!.contains("stays marked found"))
        vm.setAllCompleted(null, completed = false)!!.join()
        assertEquals(KorokProgress(found = all, hestu = mapOf("weapon" to 2)), now(), "Hestu's upgrades stay")
    }

    @Test
    fun aRegionCardCollectsOnlyItsSeeds() = runTest {
        val vm = viewModel()
        assertEquals("Lake Tower", vm.state.value!!.bulkScope("Lake").label)
        vm.setAllCompleted("Lake", completed = true)!!.join()
        assertEquals(setOf("L01", "L02", "L03"), now().completed)
    }

    @Test
    fun aFilterNarrowsItToTheSeedsShown() = runTest {
        val vm = viewModel()
        vm.toggleType("race")
        vm.state.filterNotNull().first { it.filters.types.isNotEmpty() }
        assertEquals("Mark 2 shown complete", vm.state.value!!.bulkScope().actionLabel)
        vm.setAllCompleted(null, completed = true)!!.join()
        assertEquals(setOf("P02", "L02"), now().completed)

        vm.clearTypes()
        vm.setQuery("place l0")
        vm.state.filterNotNull().first { it.filters.types.isEmpty() && it.filters.query.isNotEmpty() }
        vm.setAllCompleted("Lake", completed = true)!!.join()
        assertEquals(setOf("P02", "L01", "L02", "L03"), now().completed)
    }

    @Test
    fun undoPutsBackExactlyWhatWasThere() = runTest {
        // A seed saved before found existed is collected only; one Korok is just found.
        storage.updateProgress("normal_1") { it.copy(completed = setOf("P01"), found = setOf("L01")) }
        val vm = viewModel()
        val prior = now()

        vm.setAllCompleted(null, completed = true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.setBought("weapon", 1)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(prior.copy(hestu = mapOf("weapon" to 1)), now(), "the seeds back, the later edit kept")
        assertNull(vm.bulkChange.value)
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only Save File 2 has, so the switch can be waited for by its progress.
        storage.setState("normal_2", "L03", DiscoveryState.FOUND)
        val vm = viewModel()
        vm.setSaveFile("normal_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_2" && it.progress.isFound("L03") }

        vm.setAllCompleted("Great Plateau", completed = true)!!.join()
        assertEquals(setOf("P01", "P02"), now("normal_2").completed)
        assertEquals(KorokProgress(), now("normal_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 2 seeds complete on Save File 2", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" }
        vm.undoBulkChange(change)!!.join()
        assertEquals(KorokProgress(found = setOf("L03")), now("normal_2"))
        assertEquals(KorokProgress(), now("normal_1"))
    }

    @Test
    fun nothingToMarkWritesNothing() = runTest {
        val vm = viewModel()
        vm.setQuery("nothing like this")
        vm.state.filterNotNull().first { it.filters.query.isNotEmpty() }
        val before = dataStore.writes
        assertNull(vm.setAllCompleted(null, completed = true))
        assertEquals(before, dataStore.writes)
    }

    /** [KorokViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: KorokViewModel) = launch { vm.state.collect {} }
}
