package gx.zelda.figurines.ui

import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.figurines.data.FigurineRepository
import gx.zelda.figurines.data.FigurineStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class FigurineViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun storage() = FigurineStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.MC)

    private val catalog = FigurineRepository.parse(
        """{"categories":[{"id":"characters","name":"Characters","egg":"Blue"},{"id":"bosses","name":"Bosses","egg":"Gold"}],
           "checkpoints":[{"id":"start","name":"Gallery opens","detail":""},{"id":"later","name":"Vaati falls","detail":"","finalSeries":true}],
           "figurines":[
             {"number":1,"name":"Capless Link","category":"characters","checkpoint":"start","description":"A boy.","pictured":"Link"},
             {"number":2,"name":"Marcy","category":"characters","checkpoint":"start","description":"Post office.","requirement":"Fuse Kinstones with the Postman"},
             {"number":3,"name":"Vaati's Wrath","category":"bosses","checkpoint":"later","description":"Evil."}]}""",
    )

    /** A view model on [catalog] whose first state has loaded, so edits have a save file to go to. */
    private suspend fun TestScope.loaded(): Pair<FigurineViewModel, suspend () -> FigurineUiState> {
        val vm = main.track(FigurineViewModel(storage()) { catalog })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.filterNotNull().first()
        return vm to { vm.state.filterNotNull().first() }
    }

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = main.track(FigurineViewModel(storage()) { FigurineRepository.parse("""{"figurines": "not a list"}""") })
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = main.track(FigurineViewModel(storage()) { throw IllegalStateException("files/mc/figurines.json not found") })
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun ownedFigurinesCountForTheActiveFileOnly() = runTest {
        val (vm, state) = loaded()
        assertFalse(vm.loadFailed.value)
        assertEquals(1, state().tally.available)

        vm.setOwned(1, true)!!.join()
        val owned = vm.state.filterNotNull().first { it.tally.owned == 1 && it.saveFiles.first().owned == 1 }
        assertEquals(1, owned.groups.first().owned)

        vm.setSaveFile("file_2").join()
        val other = vm.state.filterNotNull().first { it.saveFile.id == "file_2" }
        assertEquals(0, other.tally.owned)
        assertEquals(listOf(1, 0, 0), other.saveFiles.map { it.owned })
    }

    @Test
    fun checkpointAndSideTasksGrowThePool() = runTest {
        val (vm, _) = loaded()
        vm.setRequirementMet(2, true)!!.join()
        assertEquals(2, vm.state.filterNotNull().first { 2 in it.progress.requirementsMet }.tally.available)
        vm.setCheckpoint("later")!!.join()
        val now = vm.state.filterNotNull().first { it.reached.id == "later" }
        assertEquals(3, now.tally.available)
        vm.setShells(42)!!.join()
        assertEquals(42, vm.state.filterNotNull().first { it.progress.shells == 42 }.progress.shells)
    }

    @Test
    fun showFilters() = runTest {
        val (vm, state) = loaded()
        vm.setOwned(1, true)!!.join()
        vm.state.filterNotNull().first { it.progress.isOwned(1) }
        fun shown(s: FigurineUiState) = s.groups.flatMap { g -> g.figurines.map { it.number } }

        vm.setShow(FigurineShow.TO_WIN)
        assertEquals(emptyList(), shown(state()))
        vm.setShow(FigurineShow.LOCKED)
        assertEquals(listOf(2, 3), shown(state()))
        vm.setShow(FigurineShow.AVAILABLE)
        assertEquals(listOf(1), shown(state()))
        vm.setShow(FigurineShow.ALL)
        vm.toggleCategory("bosses")
        assertEquals(listOf(3), shown(state()))
    }

    @Test
    fun hiddenFigurinesDontLeakThroughSearchOrCategories() = runTest {
        val (vm, state) = loaded()
        vm.setHideUnavailable(true).join()
        val hidden = vm.state.filterNotNull().first { it.prefs.hideUnavailable }
        val wrath = hidden.catalog.figurines.single { it.number == 3 }
        assertTrue(hidden.isHidden(wrath))
        assertTrue(hidden.groups.single { it.checkpoint.id == "later" }.hidden, "a later checkpoint's name is hidden too")
        assertEquals(listOf("start", "later"), hidden.checkpointChoices.map { it.id }, "the next checkpoint can still be picked")

        vm.setQuery("Vaati")
        assertEquals(emptyList(), state().groups)
        vm.setQuery("#003")
        assertEquals(listOf(3), state().groups.flatMap { g -> g.figurines.map { it.number } }, "the number is visible")

        vm.setQuery("")
        vm.toggleCategory("bosses")
        assertEquals(emptyList(), state().groups)

        vm.reveal(3)
        assertEquals(listOf(3), state().groups.flatMap { g -> g.figurines.map { it.number } })
    }
}
