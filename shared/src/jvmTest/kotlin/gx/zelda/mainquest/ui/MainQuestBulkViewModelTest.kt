package gx.zelda.mainquest.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestSection
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

/** Mark all complete and clear all on BotW's and The Minish Cap's main quest tabs: what they write, where, and their undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainQuestBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val botw = MainQuestStorage(dataStore, Games.BOTW)
    private val mc = MainQuestStorage(dataStore, Games.MC)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(storage: MainQuestStorage): MainQuestViewModel {
        val vm = main.track(MainQuestViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun MainQuestStorage.done(section: MainQuestSection, file: String = game.saveFiles.first().id) = progress(file).first().done(section)

    @Test
    fun aTabMarksAllItsEntriesInOneWriteThenClearsThem() = runTest {
        botw.setDone("normal_1", MainQuestSection.TOWERS, "great_plateau", true)
        botw.setDone("normal_1", MainQuestSection.DIVINE_BEASTS, "vah_ruta", true)
        val vm = viewModel(botw)
        vm.state.filterNotNull().first { it.progress.divineBeasts.isNotEmpty() }

        val before = dataStore.writes
        vm.setAllDone(MainQuestSection.TOWERS, null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(15, botw.done(MainQuestSection.TOWERS).size)
        assertEquals(setOf("vah_ruta"), botw.done(MainQuestSection.DIVINE_BEASTS), "another tab stays")
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 14 towers complete on Save File 1", change.message, "the one already done isn't counted")

        val done = vm.state.filterNotNull().first { it.bulkScope(MainQuestSection.TOWERS).action == BulkAction.CLEAR }
        assertEquals(15, done.bulkScope(MainQuestSection.TOWERS).changes)
        vm.setAllDone(MainQuestSection.TOWERS, null, false)!!.join()
        assertEquals(emptySet(), botw.done(MainQuestSection.TOWERS))
        assertEquals("Cleared 15 towers on Save File 1", vm.bulkChange.first { it != change }!!.message)
    }

    @Test
    fun anItemCategoryMarksOnlyItsItems() = runTest {
        val vm = viewModel(mc)
        assertEquals("Swords", vm.state.value!!.bulkScope(MainQuestSection.ITEMS, "Swords").label)
        vm.setAllDone(MainQuestSection.ITEMS, "Swords", true)!!.join()
        assertEquals(setOf("smiths_sword", "white_sword", "white_sword_two", "white_sword_three", "four_sword"), mc.done(MainQuestSection.ITEMS))
        assertEquals("Marked 5 items complete on File 1", vm.bulkChange.first { it != null }!!.message)
    }

    @Test
    fun theTigerScrollsTabTakesItsUpgradesAndTheirHeaderOnlyThem() = runTest {
        val vm = viewModel(mc)
        val upgrades = setOf("faster_spin", "faster_split", "longer_great_spin")
        vm.setAllDone(MainQuestSection.TIGER_SCROLLS, UPGRADES_GROUP, true)!!.join()
        assertEquals(upgrades, mc.done(MainQuestSection.TIGER_SCROLLS))

        val state = vm.state.filterNotNull().first { it.upgradeCount == 3 }
        val tab = state.bulkScope(MainQuestSection.TIGER_SCROLLS)
        assertEquals(11, tab.total, "8 scrolls and 3 upgrades")
        assertEquals("The technique upgrades are marked too.", tab.note)
        vm.setAllDone(MainQuestSection.TIGER_SCROLLS, null, true)!!.join()
        assertEquals(11, mc.done(MainQuestSection.TIGER_SCROLLS).size)
        assertEquals("Marked 8 techniques complete on File 1", vm.bulkChange.first { it?.changed == 8 }!!.message)
    }

    @Test
    fun undoPutsBackExactlyWhatWasThereAndKeepsLaterEdits() = runTest {
        mc.setDone("file_1", MainQuestSection.DUNGEONS, "cave_of_flames", true)
        val vm = viewModel(mc)
        vm.state.filterNotNull().first { it.progress.dungeons.isNotEmpty() }

        vm.setAllDone(MainQuestSection.DUNGEONS, null, true)!!.join()
        assertEquals(6, mc.done(MainQuestSection.DUNGEONS).size)
        val change = vm.bulkChange.first { it != null }!!
        vm.setDone(MainQuestSection.ITEMS, "gust_jar", true)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf("cave_of_flames"), mc.done(MainQuestSection.DUNGEONS))
        assertEquals(setOf("gust_jar"), mc.done(MainQuestSection.ITEMS), "an edit after it survives the undo")
        assertNull(vm.bulkChange.value)
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun undoOfAClearPutsBackOnlyWhatWasDone() = runTest {
        botw.setDone("normal_1", MainQuestSection.MEMORIES, "memory_1", true)
        botw.setDone("normal_1", MainQuestSection.MEMORIES, "memory_5", true)
        val vm = viewModel(botw)
        vm.state.filterNotNull().first { it.progress.memories.size == 2 }
        vm.setAllDone(MainQuestSection.MEMORIES, null, false)!!.join()
        assertEquals(emptySet(), botw.done(MainQuestSection.MEMORIES))
        vm.undoBulkChange(vm.bulkChange.first { it != null }!!)!!.join()
        assertEquals(setOf("memory_1", "memory_5"), botw.done(MainQuestSection.MEMORIES))
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel(botw)
        vm.setAllDone(MainQuestSection.DIVINE_BEASTS, null, true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.dismissBulkChange(change)
        assertNull(vm.bulkChange.value)
        assertNull(vm.undoBulkChange(change))
        assertEquals(4, botw.done(MainQuestSection.DIVINE_BEASTS).size)
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only file 2 has, so the switch can be waited for by its progress, not just the file.
        val marker = "not_a_dungeon"
        mc.setDone("file_2", MainQuestSection.DUNGEONS, marker, true)
        val vm = viewModel(mc)
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" && marker in it.progress.dungeons }

        vm.setAllDone(MainQuestSection.DUNGEONS, null, true)!!.join()
        assertEquals(7, mc.done(MainQuestSection.DUNGEONS, "file_2").size)
        assertEquals(emptySet(), mc.done(MainQuestSection.DUNGEONS, "file_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 6 dungeons complete on File 2", change.message)

        vm.setSaveFile("file_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_1" && marker !in it.progress.dungeons }
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf(marker), mc.done(MainQuestSection.DUNGEONS, "file_2"))
        assertEquals(emptySet(), mc.done(MainQuestSection.DUNGEONS, "file_1"))
    }

    /** [MainQuestViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: MainQuestViewModel) = launch { vm.state.collect {} }
}
