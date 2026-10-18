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

/** Four Swords' overview: a row per checklist, built from the bundled data with no Four Swords code. */
@OptIn(ExperimentalCoroutinesApi::class)
class FsOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.FS

    private fun OverviewUiState.summary(title: String): ProgressSummary =
        (rows.single { it.title == title }.status as RowStatus.Ready).summary

    @Test
    fun everyChecklistLoadsIntoARow() = runTest {
        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        val state = vm.state.first { s -> s != null && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals(game.trackers.drop(1).map { it.title }, state.rows.map { it.title })
        state.rows.forEach { assertFalse(it.status == RowStatus.Unavailable, it.title) }

        val stages = state.summary("Stages & Keys")
        assertEquals(0 to 13, stages.done to stages.total)
        assertEquals("Keys 0 / 9 · Palace doors 0 / 3 · Silver Keys 0 / 5 · Golden Keys 0 / 4 · Hero's Keys 0 / 4", stages.secondary)
        assertEquals("Chambers of Insight, in Chambers of Insight", stages.next)
        assertEquals("Realm of Memories 0 / 9 · Hero's Trial 0 / 10", state.summary("Anniversary Edition").secondary)
        assertEquals("Sword Beams", state.summary("A Link to the Past Link-ups").next)
    }

    /** Silver Keys done on one file point that file at Vaati's Palace. */
    @Test
    fun theNextStepFollowsTheSaveFile() = runTest {
        val storage = ChecklistStorage(dataStore, game, "fs/stages.json")
        val stages = ChecklistRepository.load("fs/stages.json")
        stages.section("silver")!!.items.dropLast(1).forEach { storage.setDone("file_2", it, true) }

        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        vm.setSaveFile("file_2").join()
        val shown = vm.state.first { s -> s?.saveFile?.id == "file_2" && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals("Vaati's Palace: silver door, in Vaati's Palace", shown.summary("Stages & Keys").next)
        assertEquals("Keys 3 / 9 · Palace doors 0 / 3 · Silver Keys 4 / 5 · Golden Keys 0 / 4 · Hero's Keys 0 / 4", shown.summary("Stages & Keys").secondary)
    }
}
