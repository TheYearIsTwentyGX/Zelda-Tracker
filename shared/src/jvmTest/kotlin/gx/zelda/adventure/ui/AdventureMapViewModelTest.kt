package gx.zelda.adventure.ui

import gx.zelda.adventure.AdventureFixture
import gx.zelda.adventure.data.AdventureStorage
import gx.zelda.adventure.model.AdventureTab
import gx.zelda.adventure.model.SquareFilter
import gx.zelda.adventure.model.SquareStatus
import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Game
import gx.zelda.core.ReleaseDate
import gx.zelda.core.SaveFile
import gx.zelda.core.TestMain
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An Adventure Mode map's screen state: ticks per save file, filters, cards, spoilers and mark-all. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdventureMapViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    /** Two files, so a save-file switch can be tested; HW:DE itself has one. */
    private val game = Game(
        id = "hwtest",
        title = "Test Warriors",
        released = ReleaseDate(2030, 1, 1),
        platform = "Test",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(Tracker(TrackerKind.ADVENTURE_MAP, "Test Map", "", dataFile = "hwtest/test_map.json")),
        spinOff = true,
    )
    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = AdventureStorage(dataStore, game, "hwtest/test_map.json")
    private fun square(id: String) = AdventureFixture.square(id)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): AdventureMapViewModel {
        val vm = main.track(AdventureMapViewModel(storage) { AdventureFixture.map })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun AdventureMapViewModel.ready(check: (AdventureUiState) -> Boolean) = state.filterNotNull().first(check)

    private suspend fun progress(file: String = "file_1") = storage.progress(file).first()

    @Test
    fun theStemComesFromTheDataFile() {
        assertEquals("test_map", storage.stem)
    }

    @Test
    fun statusAndTicksAreSavedOnTheFileOnScreen() = runTest {
        val vm = viewModel()
        vm.setStatus("r1c1", SquareStatus.A_RANK)!!.join()
        vm.setDone("r1c1.hc_link", true)!!.join()
        assertEquals(mapOf("r1c1" to 2), progress().status)
        assertEquals(setOf("r1c1.hc_link"), progress().done)
        assertEquals(emptyMap(), progress("file_2").status)
        vm.setStatus("r1c1", SquareStatus.CLEARED)!!.join()
        assertEquals(mapOf("r1c1" to 1), progress().status, "back down from A-rank to cleared")
        assertNull(vm.setDone("r9c9.nothing", true), "an id the map doesn't have")
        assertNull(vm.setStatus("r2c3", SquareStatus.CLEARED), "a blank square")
    }

    @Test
    fun aSwitchShowsAndWritesTheOtherFile() = runTest {
        storage.setDone("file_2", "r1c2.weapon_spinner_1", true)
        val vm = viewModel()
        vm.setSaveFile("file_2").join()
        // Wait for file 2's own progress, not just its id, before writing (#54).
        vm.ready { it.saveFile.id == "file_2" && it.progress.isDone("r1c2.weapon_spinner_1") }
        vm.setStatus("r2c1", SquareStatus.CLEARED)!!.join()
        assertEquals(mapOf("r2c1" to 1), progress("file_2").status)
        assertEquals(emptyMap(), progress("file_1").status)
        val state = vm.ready { it.progress.isCleared(square("r2c1")) }
        assertEquals(listOf(0, 2), state.saveFiles.map { it.all.done })
    }

    @Test
    fun chipsAndTheWarriorChoiceNarrowTheSquares() = runTest {
        val vm = viewModel()
        vm.setFilter(SquareFilter.HEART_CONTAINERS, true).join()
        var state = vm.ready { SquareFilter.HEART_CONTAINERS in it.mapPrefs.filters }
        assertEquals(listOf("r1c1", "r1c3"), state.matching.map { it.id })
        assertTrue(state.filtered)
        vm.setWarrior("zelda").join()
        state = vm.ready { it.mapPrefs.warrior == "zelda" }
        assertEquals(listOf("r1c3"), state.matching.map { it.id }, "Heart Containers left, for Zelda")
        vm.clearFilters().join()
        state = vm.ready { !it.filtered }
        assertEquals(5, state.matching.size)
        assertTrue(state.look(square("r1c2"), selected = false).let { !it.dimmed })

        vm.setFilter(SquareFilter.NOT_A_RANKED, true).join()
        vm.setStatus("r1c1", SquareStatus.A_RANK)!!.join()
        state = vm.ready { it.progress.isARanked(square("r1c1")) && SquareFilter.NOT_A_RANKED in it.mapPrefs.filters }
        assertFalse(square("r1c1") in state.matching)
        assertTrue(state.look(square("r1c1"), selected = false).dimmed)
    }

    @Test
    fun searchableNowFollowsTheCardsInHand() = runTest {
        val vm = viewModel()
        vm.setFilter(SquareFilter.SEARCHABLE, true).join()
        var state = vm.ready { SquareFilter.SEARCHABLE in it.mapPrefs.filters }
        assertEquals(emptyList(), state.matching, "no cards yet")
        vm.setStatus("r1c1", SquareStatus.CLEARED)!!.join() // gives the Bomb card
        state = vm.ready { it.inventory.has("bomb") }
        assertEquals(listOf("r1c2", "r1c3"), state.matching.map { it.id })
        vm.setCard("bomb", false)!!.join()
        state = vm.ready { !it.inventory.has("bomb") }
        assertEquals(emptyList(), state.matching)
        vm.setCard("hookshot", true)!!.join()
        state = vm.ready { it.inventory.has("hookshot") }
        assertEquals(listOf("r1c1"), state.matching.map { it.id })
        vm.setCard("hookshot", null)!!.join()
        assertTrue(vm.ready { it.inventory.isAutomatic("hookshot") }.inventory.isAutomatic("bomb").not())
    }

    @Test
    fun spoilerSafeModeHidesUnreachedSquaresAndSecrets() = runTest {
        val vm = viewModel()
        vm.setSpoilerSafe(true).join()
        var state = vm.ready { it.prefs.spoilerSafe }
        assertEquals(listOf("r1c1"), AdventureFixture.map.squares.filterNot(state.spoilers::isHidden).map { it.id })
        // A search can't find what's hidden, except by the square's colour.
        vm.setQuery("Skyloft")
        state = vm.ready { it.session.query == "Skyloft" }
        assertEquals(emptyList(), state.matching)
        vm.setQuery("Red")
        state = vm.ready { it.session.query == "Red" }
        assertEquals(listOf("r1c3", "r2c1", "r2c2"), state.matching.map { it.id })
        vm.setQuery("")
        // Content chips don't match hidden squares either.
        vm.setFilter(SquareFilter.HEART_CONTAINERS, true).join()
        state = vm.ready { SquareFilter.HEART_CONTAINERS in it.mapPrefs.filters && it.session.query.isEmpty() }
        assertEquals(listOf("r1c1"), state.matching.map { it.id })

        vm.reveal("r2c1")
        vm.useCompass("r1c1")
        state = vm.ready { "r2c1" in it.session.revealed && "r1c1" in it.session.compassed }
        assertFalse(state.spoilers.isHidden(square("r2c1")))
        assertFalse(state.spoilers.secretsHidden(square("r1c1")))
        vm.setSpoilerSafe(false).join()
        state = vm.ready { !it.prefs.spoilerSafe }
        assertTrue(AdventureFixture.map.squares.none(state.spoilers::isHidden))
    }

    @Test
    fun challengeableNowFollowsClearsAndWalls() = runTest {
        val vm = viewModel()
        vm.setFilter(SquareFilter.CHALLENGEABLE, true).join()
        var state = vm.ready { SquareFilter.CHALLENGEABLE in it.mapPrefs.filters }
        assertEquals(listOf("r1c1"), state.matching.map { it.id }, "only the start")
        vm.setStatus("r1c1", SquareStatus.CLEARED)!!.join()
        state = vm.ready { it.progress.isCleared(square("r1c1")) }
        assertEquals(listOf("r1c1", "r2c1"), state.matching.map { it.id }, "the wall keeps r1c2 out")
        assertFalse(state.look(square("r1c2"), selected = false).challengeable)
        vm.setDone("r1c1.search_hookshot", true)!!.join()
        state = vm.ready { it.progress.isDone("r1c1.search_hookshot") }
        assertEquals(listOf("r1c1", "r1c2", "r2c1"), state.matching.map { it.id })
        assertEquals(gx.zelda.adventure.model.EdgeStyle.OPENED, state.edgeStyles["r1c1" to gx.zelda.adventure.model.Side.EAST])
    }

    @Test
    fun notClearedPutsTheChallengeableSquaresFirst() = runTest {
        storage.setStatus("file_1", square("r1c1"), SquareStatus.CLEARED)
        val vm = viewModel()
        vm.setFilter(SquareFilter.NOT_CLEARED, true).join()
        val state = vm.ready { SquareFilter.NOT_CLEARED in it.mapPrefs.filters && it.progress.isCleared(square("r1c1")) }
        assertEquals(listOf("r2c1", "r1c2", "r1c3", "r2c2"), state.matching.map { it.id })
        assertEquals(listOf("r2c1", "r1c3", "r2c2"), state.groups.single { it.first.id == "red" }.second.map { it.id })
    }

    @Test
    fun theListSearchesRewardsWarriorsAndKeeps() = runTest {
        val vm = viewModel()
        for ((query, ids) in listOf("Skyloft" to listOf("r2c1"), "North Keep" to listOf("r1c1"), "zelda" to listOf("r1c1", "r1c3", "r2c1"), "A-3" to listOf("r1c3"))) {
            vm.setQuery(query)
            assertEquals(ids, vm.ready { it.session.query == query }.matching.map { it.id }, query)
        }
    }

    @Test
    fun markAllCompletesTheShownSquaresInOneWriteAndUndoes() = runTest {
        storage.setDone("file_1", "r2c2.fairy_food_magic_beans", true)
        val vm = viewModel()
        val before = dataStore.writes
        vm.setAllDone(null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        var state = vm.ready { AdventureFixture.map.squares.all(it.progress::isComplete) }
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 5 squares complete on File 1", change.message)
        assertEquals(BulkAction.CLEAR, state.bulkScope().action)

        vm.undoBulkChange(change)!!.join()
        state = vm.ready { it.progress.status.isEmpty() }
        assertEquals(setOf("r2c2.fairy_food_magic_beans"), state.progress.done, "exactly what was there")
    }

    @Test
    fun anAreaOrAFilterLimitsMarkAll() = runTest {
        val vm = viewModel()
        vm.setAllDone("green", true)!!.join()
        assertEquals(setOf("r1c1", "r1c2"), progress().status.keys)
        vm.setFilter(SquareFilter.HEART_CONTAINERS, true).join()
        val state = vm.ready { SquareFilter.HEART_CONTAINERS in it.mapPrefs.filters }
        assertEquals(listOf("r1c3"), state.bulkSquares().map { it.id }, "r1c1's is ticked now")
        assertTrue(state.bulkScope().filtered)
        assertEquals("Mark 1 shown complete", state.bulkScope().actionLabel)
        vm.setAllDone(null, true)!!.join()
        assertEquals(setOf("r1c1", "r1c2", "r1c3"), progress().status.keys)
    }

    @Test
    fun oneSquareCanBeCompletedFromItsSheet() = runTest {
        val vm = viewModel()
        vm.setSquareDone("r1c1", true)!!.join()
        val state = vm.ready { it.progress.isComplete(square("r1c1")) }
        assertEquals(7, state.progress.doneCount(square("r1c1")))
        vm.setSquareDone("r1c1", false)!!.join()
        assertFalse(vm.ready { !it.progress.isStarted(square("r1c1")) }.progress.isStarted(square("r1c1")))
    }

    /** The side panel leaves the map tappable: tapping the open square again closes it. */
    @Test
    fun tappingASquareOpensItAndTappingItAgainClosesIt() = runTest {
        val vm = viewModel()
        vm.tapSquare("r1c1")
        assertEquals("r1c1", vm.selected.value)
        vm.tapSquare("r2c1")
        assertEquals("r2c1", vm.selected.value, "another square swaps what's open")
        vm.tapSquare("r2c1")
        assertNull(vm.selected.value)
    }

    @Test
    fun theTabAndTheChipsAreKeptForTheMap() = runTest {
        val vm = viewModel()
        vm.setTab(AdventureTab.LIST).join()
        vm.setFilter(SquareFilter.SKULLTULAS, true).join()
        vm.ready { it.mapPrefs.tab == AdventureTab.LIST }
        val again = main.track(AdventureMapViewModel(storage) { AdventureFixture.map })
        backgroundScope.launchCollect(again)
        val state = again.ready { it.mapPrefs.tab == AdventureTab.LIST }
        assertEquals(setOf(SquareFilter.SKULLTULAS), state.mapPrefs.filters)
        assertEquals(listOf("r1c1"), state.matching.map { it.id })
    }

    @Test
    fun aMissingMapIsReported() = runTest {
        val vm = main.track(AdventureMapViewModel(storage) { error("no such file") })
        backgroundScope.launchCollect(vm)
        assertTrue(vm.loadFailed.first { it })
        assertNull(vm.state.value)
    }

    private fun CoroutineScope.launchCollect(vm: AdventureMapViewModel) = launch { vm.state.collect {} }
}
