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

/** Echoes of Wisdom's overview: a row per checklist, built from the bundled data with no EoW code. */
@OptIn(ExperimentalCoroutinesApi::class)
class EowOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.EOW

    private fun OverviewUiState.summary(title: String): ProgressSummary =
        (rows.single { it.title == title }.status as RowStatus.Ready).summary

    @Test
    fun everyChecklistLoadsIntoARow() = runTest {
        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        val state = vm.state.first { s -> s != null && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals(game.trackers.drop(1).map { it.title }, state.rows.map { it.title })
        assertEquals(game.trackers.drop(1).map { it.dataFile }, state.rows.map { it.dataFile })
        state.rows.forEach { assertFalse(it.status == RowStatus.Unavailable, it.title) }

        val hearts = state.summary("Heart Pieces")
        assertEquals(0, hearts.done)
        assertEquals(47, hearts.total)
        assertEquals("Hearts 3 / 20 · Pieces of Heart 0 / 40 · Heart Containers 0 / 7", hearts.secondary)
        assertEquals(127, state.summary("Echoes").total)
        assertEquals(null, state.mapItems, "EoW has no map percentage")
    }

    @Test
    fun theNextStepFollowsTheSaveFileAndTheSpoilerSetting() = runTest {
        val storage = ChecklistStorage(dataStore, game, "eow/main_quest.json")
        val quest = ChecklistRepository.load("eow/main_quest.json")
        quest.section("story")!!.items.take(2).forEach { storage.setDone("file_2", it, true) }
        val third = quest.section("story")!!.items[2]

        val vm = main.track(OverviewViewModel(dataStore, game, loadDispatcher = dispatcher))
        vm.setSaveFile("file_2").join()
        val shown = vm.state.first { s -> s?.saveFile?.id == "file_2" && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals("${third.name}, in ${third.region}", shown.summary("Main Quest").next)

        storage.updatePrefs { it.copy(hideUnchecked = true) }
        val hidden = vm.state.first { s -> s?.rows?.any { (it.status as? RowStatus.Ready)?.summary?.next?.contains("still to do") == true } == true }!!
        assertFalse(third.name in hidden.summary("Main Quest").next!!)
    }
}
