package gx.zelda.bosses.ui

import gx.zelda.bosses.data.BossStorage
import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.TestMain
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

/**
 * Mark all complete and clear all on BotW's overworld bosses: a mark defeats every boss, a clear
 * puts each back to not found, an undo puts back each one's exact state, and each save file acts on
 * its own spawns (the Master Mode file has an extra Lynel).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BossBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = BossStorage(dataStore, Games.BOTW)

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): BossViewModel {
        val vm = main.track(BossViewModel(storage))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun progress(file: String = "normal_1") = storage.progress(file).first()

    private val moldugas = setOf("3098676163", "3569739054", "494020159", "3538536935", "1755179653")
    private val hebraLynels = setOf("1423341458", "3788100745", "2378625228", "329409903")
    private val masterModeLynel = "1288562591"

    @Test
    fun aTabDefeatsAllItsBossesInOneWriteThenClearsThemToNotFound() = runTest {
        storage.setState("normal_1", "3098676163", DiscoveryState.COMPLETED)
        storage.setState("normal_1", "3569739054", DiscoveryState.FOUND)
        storage.setState("normal_1", "1698864303", DiscoveryState.FOUND)
        val vm = viewModel()

        val before = dataStore.writes
        vm.setAllDefeated("molduga", null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(moldugas, progress().defeated)
        assertEquals("Marked 4 bosses complete on Save File 1", vm.bulkChange.first { it != null }!!.message)

        val done = vm.state.filterNotNull().first { it.bulkScope("molduga").action == BulkAction.CLEAR }
        assertEquals("Moldugas", done.bulkScope("molduga").label)
        vm.setAllDefeated("molduga", null, false)!!.join()
        assertEquals(emptySet(), progress().defeated)
        assertEquals(setOf("1698864303"), progress().found, "cleared bosses aren't left found; the Hinox stays")
    }

    @Test
    fun aRegionCardTakesOnlyItsBossesOnItsTab() = runTest {
        val vm = viewModel()
        vm.setAllDefeated("lynel", "Hebra", true)!!.join()
        assertEquals(hebraLynels, progress().defeated)
    }

    @Test
    fun aVariantFilterNarrowsItToTheBossesShown() = runTest {
        val vm = viewModel()
        vm.toggleVariant("white_maned_lynel")
        val filtered = vm.state.filterNotNull().first { it.filters.variants.isNotEmpty() }
        assertTrue(filtered.bulkScope("lynel").filtered)
        assertFalse(filtered.bulkScope("hinox").filtered, "a Lynel chip doesn't narrow the Hinox tab")
        vm.setAllDefeated("lynel", null, true)!!.join()
        assertEquals(8, progress().defeated.size, "the Normal Mode file's 8 White-Maned Lynels")
        assertTrue(masterModeLynel !in progress().defeated)
    }

    @Test
    fun undoPutsBackEachBossesExactState() = runTest {
        storage.setState("normal_1", "3098676163", DiscoveryState.COMPLETED)
        storage.setState("normal_1", "3569739054", DiscoveryState.FOUND)
        val vm = viewModel()
        val prior = progress()

        vm.setAllDefeated("molduga", null, true)!!.join()
        vm.undoBulkChange(vm.bulkChange.first { it != null }!!)!!.join()
        assertEquals(prior, progress(), "the found one back to found, not defeated or unfound")

        vm.state.filterNotNull().first { it.progress == prior }
        vm.setAllDefeated("molduga", null, false)!!.join()
        val clear = vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!
        assertEquals("Cleared 2 bosses on Save File 1", clear.message)
        vm.setState("1698864303", DiscoveryState.FOUND)!!.join()
        vm.undoBulkChange(clear)!!.join()
        assertEquals(prior.defeated, progress().defeated)
        assertEquals(prior.found + "1698864303", progress().found, "an edit outside its scope survives the undo")
        assertNull(vm.bulkChange.value)
    }

    @Test
    fun aDismissedSnackbarDropsTheUndo() = runTest {
        val vm = viewModel()
        vm.setAllDefeated("molduga", null, true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.dismissBulkChange(change)
        assertNull(vm.undoBulkChange(change))
        assertEquals(moldugas, progress().defeated)
    }

    @Test
    fun hiddenBossesAreMarkedButNeverNamed() = runTest {
        val vm = viewModel()
        vm.setHideUndiscovered(true).join()
        val state = vm.state.filterNotNull().first { it.prefs.hideUndiscovered }
        val names = state.bulkSpawns("lynel", "Hebra").flatMap { listOf(state.data.variantName(it.variant), it.nearestPlace) }
        val scope = state.bulkScope("lynel", "Hebra")
        assertEquals(4, scope.total, "hidden rows are in the scope")
        val text = listOf(scope.label, scope.confirmTitle, scope.confirmText("Save File 1"), scope.contentDescription, scope.actionLabel)
        assertFalse(text.any { line -> names.any { it in line } }, "no boss named in $text")
    }

    @Test
    fun eachFileActsOnItsOwnSpawnsAndUndoGoesBackToIt() = runTest {
        val vm = viewModel()
        val normal = vm.state.value!!.bulkScope("lynel")
        assertEquals(22, normal.total)
        assertEquals("Each is marked defeated. Master Mode's own spawns aren't on this file, so they don't change.", normal.note)

        // A mark only the Master Mode file has, so the switch can be waited for by its progress, not just the file.
        val marker = "not_a_spawn"
        storage.setState("master", marker, DiscoveryState.FOUND)
        vm.setSaveFile("master").join()
        val master = vm.state.filterNotNull().first { it.saveFile.masterMode && it.progress.isFound(marker) }
        assertEquals(23, master.bulkScope("lynel").total)
        assertEquals("Each is marked defeated.", master.bulkScope("lynel").note)

        vm.setAllDefeated("lynel", null, true)!!.join()
        assertEquals(23, progress("master").defeated.size)
        assertTrue(masterModeLynel in progress("master").defeated)
        assertEquals(emptySet(), progress("normal_1").defeated)
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 23 bosses complete on Master Mode", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" && !it.progress.isFound(marker) }
        vm.undoBulkChange(change)!!.join()
        assertEquals(emptySet(), progress("master").defeated)
        assertEquals(setOf(marker), progress("master").found)
    }

    /** [BossViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: BossViewModel) = launch { vm.state.collect {} }
}
