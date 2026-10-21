package gx.zelda.upgrades.ui

import gx.zelda.armor.model.PieceProgress
import gx.zelda.armor.ui.OwnershipFilter
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.UpgradeFixture
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotalsMode
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

/** The item-upgrade view model: per-file progress, levels, extra pouch copies, one write per change, mark all and the potion chip. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = UpgradeStorage(dataStore, Games.SS)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): UpgradeViewModel {
        val vm = main.track(UpgradeViewModel(storage) { UpgradeFixture.catalog })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun progress(file: String = "file_1") = storage.progress(file).first()

    @Test
    fun aLevelCollectsTheItemInOneWrite() = runTest {
        val vm = viewModel()
        val before = dataStore.writes
        vm.setLevel("beetle", 2)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(PieceProgress(owned = true, level = 2), progress().of("beetle"))

        vm.setOwned("beetle", false)!!.join()
        assertEquals(PieceProgress(owned = false, level = 2), progress().of("beetle"), "unticking keeps the level, as armor does")
        val state = vm.state.filterNotNull().first { !it.progress.of("beetle").owned }
        assertEquals(0, state.progress.level(state.visible.first()), "an item you don't have counts at its base level")
    }

    @Test
    fun extraPouchCopiesAreTrackedButNotTotalled() = runTest {
        val vm = viewModel()
        var before = dataStore.writes
        vm.addCopy("seed_satchel")!!.join()
        assertEquals(1, dataStore.writes - before)
        assertNull(vm.addCopy("beetle"), "only pouch items have copies")

        val two = vm.state.filterNotNull().first { it.progress.copyCount(UpgradeFixture.satchel) == 2 }
        assertEquals(listOf("seed_satchel", "seed_satchel#2"), two.groups.single { it.section.id == "pouch" }.copies.map { it.key })

        vm.setLevel("seed_satchel#2", 1)!!.join()
        val state = vm.state.filterNotNull().first { it.progress.of("seed_satchel#2").owned }
        val totals = UpgradeCalculator.totals(state.catalog, state.progress, state.prefs, UpgradeTotalsMode.ALL)
        assertEquals(listOf(2), totals.lines.flatMap { it.uses }.filter { it.copy.item.multiple }.map { it.copy.number }.distinct())
        assertEquals(1, state.saveFiles.single { it.file.id == "file_1" }.obtained, "one item obtained, however many copies")

        before = dataStore.writes
        vm.removeCopy("seed_satchel", 2)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(1, progress().copyCount(UpgradeFixture.satchel))
        assertEquals(PieceProgress(), progress().of("seed_satchel#2"), "its progress goes with it")
    }

    @Test
    fun eachSaveFileKeepsItsOwnProgress() = runTest {
        // A mark only File 2 has, so the switch can be waited for by its progress.
        storage.update("file_2") { it.withCopy("hylian_shield") { p -> p.copy(owned = true) } }
        val vm = viewModel()
        vm.setLevel("beetle", 1)!!.join()
        vm.setSaveFile("file_2").join()
        val two = vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.progress.of("hylian_shield").owned }
        assertEquals(PieceProgress(), two.progress.of("beetle"))

        vm.addCopy("seed_satchel")!!.join()
        assertEquals(2, progress("file_2").copyCount(UpgradeFixture.satchel))
        assertEquals(1, progress("file_1").copyCount(UpgradeFixture.satchel))
        assertEquals(PieceProgress(true, 1), progress("file_1").of("beetle"))
    }

    @Test
    fun aSectionMarksItsCopiesCollectedAndUndoPutsThemBack() = runTest {
        storage.update("file_1") { it.withCopyAdded(UpgradeFixture.satchel).withCopy("seed_satchel") { p -> p.copy(owned = true, level = 1) } }
        val vm = viewModel()
        vm.state.filterNotNull().first { it.progress.copyCount(UpgradeFixture.satchel) == 2 }

        val before = dataStore.writes
        vm.setAllOwned("pouch", owned = true)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertTrue(progress().of("seed_satchel#2").owned)
        assertEquals(1, progress().of("seed_satchel").level, "levels stay")
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 1 item complete on File 1", change.message)

        vm.undoBulkChange(change)!!.join()
        assertEquals(PieceProgress(), progress().of("seed_satchel#2"))
        assertEquals(PieceProgress(true, 1), progress().of("seed_satchel"))
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun hidingThePotionsLeavesThemOutOfTheListAndMarkAll() = runTest {
        val vm = viewModel()
        vm.setSectionShown("potions", false).join()
        val state = vm.state.filterNotNull().first { !it.prefs.shows("potions") }
        assertEquals(listOf("items", "shields", "pouch"), state.groups.map { it.section.id })
        assertTrue(state.bulkScope().note!!.contains("Potions the filters hide isn't changed"))

        vm.setAllOwned(null, owned = true)!!.join()
        assertEquals(PieceProgress(), progress().of("heart_potion"))
        assertTrue(progress().of("hylian_shield").owned)

        vm.setSectionShown("potions", true).join()
        vm.state.filterNotNull().first { it.prefs.shows("potions") }
        assertEquals(emptySet(), storage.prefs.first().hiddenSections)
    }

    @Test
    fun theSearchAndOwnershipFilterNarrowTheRows() = runTest {
        storage.update("file_1") { it.withCopy("beetle") { p -> p.copy(owned = true) } }
        val vm = viewModel()
        vm.setQuery("quick")
        val found = vm.state.filterNotNull().first { it.query.isNotEmpty() }
        assertEquals(listOf("beetle"), found.groups.flatMap { it.copies }.map { it.key }, "a later form's name finds the item")
        assertEquals("Clear 1 shown", found.bulkScope().actionLabel)

        vm.setQuery("")
        vm.setOwnershipFilter(OwnershipFilter.MISSING)
        val missing = vm.state.filterNotNull().first { it.query.isEmpty() && it.ownershipFilter == OwnershipFilter.MISSING }
        assertEquals(listOf("hylian_shield", "seed_satchel", "heart_potion"), missing.groups.flatMap { it.copies }.map { it.key })
        assertEquals("Mark 3 shown complete", missing.bulkScope().actionLabel)
    }

    /** [UpgradeViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: UpgradeViewModel) = launch { vm.state.collect {} }
}
