package gx.zelda.map.data

import gx.zelda.checklist.LinkFixture
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.data.StoryProgress
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.ui.ChecklistUiState
import gx.zelda.checklist.ui.ChecklistViewModel
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.DiscoveryState
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.map.model.MapMarker
import gx.zelda.map.model.MarkerIcon
import gx.zelda.map.ui.MapUiState
import gx.zelda.map.ui.MapViewModel
import gx.zelda.shrines.scan.WorldPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A checklist on the map is the checklist: the link fixture's Rewards file as a map layer, next to
 * its list's view model. A tick on either side shows on the other, through `sameAs` links too, and
 * mark-all, "Hide unchecked", story gating and save files all agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistMapLayerTest {
    // A real single thread, as in MapViewModelTest: the map's collectors run on Main, not inline on DataStore's threads.
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val main = TestMain(thread)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() {
        main.tearDown()
        thread.close()
    }

    private val game = LinkFixture.game
    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val rewards = ChecklistStorage(dataStore, game, "link/rewards.json")
    private val items = ChecklistStorage(dataStore, game, "link/items.json")
    private val story = ChecklistStorage(dataStore, game, "link/main_quest.json")

    private val source = ChecklistMapSource(
        dataFile = "link/rewards.json",
        title = "Rewards",
        noun = "reward",
        hiddenLabel = "Unclaimed reward",
        sections = mapOf("rewards" to MarkerIcon.EOW_STAMP),
        count = { if (it.id == "gold") 3 else 1 },
    )

    private fun layer() = ChecklistMapLayer(source, rewards, StoryProgress(dataStore, game, "link/rewards.json") { LinkFixture.story }::gate) {
        LinkFixture.rewards.sections.single().items.mapIndexed { i, item ->
            ChecklistMapLayer.Placed(item, WorldPoint(i.toDouble(), 0.0), MarkerIcon.EOW_STAMP, "Rewards")
        }
    }

    private suspend fun TestScope.mapVm(): MapViewModel {
        val vm = main.track(MapViewModel(MapStorage(dataStore, game), listOf(layer())))
        backgroundScope.launch { vm.state.collect {} }
        // Wait for the first state before writing, as the checklist tests do.
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun TestScope.listVm(catalog: ChecklistCatalog = LinkFixture.rewards): ChecklistViewModel {
        val gate = StoryProgress(dataStore, game, "link/rewards.json") { LinkFixture.story }
        val vm = main.track(ChecklistViewModel(rewards, gate::gate) { catalog })
        backgroundScope.launch { vm.state.collect {} }
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun MapViewModel.whenState(predicate: (MapUiState) -> Boolean) = state.filterNotNull().first(predicate)

    private suspend fun ChecklistViewModel.whenList(predicate: (ChecklistUiState) -> Boolean) = state.filterNotNull().first(predicate)

    private fun MapUiState.marker(id: String): MapMarker? = markers.firstOrNull { it.entry.id == id }

    private fun MapUiState.state(id: String) = all.getValue("rewards:$id").entry.state

    private fun ChecklistUiState.done(id: String) = progress.isDone(catalog.item(id)!!)

    @Test
    fun everyPlacedItemIsAMarkerWithTheChecklistsWording() = runTest {
        val state = mapVm().whenState { true }
        assertEquals(listOf("reward_5", "reward_10", "reward_30", "reward_50", "gold", "after_trial"), state.markers.map { it.entry.id })
        val summary = state.layers.single()
        assertEquals("Rewards", summary.layer.title)
        assertEquals(0 to 6, summary.done to summary.total)
        val gold = state.marker("gold")!!
        assertEquals("Gold Rupee", gold.entry.name)
        assertEquals("Town", gold.entry.region)
        assertEquals(3, gold.entry.count)
        assertEquals(DiscoveryState.UNDISCOVERED, gold.entry.state)
        assertEquals(listOf("Not collected", "Collected"), summary.layer.states.map(summary.layer::label))
    }

    @Test
    fun aTickOnTheMapIsATickInTheListAndBack() = runTest {
        val map = mapVm()
        val list = listVm()
        val state = map.whenState { true }

        map.setState(state.marker("gold")!!, DiscoveryState.COMPLETED)!!.join()
        list.whenList { it.done("gold") }

        list.setDone("gold", false)!!.join()
        list.setDone("after_trial", true)!!.join()
        val after = map.whenState { it.state("gold") == DiscoveryState.UNDISCOVERED && it.state("after_trial") == DiscoveryState.COMPLETED }
        assertEquals(1, after.layers.single().done)
    }

    @Test
    fun linkedRowsReadAndWriteTheItemTheyName() = runTest {
        val map = mapVm()
        val state = map.whenState { true }

        // Ticking the 5-crystal reward on the map raises Items' Wallet to its first tier.
        map.setState(state.marker("reward_5")!!, DiscoveryState.COMPLETED)!!.join()
        assertEquals(mapOf("wallet" to 1), items.progress("file_1").first { it.levels.isNotEmpty() }.levels)

        // A Wallet raised to tier 3 in Items shows every wallet reward collected on the map.
        items.setLevel("file_1", LinkFixture.item("wallet"), 3)
        map.whenState { s -> listOf("reward_5", "reward_30", "reward_50").all { s.state(it) == DiscoveryState.COMPLETED } }
    }

    @Test
    fun markAllInTheListShowsOnTheMap() = runTest {
        val map = mapVm()
        val list = listVm()
        list.setAllDone(null, true)!!.join()
        map.whenState { it.layers.single().done == 6 }
        list.setAllDone(null, false)!!.join()
        map.whenState { it.layers.single().done == 0 }
    }

    @Test
    fun hideUncheckedIsTheLayersSpoilerSetting() = runTest {
        rewards.setDone("file_1", LinkFixture.item("gold"), true)
        rewards.updatePrefs { it.copy(hideUnchecked = true) }
        val map = mapVm()
        val state = map.whenState { s -> s.marker("gold") != null && s.markers.size == 1 }
        assertFalse(state.marker("gold")!!.anonymous)
        assertTrue(state.all.getValue("rewards:reward_10").anonymous)
        assertEquals("Unclaimed reward", state.all.getValue("rewards:reward_10").layer.hiddenLabel)

        map.setUndiscoveredAsDots(true).join()
        assertTrue(map.whenState { it.markers.size == 6 }.marker("reward_10")!!.anonymous)
    }

    @Test
    fun storyGatingLeavesOutWhatTheListLeavesOut() = runTest {
        AppSettingsStorage(dataStore).setStoryGating(true)
        val map = mapVm()
        val locked = map.whenState { it.layers.single().locked == 3 }
        assertEquals(listOf("reward_5", "reward_10", "gold"), locked.markers.map { it.entry.id })
        // Totals keep what's still locked, as the list's counts do.
        assertEquals(6, locked.layers.single().total)

        // The temple opens the two big wallet rewards; the trial (Items' Sword tier 2) the last one.
        story.setDone("file_1", LinkFixture.item("temple"), true)
        map.whenState { it.layers.single().locked == 1 && it.marker("reward_30") != null }
        items.setLevel("file_1", LinkFixture.item("sword"), 2)
        map.whenState { it.layers.single().locked == 0 && it.markers.size == 6 }
    }

    @Test
    fun followsTheActiveSaveFile() = runTest {
        rewards.setDone("file_2", LinkFixture.item("gold"), true)
        val map = mapVm()
        assertEquals(DiscoveryState.UNDISCOVERED, map.whenState { it.saveFile.id == "file_1" }.state("gold"))

        map.setSaveFile("file_2").join()
        val second = map.whenState { it.saveFile.id == "file_2" }
        assertEquals(DiscoveryState.COMPLETED, second.state("gold"))

        // A tick after the switch lands on the file now shown.
        map.setState(second.marker("reward_10")!!, DiscoveryState.COMPLETED)!!.join()
        map.whenState { it.state("reward_10") == DiscoveryState.COMPLETED }
        assertEquals(mapOf("harp" to 1), items.progress("file_2").first { it.levels.isNotEmpty() }.levels)
        assertTrue(items.progress("file_1").first().levels.isEmpty())
    }
}
