package gx.zelda.sidequests.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.sidequests.data.SideQuestStorage
import gx.zelda.sidequests.model.QuestKind
import gx.zelda.sidequests.model.QuestState
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

/**
 * Mark all complete and clear all on BotW's side quests: a mark completes every quest, a clear puts
 * each back to not started (in progress ones too), and an undo puts back each quest's exact state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SideQuestBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = SideQuestStorage(dataStore, Games.BOTW)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): SideQuestViewModel {
        val vm = main.track(SideQuestViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun progress(file: String = "normal_1") = storage.progress(file).first()

    private val dueling = setOf(
        "arrows_of_burning_heat", "by_fireflys_light", "cooking_with_koko", "flown_the_coop", "koko_cuisine", "kokos_kitchen",
        "kokos_specialty", "misko_the_great_bandit", "playtime_with_cottla", "the_priceless_maracas", "wild_horses",
    )

    @Test
    fun aTabCompletesAllItsQuestsInOneWriteThenClearsThemToNotStarted() = runTest {
        storage.setState("normal_1", "wild_horses", QuestState.COMPLETED)
        storage.setState("normal_1", "koko_cuisine", QuestState.FOUND)
        val vm = viewModel()
        val shrineQuest = vm.state.value!!.catalog.ofKind(QuestKind.SHRINE).first().id
        vm.setState(shrineQuest, QuestState.FOUND)!!.join()
        vm.state.filterNotNull().first { it.progress.isStarted(shrineQuest) }

        val before = dataStore.writes
        vm.setAllDone(QuestKind.SIDE, null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(76, progress().completed.size)
        assertEquals("Marked 75 side quests complete on Save File 1", vm.bulkChange.first { it != null }!!.message)
        assertEquals(QuestState.FOUND, progress().state(shrineQuest), "another tab's quest stays")

        val done = vm.state.filterNotNull().first { it.bulkScope(QuestKind.SIDE).action == BulkAction.CLEAR }
        assertEquals("Each goes back to not started, in progress ones too.", done.bulkScope(QuestKind.SIDE).note)
        vm.setAllDone(QuestKind.SIDE, null, false)!!.join()
        assertEquals(emptySet(), progress().completed)
        assertEquals(setOf(shrineQuest), progress().started, "cleared quests aren't left in progress")
    }

    @Test
    fun aRegionCardTakesOnlyItsQuestsOnItsTab() = runTest {
        val vm = viewModel()
        assertEquals("Dueling Peaks", vm.state.value!!.bulkScope(QuestKind.SIDE, "Dueling Peaks").label)
        vm.setAllDone(QuestKind.SIDE, "Dueling Peaks", true)!!.join()
        assertEquals(dueling, progress().completed)
    }

    @Test
    fun aFilterNarrowsItToTheQuestsShown() = runTest {
        storage.setState("normal_1", "wild_horses", QuestState.COMPLETED)
        val vm = viewModel()
        vm.setQuery("koko")
        val searched = vm.state.filterNotNull().first { it.filters.query == "koko" }
        assertTrue(searched.bulkScope(QuestKind.SIDE).filtered)
        vm.setAllDone(QuestKind.SIDE, null, true)!!.join()
        assertEquals(setOf("wild_horses", "cooking_with_koko", "koko_cuisine", "kokos_kitchen", "kokos_specialty"), progress().completed)

        val completed = progress().completed
        vm.setQuery("")
        vm.setIncompleteOnly(true)
        vm.setState("misko_the_great_bandit", QuestState.FOUND)!!.join()
        vm.state.filterNotNull().first { it.filters.query.isEmpty() && it.filters.incompleteOnly && it.progress.isStarted("misko_the_great_bandit") }
        vm.setAllDone(QuestKind.SIDE, "Dueling Peaks", false)!!.join()
        assertEquals("Cleared 1 side quest on Save File 1", vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!.message)
        assertEquals(completed, progress().completed, "completed ones aren't shown, so they stay")
        assertEquals(completed, progress().started)
    }

    @Test
    fun undoPutsBackEachQuestsExactState() = runTest {
        storage.setState("normal_1", "wild_horses", QuestState.COMPLETED)
        storage.setState("normal_1", "koko_cuisine", QuestState.FOUND)
        val vm = viewModel()
        val prior = progress()

        vm.setAllDone(QuestKind.SIDE, "Dueling Peaks", true)!!.join()
        val mark = vm.bulkChange.first { it != null }!!
        vm.undoBulkChange(mark)!!.join()
        assertEquals(prior, progress(), "koko_cuisine back in progress, not completed or cleared")

        vm.state.filterNotNull().first { it.progress == prior }
        vm.setAllDone(QuestKind.SIDE, "Dueling Peaks", false)!!.join()
        assertEquals(QuestState.UNDISCOVERED, progress().state("koko_cuisine"))
        val clear = vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!
        assertEquals("Cleared 2 side quests on Save File 1", clear.message)
        vm.setState("misko_the_great_bandit", QuestState.FOUND)!!.join()
        vm.undoBulkChange(clear)!!.join()
        assertEquals(QuestState.COMPLETED, progress().state("wild_horses"))
        assertEquals(QuestState.FOUND, progress().state("koko_cuisine"))
        assertEquals(QuestState.UNDISCOVERED, progress().state("misko_the_great_bandit"), "it was in the change's scope, so its undo wins")
        assertNull(vm.undoBulkChange(clear), "an undo only works once")
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel()
        vm.setAllDone(QuestKind.DLC, null, true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 13 DLC quests complete on Save File 1", change.message)
        vm.dismissBulkChange(change)
        assertNull(vm.undoBulkChange(change))
        assertEquals(13, progress().completed.size)
    }

    @Test
    fun hiddenQuestsAreMarkedButNeverNamed() = runTest {
        val vm = viewModel()
        vm.setHideUndiscovered(true).join()
        val state = vm.state.filterNotNull().first { it.prefs.hideUndiscovered }
        val names = state.bulkQuests(QuestKind.SIDE, "Dueling Peaks").map { it.name }
        assertEquals(dueling.size, names.size, "hidden rows are in the scope")
        val scope = state.bulkScope(QuestKind.SIDE, "Dueling Peaks")
        val text = listOf(scope.label, scope.confirmTitle, scope.confirmText("Save File 1"), scope.contentDescription, scope.actionLabel)
        assertFalse(text.any { line -> names.any { it in line } }, "no quest name in $text")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only file 2 has, so the switch can be waited for by its progress, not just the file.
        val marker = "not_a_quest"
        storage.setState("normal_2", marker, QuestState.FOUND)
        val vm = viewModel()
        vm.setSaveFile("normal_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_2" && it.progress.isStarted(marker) }

        vm.setAllDone(QuestKind.SHRINE, null, true)!!.join()
        assertEquals(42, progress("normal_2").completed.size)
        assertEquals(emptySet(), progress("normal_1").completed)
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 42 Shrine Quests complete on Save File 2", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" && !it.progress.isStarted(marker) }
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf(marker), progress("normal_2").started)
        assertEquals(emptySet(), progress("normal_2").completed)
    }

    /** [SideQuestViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: SideQuestViewModel) = launch { vm.state.collect {} }
}
