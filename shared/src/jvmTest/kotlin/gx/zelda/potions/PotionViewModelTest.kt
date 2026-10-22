package gx.zelda.potions

import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.potions.ui.PotionViewModel
import gx.zelda.upgrades.data.UpgradeRepository
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.ui.UpgradeViewModel
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

/** The recipe book's view model: its cards, one write per tick, the same progress as Item Upgrades, mark all and save files. */
@OptIn(ExperimentalCoroutinesApi::class)
class PotionViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = UpgradeStorage(dataStore, Games.SS)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): PotionViewModel {
        val vm = main.track(PotionViewModel(storage))
        backgroundScope.launch { vm.state.collect {} }
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun potion(id: String, file: String = "file_1") = storage.progress(file).first().of(id)

    @Test
    fun theBookShowsEachPotionAndHowMuchIsMade() = runTest {
        storage.update("file_1") { it.withCopy("revitalizing_potion") { p -> p.copy(owned = true, level = 1) } }
        val vm = viewModel()
        val state = vm.state.filterNotNull().first { it.made > 0 }
        assertEquals(listOf("heart_potion", "stamina_potion", "air_potion", "revitalizing_potion", "guardian_potion"), state.cards.map { it.potion.id })
        val revitalizing = state.cards.single { it.potion.id == "revitalizing_potion" }
        assertEquals(2 to 3, revitalizing.made to revitalizing.steps)
        assertEquals(1, revitalizing.shownLevel, "the card shows the Revitalizing Potion+")
        assertEquals(2 to 12, state.made to state.steps)
        assertEquals(listOf(2, 0, 0), state.saveFiles.map { it.made })
    }

    @Test
    fun tickingAStepIsOneWriteAndTicksTheStepsBefore() = runTest {
        val vm = viewModel()
        var before = dataStore.writes
        vm.setStepMade("heart_potion", 2, made = true)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(PieceProgress(owned = true, level = 2), potion("heart_potion"))
        val state = vm.state.filterNotNull().first { it.made == 3 }
        assertEquals(listOf(true, true, true), state.recipe(state.potion("heart_potion")!!).map { it.made })
        assertTrue(state.needs(state.potion("heart_potion")!!).isEmpty)

        before = dataStore.writes
        vm.setStepMade("heart_potion", 1, made = false)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(PieceProgress(owned = true, level = 0), potion("heart_potion"), "the Heart Potion stays bought")

        assertNull(vm.setStepMade("not_a_potion", 0, made = true))
        assertNull(vm.setStepMade("beetle", 0, made = true), "only potions are in the book")
    }

    /** One progress: the book and Item Upgrades' Potions show the same, both ways. */
    @Test
    fun itSharesItsProgressWithItemUpgrades() = runTest {
        val book = viewModel()
        val upgrades = main.track(UpgradeViewModel(storage))
        backgroundScope.launch { upgrades.state.collect {} }
        upgrades.state.filterNotNull().first()

        book.setStepMade("guardian_potion", 1, made = true)!!.join()
        val upgradeState = upgrades.state.filterNotNull().first { it.progress.of("guardian_potion").owned }
        assertEquals(1, upgradeState.progress.of("guardian_potion").level)
        val totals = UpgradeCalculator.totals(upgradeState.catalog, upgradeState.progress, upgradeState.prefs, UpgradeTotalsMode.ALL)
        assertEquals(1_470, totals.rupees, "the Guardian Potion+'s 40 Rupees are done")

        upgrades.setLevel("air_potion", 1)!!.join()
        val bookState = book.state.filterNotNull().first { it.cards.single { c -> c.potion.id == "air_potion" }.made == 2 }
        assertTrue(bookState.cards.single { it.potion.id == "air_potion" }.done)
    }

    @Test
    fun markAllMakesEveryStepAndUndoPutsBackWhatWasThere() = runTest {
        storage.update("file_1") { it.withCopy("heart_potion") { p -> p.copy(owned = true, level = 1) } }
        val vm = viewModel()
        val start = vm.state.filterNotNull().first { it.made == 2 }
        assertEquals("Mark all complete", start.bulkScope().actionLabel)
        assertEquals(5, start.bulkScope().changes)

        val before = dataStore.writes
        vm.setAllMade(true)!!.join()
        assertEquals(1, dataStore.writes - before)
        val all = vm.state.filterNotNull().first { it.made == 12 }
        assertEquals("Clear all", all.bulkScope().actionLabel)
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 5 potions complete on File 1", change.message)
        assertEquals(all.potions.map { it.id }.toSet(), storage.progress("file_1").first().items.keys, "nothing else is touched")

        vm.undoBulkChange(change)!!.join()
        assertEquals(PieceProgress(owned = true, level = 1), potion("heart_potion"))
        assertEquals(PieceProgress(), potion("guardian_potion"))
        assertNull(vm.undoBulkChange(change), "an undo only works once")

        vm.state.filterNotNull().first { it.made == 2 }
        vm.setAllMade(true)!!.join()
        vm.state.filterNotNull().first { it.made == 12 }
        vm.setAllMade(false)!!.join()
        assertEquals(PieceProgress(), potion("heart_potion"))
        assertEquals("Cleared 5 potions on File 1", vm.bulkChange.first { it?.message?.startsWith("Cleared") == true }!!.message)
    }

    @Test
    fun eachSaveFileKeepsItsOwnPotions() = runTest {
        // A mark only File 2 has, so the switch can be waited for by its progress.
        storage.update("file_2") { it.withCopy("air_potion") { p -> p.copy(owned = true) } }
        val vm = viewModel()
        vm.setStepMade("heart_potion", 0, made = true)!!.join()
        vm.setSaveFile("file_2").join()
        val two = vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.progress.of("air_potion").owned }
        assertFalse(two.progress.of("heart_potion").owned)

        vm.setStepMade("stamina_potion", 1, made = true)!!.join()
        assertEquals(PieceProgress(owned = true, level = 1), potion("stamina_potion", "file_2"))
        assertEquals(PieceProgress(), potion("stamina_potion", "file_1"))
        assertEquals(PieceProgress(owned = true), potion("heart_potion", "file_1"))
    }

    @Test
    fun aMissingDatasetReportsLoadFailure() = runTest {
        val vm = main.track(PotionViewModel(storage) { UpgradeRepository.parse("""{"sections": "not a list"}""") })
        assertTrue(vm.loadFailed.first { it })
        assertNull(vm.state.value)
    }
}
