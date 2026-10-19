package gx.zelda.overview

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.core.Games
import gx.zelda.core.ProgressSummary
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Tri Force Heroes's overview: a row per checklist, built from the bundled data with no TFH code. */
@OptIn(ExperimentalCoroutinesApi::class)
class TfhOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.TFH

    private fun OverviewUiState.summary(title: String): ProgressSummary =
        (rows.single { it.title == title }.status as RowStatus.Ready).summary

    @Test
    fun everyChecklistLoadsIntoARow() = runTest {
        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        val state = vm.state.first { s -> s != null && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals(game.trackers.drop(1).map { it.title }, state.rows.map { it.title })
        assertEquals(game.trackers.drop(1).map { it.dataFile }, state.rows.map { it.dataFile })
        state.rows.forEach { assertFalse(it.status == RowStatus.Unavailable, it.title) }

        val adventure = state.summary("Adventure")
        assertEquals(0, adventure.done)
        assertEquals(137, adventure.total)
        assertEquals(
            "Completion List 0 / 128 · Areas cleared 0 / 8 · Levels 0 / 32 · Drablands Challenges 0 / 96 · King Tuft's Rewards 0 / 9",
            adventure.secondary,
        )
        assertEquals("Deku Forest, in Woodlands", adventure.next)
        assertEquals(38, state.summary("Outfits").total)
        assertEquals(8, state.summary("Den of Trials").total)
        assertEquals(null, state.mapItems, "TFH has no map percentage")
    }

    @Test
    fun theNextStepFollowsProgressAndTheSpoilerSetting() = runTest {
        val storage = ChecklistStorage(dataStore, game, "tfh/adventure.json")
        val levels = ChecklistRepository.load("tfh/adventure.json")
        levels.section("levels")!!.items.take(2).forEach { storage.setDone("file_1", it, true) }
        val third = levels.section("levels")!!.items[2]

        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        vm.setSaveFile("file_1").join()
        val shown = vm.state.first { s -> s?.saveFile?.id == "file_1" && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals("${third.name}, in ${third.region}", shown.summary("Adventure").next)

        storage.updatePrefs { it.copy(hideUnchecked = true) }
        val hidden = vm.state.first { s -> s?.rows?.any { (it.status as? RowStatus.Ready)?.summary?.next?.contains("still to do") == true } == true }!!
        assertFalse(third.name in hidden.summary("Adventure").next!!)
    }
}
