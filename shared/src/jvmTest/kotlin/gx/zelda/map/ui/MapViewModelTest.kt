package gx.zelda.map.ui

import gx.zelda.bosses.data.BossStorage
import gx.zelda.bosses.model.BossCatalog
import gx.zelda.bosses.model.BossData
import gx.zelda.bosses.model.BossKind
import gx.zelda.bosses.model.BossSpawn
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.Korok
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.SheikahTower
import gx.zelda.map.data.BossLayer
import gx.zelda.map.data.KorokLayer
import gx.zelda.map.data.MapStorage
import gx.zelda.map.data.ShrineLayer
import gx.zelda.map.data.TowerLayer
import gx.zelda.map.data.botwMapLayers
import gx.zelda.map.model.MapMarker
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.TrialType
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

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    // A real single thread, like Android's main looper. With an unconfined Main the view model's
    // collectors run inline on DataStore's threads, and under load a fresh progress() collector
    // could miss an edit made from the view model.
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val main = TestMain(thread)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() {
        main.tearDown()
        thread.close()
    }

    private val game = Games.BOTW
    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val shrines = ShrineStorage(dataStore, game)
    private val koroks = KorokStorage(dataStore, game)
    private val mainQuest = MainQuestStorage(dataStore, game)
    private val bosses = BossStorage(dataStore, game)

    private val here = WorldPoint(0.0, 0.0)
    private val shrineLayer = ShrineLayer(shrines) {
        listOf("ja_baij", "oman_au").map { Shrine(it, it, "Great Plateau", "Trial", TrialType.PUZZLE, location = "") to here }
    }
    private val korokLayer = KorokLayer(koroks) {
        listOf("P01", "P02").map { Korok(it, 0, "E-6", "Great Plateau", 0.0, 0.0, 0.0, "Oman Au Shrine", "Rock Lift") to "Lone rock" }
    }
    private val towerLayer = TowerLayer(mainQuest) {
        listOf(SheikahTower("great_plateau", "Great Plateau Tower", "Great Plateau", "", "") to here)
    }

    // A Hinox that Master Mode ranks up, and the Master Mode file's extra Lynel.
    private val bossLayer = BossLayer(bosses) {
        BossData(
            BossCatalog(
                coordinates = "",
                regions = listOf("Great Plateau"),
                kinds = listOf(
                    BossKind("hinox", "Hinox", "Hinoxes", variants = listOf("hinox", "blue_hinox")),
                    BossKind("lynel", "Lynel", "Lynels", variants = listOf("white_maned_lynel")),
                ),
                spawns = listOf(
                    BossSpawn(1, "D-6", "hinox", "hinox", "Great Plateau", 0.0, 0.0, 0.0, "Hopper Pond", masterModeVariant = "blue_hinox"),
                    BossSpawn(2, "D-6", "lynel", "white_maned_lynel", "Great Plateau", 0.0, 0.0, 0.0, "Temple of Time", masterModeOnly = true),
                ),
            ),
            CompendiumCatalog(
                listOf("hinox" to "Hinox", "blue_hinox" to "Blue Hinox", "white_maned_lynel" to "White-Maned Lynel")
                    .map { (id, name) -> CompendiumEntry(id, 1, 1, name, CompendiumCategory.MONSTERS) },
            ),
        )
    }

    private fun vm(focus: String? = null) =
        main.track(MapViewModel(MapStorage(dataStore, game), listOf(shrineLayer, korokLayer, towerLayer, bossLayer), focus))

    /** Keeps [vm]'s state flowing, as the screen does, and waits for a state matching [until]. */
    private suspend fun TestScope.await(vm: MapViewModel, until: (MapUiState) -> Boolean): MapUiState {
        backgroundScope.launch { vm.state.collect {} }
        return vm.state.filterNotNull().first(until)
    }

    private fun MapUiState.marker(key: String): MapMarker? = markers.firstOrNull { it.key == key }

    @Test
    fun showsEveryLayerForTheActiveFile() = runTest {
        val state = await(vm()) { true }
        assertEquals("normal_1", state.saveFile.id)
        assertEquals(listOf("shrines", "koroks", "towers", "bosses"), state.layers.map { it.layer.id })
        assertEquals(6, state.markers.size)
        assertTrue(state.markers.all { it.entry.state == DiscoveryState.UNDISCOVERED && !it.anonymous })
    }

    @Test
    fun followsTheActiveSaveFile() = runTest {
        shrines.setState("normal_2", "ja_baij", DiscoveryState.COMPLETED)
        val vm = vm()
        await(vm) { it.saveFile.id == "normal_1" }
        vm.setSaveFile("normal_2").join()
        val state = await(vm) { it.saveFile.id == "normal_2" }
        assertEquals(DiscoveryState.COMPLETED, state.marker("shrines:ja_baij")?.entry?.state)
        assertEquals(listOf(1, 0, 0, 0), state.layers.map { it.done })
        // Switching here switches every tracker, which share the key.
        assertEquals("normal_2", koroks.activeSaveFile.first())
    }

    @Test
    fun sheetEditsGoToEachLayersTracker() = runTest {
        val vm = vm()
        val state = await(vm) { true }
        // One tap at a time, as in the sheet: each write lands in its own tracker before the next.
        vm.setState(state.marker("koroks:P01")!!, DiscoveryState.FOUND)!!.join()
        assertEquals(DiscoveryState.FOUND, koroks.progress("normal_1").first { "P01" in it.found }.state("P01"))
        vm.setState(state.marker("shrines:oman_au")!!, DiscoveryState.COMPLETED)!!.join()
        assertEquals(DiscoveryState.COMPLETED, shrines.progress("normal_1").first { "oman_au" in it.completed }.state("oman_au"))
        vm.setState(state.marker("towers:great_plateau")!!, DiscoveryState.COMPLETED)!!.join()
        assertTrue("great_plateau" in mainQuest.progress("normal_1").first { it.towers.isNotEmpty() }.towers)
        vm.setState(state.marker("bosses:1")!!, DiscoveryState.COMPLETED)!!.join()
        assertTrue(bosses.progress("normal_1").first { it.defeated.isNotEmpty() }.isDefeated("1"))
        val after = await(vm) { s -> s.layers.map { it.done } == listOf(1, 0, 1, 1) }
        assertEquals(DiscoveryState.FOUND, after.marker("koroks:P01")?.entry?.state)
    }

    @Test
    fun trackerSpoilerSettingHidesOnlyItsOwnLayer() = runTest {
        koroks.setState("normal_1", "P02", DiscoveryState.FOUND)
        shrines.updatePrefs { it.copy(hideUndiscovered = true) }
        val vm = vm()
        val state = await(vm) { s -> s.marker("koroks:P02")?.entry?.state == DiscoveryState.FOUND }
        assertEquals(listOf("koroks:P01", "koroks:P02", "towers:great_plateau", "bosses:1"), state.markers.map { it.key })
        // The sheet still knows a hidden shrine, unnamed.
        assertTrue(state.all.getValue("shrines:ja_baij").anonymous)

        vm.setUndiscoveredAsDots(true).join()
        val dots = await(vm) { it.prefs.undiscoveredAsDots }
        assertTrue(dots.marker("shrines:ja_baij")!!.anonymous)

        vm.reveal("shrines:ja_baij")
        assertFalse(await(vm) { !it.all.getValue("shrines:ja_baij").anonymous }.marker("shrines:ja_baij")!!.anonymous)
    }

    /** The Bosses layer names each boss as the active file meets it, and honours "Hide unfound bosses". */
    @Test
    fun bossesFollowTheFilesModeAndSpoilerSetting() = runTest {
        val vm = vm()
        val normal = await(vm) { true }
        assertEquals("Hinox", normal.marker("bosses:1")?.entry?.name)
        assertEquals(null, normal.marker("bosses:2"))

        vm.setSaveFile("master").join()
        val master = await(vm) { it.saveFile.masterMode }
        assertEquals("Blue Hinox", master.marker("bosses:1")?.entry?.name)
        assertEquals("White-Maned Lynel", master.marker("bosses:2")?.entry?.name)
        assertEquals(2, master.layers.first { it.layer.id == "bosses" }.total)

        bosses.setState("master", "1", DiscoveryState.FOUND)
        bosses.updatePrefs { it.copy(hideUndiscovered = true) }
        val hidden = await(vm) { s -> s.marker("bosses:2") == null && s.marker("bosses:1") != null }
        assertTrue(hidden.all.getValue("bosses:2").anonymous)
        assertFalse(hidden.marker("bosses:1")!!.anonymous)
    }

    @Test
    fun showOnMapPinsNamesAndSelectsTheEntry() = runTest {
        koroks.updatePrefs { it.copy(hideUndiscovered = true) }
        val vm = vm(focus = "koroks:P01")
        vm.setLayerOn("koroks", false).join()
        val state = await(vm) { "koroks" in it.prefs.hiddenLayers }
        assertEquals(listOf("koroks:P01"), state.markers.filter { it.layer.id == "koroks" }.map { it.key })
        assertFalse(state.marker("koroks:P01")!!.anonymous)
        assertEquals("koroks:P01", vm.selected.filterNotNull().first().key)
        assertEquals("koroks:P01", vm.pendingFocus.value)
        vm.focusShown()
        assertEquals(null, vm.pendingFocus.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val broken = KorokLayer(koroks) { throw IllegalStateException("files/botw/koroks.json not found") }
        val vm = main.track(MapViewModel(MapStorage(dataStore, game), listOf(shrineLayer, broken)))
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.loadFailed.first { it })
        assertEquals(null, vm.state.value)
    }

    @Test
    fun bundledLayersLoad() = runTest {
        val vm = main.track(MapViewModel(MapStorage(dataStore, game), botwMapLayers(dataStore, game)))
        val state = await(vm) { true }
        assertEquals(listOf(136, 900, 15, 108), state.layers.map { it.total })
        assertFalse(vm.loadFailed.value)
    }
}
