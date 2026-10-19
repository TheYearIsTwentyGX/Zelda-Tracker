package gx.zelda.shrines.ui

import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Discoveries
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.BlessingCurrency
import gx.zelda.shrines.model.Lightroot
import gx.zelda.shrines.model.OrbUpgrades
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.TrialType
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

/** Mark all complete and clear all on the shrine tracker: base and DLC shrines, regions, Lightroots, and the undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShrineBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))

    private fun shrine(id: String, region: String, dlc: Boolean = false, quest: String? = null) =
        Shrine(id, id.replaceFirstChar { it.uppercase() }, region, "Trial", TrialType.PUZZLE, quest = quest, dlc = dlc, location = "Somewhere")

    /** Two regions, each with a Champions' Ballad shrine among the base game's. */
    private val botw = ShrineCatalog(
        regions = listOf("Great Plateau", "Hateno"),
        upgrades = OrbUpgrades(4, 3, 30, 1, 10, 4),
        shrines = listOf(
            shrine("oman", "Great Plateau"),
            shrine("keh", "Great Plateau"),
            shrine("ja_baij", "Great Plateau", quest = "The Bird in the Mountains"),
            shrine("etsu", "Great Plateau", dlc = true),
            shrine("myahm", "Hateno"),
            shrine("kam", "Hateno", dlc = true),
        ),
    )
    private val base = setOf("oman", "keh", "ja_baij", "myahm")

    private val totk = ShrineCatalog(
        regions = listOf("Hebra", "Necluda"),
        upgrades = OrbUpgrades(4, 3, 40, 1, 10, 4, BlessingCurrency.LIGHT_OF_BLESSING),
        shrines = listOf(shrine("oromuwak", "Necluda"), shrine("joniu", "Hebra"), shrine("ihen", "Hebra")),
        lightroots = listOf(Lightroot("kawumoro", "Kawumoro", "oromuwak"), Lightroot("uinoj", "Uinoj", "joniu"), Lightroot("nehi", "Nehi", "ihen")),
    )

    private fun storage(game: Game = Games.BOTW) = ShrineStorage(dataStore, game)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(storage: ShrineStorage, catalog: ShrineCatalog = botw): ShrineViewModel {
        val vm = main.track(ShrineViewModel(storage) { catalog })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun ShrineStorage.now(file: String = "normal_1"): ShrineProgress = progress(file).first()

    @Test
    fun theBaseRowCompletesBaseShrinesOnlyInOneWriteAndTheDlcRowTheRest() = runTest {
        val storage = storage()
        storage.setState("normal_1", "oman", DiscoveryState.COMPLETED)
        val vm = viewModel(storage)

        val before = dataStore.writes
        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(base, storage.now().completed, "the DLC's shrines are left out")
        assertEquals(base, storage.now().found, "completing finds them too")
        assertEquals("Marked 3 shrines complete on Save File 1", vm.bulkChange.first { it != null }!!.message)

        vm.setAllCompleted(null, dlc = true, completed = true)!!.join()
        assertEquals(base + "etsu" + "kam", storage.now().completed)
    }

    @Test
    fun clearingLeavesEachShrineFound() = runTest {
        val storage = storage()
        storage.setState("normal_1", "etsu", DiscoveryState.COMPLETED)
        val vm = viewModel(storage)
        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        val done = vm.state.filterNotNull().first { it.shrineScope().action == BulkAction.CLEAR }
        assertTrue(done.shrineScope().note!!.contains("stays marked found"))

        vm.setAllCompleted(null, dlc = false, completed = false)!!.join()
        assertEquals(setOf("etsu"), storage.now().completed, "the DLC shrine stays complete")
        assertEquals(base + "etsu", storage.now().found)
        assertEquals("Cleared 4 shrines on Save File 1", vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!.message)
    }

    @Test
    fun aRegionCardTakesItsDlcShrinesOnlyWhileTheDlcChipShowsThem() = runTest {
        val storage = storage()
        val vm = viewModel(storage)
        val scope = vm.state.value!!.shrineScope("Great Plateau")
        assertEquals("Great Plateau Tower", scope.label)
        assertTrue(scope.note!!.contains("1 shrine from The Champions' Ballad"))
        vm.setAllCompleted("Great Plateau", dlc = false, completed = true)!!.join()
        assertEquals(setOf("oman", "keh", "ja_baij", "etsu"), storage.now().completed)

        vm.setShowDlc(false).join()
        vm.state.filterNotNull().first { !it.prefs.showDlc }
        assertTrue(vm.state.value!!.shrineScope().note!!.contains("The Champions' Ballad's shrines aren't changed"))
        vm.setAllCompleted("Great Plateau", dlc = false, completed = false)!!.join()
        assertEquals(setOf("etsu"), storage.now().completed, "a hidden DLC shrine isn't touched")
        assertNull(vm.setAllCompleted(null, dlc = true, completed = true), "nothing shows for the DLC row to act on")
    }

    @Test
    fun aFilterNarrowsItToTheShrinesShown() = runTest {
        val storage = storage()
        val vm = viewModel(storage)
        vm.setQuestsOnly(true)
        vm.state.filterNotNull().first { it.filters.questsOnly }
        assertEquals("Mark 1 shown complete", vm.state.value!!.shrineScope().actionLabel)
        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        assertEquals(setOf("ja_baij"), storage.now().completed)

        vm.setQuestsOnly(false)
        vm.setQuery("hateno")
        vm.state.filterNotNull().first { !it.filters.questsOnly && it.filters.query == "hateno" }
        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        assertEquals(setOf("ja_baij", "myahm"), storage.now().completed)
    }

    @Test
    fun theDialogNeverNamesAHiddenShrine() = runTest {
        val storage = storage()
        val vm = viewModel(storage)
        vm.setHideUndiscovered(true).join()
        val state = vm.state.filterNotNull().first { it.prefs.hideUndiscovered }
        for (scope in listOf(state.shrineScope(), state.shrineScope(dlc = true), state.shrineScope("Hateno"))) {
            val text = scope.confirmTitle + scope.confirmText("Save File 1") + scope.contentDescription
            botw.shrines.forEach { assertFalse(it.name in text, "${it.name} in: $text") }
        }
    }

    @Test
    fun undoPutsBackExactlyWhatWasThere() = runTest {
        val storage = storage()
        // A shrine saved before found existed is completed only; one is just found.
        storage.updateProgress("normal_1") { it.copy(completed = setOf("oman"), found = setOf("keh")) }
        val vm = viewModel(storage)
        val prior = storage.now()

        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        val mark = vm.bulkChange.first { it != null }!!
        vm.undoBulkChange(mark)!!.join()
        assertEquals(prior, storage.now())
        assertNull(vm.undoBulkChange(mark), "an undo only works once")

        vm.state.filterNotNull().first { it.progress == prior }
        vm.setAllCompleted("Great Plateau", dlc = false, completed = true)!!.join()
        vm.state.filterNotNull().first { it.shrineScope("Great Plateau").action == BulkAction.CLEAR }
        vm.setAllCompleted("Great Plateau", dlc = false, completed = false)!!.join()
        val clear = vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!
        vm.setCompleted("myahm", true)!!.join()
        vm.undoBulkChange(clear)!!.join()
        val after = storage.now()
        assertEquals(setOf("oman", "keh", "ja_baij", "etsu", "myahm"), after.completed, "the region back, the later edit kept")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        val storage = storage()
        // A mark only Save File 2 has, so the switch can be waited for by its progress.
        storage.setState("normal_2", "kam", DiscoveryState.FOUND)
        val vm = viewModel(storage)
        vm.setSaveFile("normal_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_2" && it.progress.isFound("kam") }

        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        assertEquals(base, storage.now("normal_2").completed)
        assertEquals(ShrineProgress(), storage.now("normal_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 4 shrines complete on Save File 2", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" }
        vm.undoBulkChange(change)!!.join()
        assertEquals(ShrineProgress(found = setOf("kam")), storage.now("normal_2"))
        assertEquals(ShrineProgress(), storage.now("normal_1"))
    }

    @Test
    fun lightrootsLightPerTabAndRegionWithoutTouchingShrines() = runTest {
        val storage = storage(Games.TOTK)
        storage.setLightrootState("file_1", "uinoj", DiscoveryState.FOUND)
        val vm = viewModel(storage, totk)

        val before = dataStore.writes
        vm.setAllLit("Hebra", lit = true)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(Discoveries(setOf("uinoj", "nehi"), setOf("uinoj", "nehi")), storage.now("file_1").lightroots)
        assertEquals("Marked 2 Lightroots complete on Save File 1", vm.bulkChange.first { it != null }!!.message)

        vm.setAllLit(null, lit = true)!!.join()
        assertEquals(3, storage.now("file_1").lightroots.completed.size)
        assertTrue(storage.now("file_1").completed.isEmpty(), "the shrines above stay as they were")

        vm.setAllLit(null, lit = false)!!.join()
        assertEquals(Discoveries(emptySet(), setOf("kawumoro", "uinoj", "nehi")), storage.now("file_1").lightroots, "each stays found")

        val clear = vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!
        vm.undoBulkChange(clear)!!.join()
        assertEquals(3, storage.now("file_1").lightroots.completed.size)
    }

    @Test
    fun totkShrinesHaveOneRowWithNoDlcNote() = runTest {
        val storage = storage(Games.TOTK)
        val vm = viewModel(storage, totk)
        assertNull(vm.state.value!!.shrineScope().note)
        vm.setAllCompleted(null, dlc = false, completed = true)!!.join()
        assertEquals(setOf("oromuwak", "joniu", "ihen"), storage.now("file_1").completed)
        assertTrue(storage.now("file_1").lightroots.completed.isEmpty())
    }

    @Test
    fun nothingToMarkWritesNothing() = runTest {
        val vm = viewModel(storage())
        vm.setQuery("nothing like this")
        vm.state.filterNotNull().first { it.filters.query.isNotEmpty() }
        val before = dataStore.writes
        assertNull(vm.setAllCompleted(null, dlc = false, completed = true))
        assertNull(vm.setAllCompleted("Hateno", dlc = false, completed = true))
        assertEquals(before, dataStore.writes)
    }

    /** [ShrineViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: ShrineViewModel) = launch { vm.state.collect {} }
}
