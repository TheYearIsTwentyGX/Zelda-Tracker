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

/** Four Swords Adventures' overview: a row per checklist, built from the bundled data with no FSA code. */
@OptIn(ExperimentalCoroutinesApi::class)
class FsaOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.FSA

    private fun OverviewUiState.summary(title: String): ProgressSummary =
        (rows.single { it.title == title }.status as RowStatus.Ready).summary

    @Test
    fun everyChecklistLoadsIntoARow() = runTest {
        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        val state = vm.state.first { s -> s != null && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals(game.trackers.drop(1).map { it.title }, state.rows.map { it.title })
        state.rows.forEach { assertFalse(it.status == RowStatus.Unavailable, it.title) }

        val adventure = state.summary("Hyrulean Adventure")
        assertEquals(0 to 24, adventure.done to adventure.total)
        assertEquals("Maidens rescued 0 / 7 · Royal Jewels 0 / 4", adventure.secondary)
        assertEquals("Lake Hylia, in Whereabouts of the Wind", adventure.next)
        assertEquals("Tingle's Tower 0 / 8 · Shadow Battle 0 / 1", state.summary("Tingle's Tower & Shadow Battle").secondary)
    }

    /** The first level done on one file moves its next step on, and counts the Blue Maiden. */
    @Test
    fun theNextStepFollowsTheSaveFile() = runTest {
        val storage = ChecklistStorage(dataStore, game, "fsa/hyrulean_adventure.json")
        val adventure = ChecklistRepository.load("fsa/hyrulean_adventure.json")
        adventure.items.take(3).forEach { storage.setDone("file_3", it, true) }

        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        vm.setSaveFile("file_3").join()
        val shown = vm.state.first { s -> s?.saveFile?.id == "file_3" && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals("The Coast, in Eastern Hyrule", shown.summary("Hyrulean Adventure").next)
        assertEquals("Maidens rescued 1 / 7 · Royal Jewels 0 / 4", shown.summary("Hyrulean Adventure").secondary)
    }
}
