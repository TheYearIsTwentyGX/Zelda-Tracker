package gx.zelda

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.ui.ArmorDetailScreen
import gx.zelda.armor.ui.ArmorTrackerScreen
import gx.zelda.armor.ui.ArmorViewModel
import gx.zelda.bosses.data.BossStorage
import gx.zelda.bosses.ui.BossDetailScreen
import gx.zelda.bosses.ui.BossTrackerScreen
import gx.zelda.bosses.ui.BossViewModel
import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.compendium.ui.CompendiumDetailScreen
import gx.zelda.compendium.ui.CompendiumTrackerScreen
import gx.zelda.compendium.ui.CompendiumViewModel
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.adventure.data.AdventureStorage
import gx.zelda.adventure.ui.AdventureMapScreen
import gx.zelda.adventure.ui.AdventureMapViewModel
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.ui.ChecklistDetailScreen
import gx.zelda.checklist.ui.ChecklistScreen
import gx.zelda.checklist.ui.ChecklistViewModel
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind
import gx.zelda.core.ui.HomeScreen
import gx.zelda.core.ui.ZeldaTheme
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.fairies.ui.FairyTrackerScreen
import gx.zelda.fairies.ui.FairyViewModel
import gx.zelda.hearts.data.HeartStorage
import gx.zelda.hearts.ui.HeartTrackerScreen
import gx.zelda.hearts.ui.HeartViewModel
import gx.zelda.kinstones.data.KinstoneStorage
import gx.zelda.kinstones.ui.KinstoneDetailScreen
import gx.zelda.kinstones.ui.KinstoneTrackerScreen
import gx.zelda.kinstones.ui.KinstoneViewModel
import gx.zelda.figurines.data.FigurineStorage
import gx.zelda.figurines.ui.FigurineTrackerScreen
import gx.zelda.figurines.ui.FigurineViewModel
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.scan.KorokScanScreen
import gx.zelda.koroks.scan.KorokScanViewModel
import gx.zelda.koroks.ui.KorokDetailScreen
import gx.zelda.koroks.ui.KorokTrackerScreen
import gx.zelda.koroks.ui.KorokViewModel
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.ui.MainQuestScreen
import gx.zelda.mainquest.ui.MainQuestViewModel
import gx.zelda.overview.OverviewScreen
import gx.zelda.overview.OverviewViewModel
import gx.zelda.map.data.MapLayers
import gx.zelda.map.data.MapStorage
import gx.zelda.map.data.botwMapLayers
import gx.zelda.map.model.markerKey
import gx.zelda.map.ui.MapViewModel
import gx.zelda.map.ui.WorldMapScreen
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.scan.ShrineScanScreen
import gx.zelda.shrines.ui.ShrineDetailScreen
import gx.zelda.shrines.scan.ShrineScanViewModel
import gx.zelda.shrines.ui.ShrineTrackerScreen
import gx.zelda.shrines.ui.ShrineViewModel
import gx.zelda.sidequests.data.SideQuestStorage
import gx.zelda.sidequests.ui.SideQuestDetailScreen
import gx.zelda.sidequests.ui.SideQuestTrackerScreen
import gx.zelda.sidequests.ui.SideQuestViewModel
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination.Companion.hasRoute
import gx.zelda.core.AppSettings
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.ui.AppDrawerSheet
import gx.zelda.core.ui.AppMenu
import gx.zelda.core.ui.LocalAppMenu
import gx.zelda.core.ui.MenuSelection
import gx.zelda.settings.AppSettingsViewModel
import gx.zelda.settings.SettingsScreen
import kotlinx.coroutines.launch
import gx.zelda.zeldanotes.NotesImportScreen
import gx.zelda.zeldanotes.NotesImportViewModel
import kotlinx.serialization.Serializable

@Serializable private data object Home
@Serializable private data class ArmorTracker(val gameId: String)
@Serializable private data class ArmorDetail(val gameId: String, val pieceId: String)
@Serializable private data class Fairies(val gameId: String)
@Serializable private data class MainQuest(val gameId: String)
@Serializable private data class Shrines(val gameId: String)
@Serializable private data class Compendium(val gameId: String)
@Serializable private data class CompendiumDetail(val gameId: String, val entryId: String)
@Serializable private data class Koroks(val gameId: String)
@Serializable private data class KorokDetail(val gameId: String, val korokId: String)
@Serializable private data class ShrineScan(val gameId: String)

@Serializable private data class KorokScan(val gameId: String)
@Serializable private data class NotesImport(val gameId: String)
@Serializable private data class Overview(val gameId: String)
@Serializable private data class SideQuests(val gameId: String)
@Serializable private data class SideQuestDetail(val gameId: String, val questId: String)
@Serializable private data class Bosses(val gameId: String)
@Serializable private data class BossDetail(val gameId: String, val spawnId: String)
@Serializable private data class ShrineDetail(val gameId: String, val shrineId: String)
@Serializable private data class HeartPieces(val gameId: String)
@Serializable private data class Kinstones(val gameId: String)
@Serializable private data class KinstoneDetail(val gameId: String, val fusionId: String)
@Serializable private data class Figurines(val gameId: String)
/** A checklist tracker, named by its dataset since a game can have several. */
@Serializable private data class Checklist(val gameId: String, val dataFile: String)
@Serializable private data class ChecklistDetail(val gameId: String, val dataFile: String, val itemId: String)
/** A Hyrule Warriors Adventure Mode map, named by its dataset since a game has several. */
@Serializable private data class AdventureMap(val gameId: String, val dataFile: String)
@Serializable private data object Settings
/** [focusLayer] and [focusId] name the entry "Show on map" centres on, if any. */
@Serializable private data class WorldMap(val gameId: String, val focusLayer: String? = null, val focusId: String? = null)

@Composable
fun App(dataStore: DataStore<Preferences>) {
    ZeldaTheme {
        val nav = rememberNavController()
        val settingsVm = viewModel(key = "app_settings") { AppSettingsViewModel(AppSettingsStorage(dataStore)) }
        val settings by settingsVm.settings.collectAsStateWithLifecycle()
        // The NavHost keeps its old indent inside the drawer, so wrapping it didn't touch every route.
        AppMenuDrawer(nav, settings, settingsVm) {
        // Edge-to-edge (forced from targetSdk 35) means adjustResize no longer shrinks the window
        // for the keyboard, so lift every screen above it here. imePadding consumes the IME
        // insets, so each Scaffold drops its own navigation-bar padding while the keyboard is up.
        NavHost(navController = nav, startDestination = Home, modifier = Modifier.fillMaxSize().imePadding()) {
            composable<Home> {
                val menu = LocalAppMenu.current
                HomeScreen(
                    games = settings?.shownGames(),
                    onOpenTracker = { game, tracker -> nav.navigate(trackerRoute(game.id, tracker)) },
                    onOpenMenu = { menu?.open() },
                    onOpenSettings = { nav.navigate(Settings) },
                )
            }
            composable<Settings> {
                SettingsScreen(settings, settingsVm, onBack = { nav.popBackStack() })
            }
            composable<Overview> { entry ->
                val game = Games.byId(entry.toRoute<Overview>().gameId)
                val vm = viewModel(key = "overview_${game.id}") { OverviewViewModel(dataStore, game) }
                val state by vm.state.collectAsStateWithLifecycle()
                OverviewScreen(
                    state,
                    vm,
                    onOpenTracker = { kind, dataFile -> nav.openTrackerFromOverview(game, kind, dataFile) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Fairies> { entry ->
                val game = Games.byId(entry.toRoute<Fairies>().gameId)
                val vm = viewModel(key = game.id) { FairyViewModel(FairyStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                FairyTrackerScreen(state, loadFailed, vm, onBack = { nav.popBackStack() })
            }
            composable<MainQuest> { entry ->
                val game = Games.byId(entry.toRoute<MainQuest>().gameId)
                val vm = viewModel(key = game.id) { MainQuestViewModel(MainQuestStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                MainQuestScreen(
                    state,
                    loadFailed,
                    vm,
                    onBack = { nav.popBackStack() },
                    title = game.trackers.first { it.kind == TrackerKind.MAIN_QUEST }.title,
                )
            }
            composable<Shrines> { entry ->
                val game = Games.byId(entry.toRoute<Shrines>().gameId)
                val vm = shrineViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                ShrineTrackerScreen(
                    state,
                    loadFailed,
                    vm,
                    onBack = { nav.popBackStack() },
                    title = game.trackers.first { it.kind == TrackerKind.SHRINES }.title,
                    // Only BotW has the shrine map data the scanner reads.
                    onScanMap = { nav.navigate(ShrineScan(game.id)) }.takeIf { game == Games.BOTW },
                    onOpenShrine = { nav.navigate(ShrineDetail(game.id, it)) },
                )
            }
            composable<ShrineDetail> { entry ->
                val route = entry.toRoute<ShrineDetail>()
                val listEntry = remember(entry) { nav.getBackStackEntry<Shrines>() }
                val vm = shrineViewModel(listEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    ShrineDetailScreen(it, route.shrineId, vm, onBack = { nav.popBackStack() })
                }
            }
            composable<ShrineScan> { entry ->
                val game = Games.byId(entry.toRoute<ShrineScan>().gameId)
                val vm = viewModel(key = "scan_${game.id}") { ShrineScanViewModel(ShrineStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                ShrineScanScreen(state, loadFailed, vm, onBack = { nav.popBackStack() })
            }
            composable<Compendium> { entry ->
                val game = Games.byId(entry.toRoute<Compendium>().gameId)
                val vm = compendiumViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                CompendiumTrackerScreen(
                    state = state,
                    loadFailed = loadFailed,
                    viewModel = vm,
                    onOpenEntry = { nav.navigate(CompendiumDetail(game.id, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<CompendiumDetail> { entry ->
                val route = entry.toRoute<CompendiumDetail>()
                val trackerEntry = remember(entry) { nav.getBackStackEntry<Compendium>() }
                val vm = compendiumViewModel(trackerEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    CompendiumDetailScreen(it, route.entryId, vm, onBack = { nav.popBackStack() })
                }
            }
            composable<Koroks> { entry ->
                val game = Games.byId(entry.toRoute<Koroks>().gameId)
                val vm = korokViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                KorokTrackerScreen(
                    state,
                    loadFailed,
                    vm,
                    onOpenKorok = { nav.navigate(KorokDetail(game.id, it)) },
                    onBack = { nav.popBackStack() },
                    // Only BotW has the map tiles and marker template the live scan reads.
                    onScanLive = { nav.navigate(KorokScan(game.id)) }.takeIf { game == Games.BOTW },
                    // Zelda Notes and its droplet template are BotW's.
                    onImportNotes = { nav.navigate(NotesImport(game.id)) }.takeIf { game == Games.BOTW },
                )
            }
            composable<NotesImport> { entry ->
                val game = Games.byId(entry.toRoute<NotesImport>().gameId)
                val vm = viewModel(key = "notes_import_${game.id}") { NotesImportViewModel(KorokStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                NotesImportScreen(state, loadFailed, vm, onBack = { nav.popBackStack() })
            }
            composable<KorokScan> { entry ->
                val game = Games.byId(entry.toRoute<KorokScan>().gameId)
                val vm = viewModel(key = "korok_scan_${game.id}") { KorokScanViewModel(KorokStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                KorokScanScreen(state, loadFailed, vm, onBack = { nav.popBackStack() })
            }
            composable<KorokDetail> { entry ->
                val route = entry.toRoute<KorokDetail>()
                val listEntry = remember(entry) { nav.getBackStackEntry<Koroks>() }
                val vm = korokViewModel(listEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    KorokDetailScreen(
                        it,
                        route.korokId,
                        vm,
                        onBack = { nav.popBackStack() },
                        onShowOnMap = { nav.navigate(WorldMap(route.gameId, MapLayers.KOROKS, route.korokId)) },
                    )
                }
            }
            composable<SideQuests> { entry ->
                val game = Games.byId(entry.toRoute<SideQuests>().gameId)
                val vm = sideQuestViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                SideQuestTrackerScreen(
                    state,
                    loadFailed,
                    vm,
                    onOpenQuest = { nav.navigate(SideQuestDetail(game.id, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<SideQuestDetail> { entry ->
                val route = entry.toRoute<SideQuestDetail>()
                val game = Games.byId(route.gameId)
                val listEntry = remember(entry) { nav.getBackStackEntry<SideQuests>() }
                val vm = sideQuestViewModel(listEntry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    SideQuestDetailScreen(
                        it,
                        route.questId,
                        vm,
                        // The armor detail shares its list's view model, so open the list under it.
                        onOpenArmor = if (game.has(TrackerKind.ARMOR)) {
                            { pieceId ->
                                nav.navigate(ArmorTracker(game.id))
                                nav.navigate(ArmorDetail(game.id, pieceId))
                            }
                        } else {
                            null
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable<Bosses> { entry ->
                val game = Games.byId(entry.toRoute<Bosses>().gameId)
                val vm = bossViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                BossTrackerScreen(
                    state,
                    loadFailed,
                    vm,
                    onOpenSpawn = { nav.navigate(BossDetail(game.id, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<BossDetail> { entry ->
                val route = entry.toRoute<BossDetail>()
                val listEntry = remember(entry) { nav.getBackStackEntry<Bosses>() }
                val game = Games.byId(route.gameId)
                val vm = bossViewModel(listEntry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    BossDetailScreen(
                        it,
                        route.spawnId,
                        vm,
                        onBack = { nav.popBackStack() },
                        onShowOnMap = { nav.navigate(WorldMap(game.id, MapLayers.BOSSES, route.spawnId)) }
                            .takeIf { game.has(TrackerKind.MAP) },
                    )
                }
            }
            composable<WorldMap> { entry ->
                val route = entry.toRoute<WorldMap>()
                val game = Games.byId(route.gameId)
                val focus = route.focusId?.let { markerKey(route.focusLayer ?: return@let null, it) }
                val vm = viewModel(key = "map_${game.id}") {
                    MapViewModel(MapStorage(dataStore, game), botwMapLayers(dataStore, game), focus)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                WorldMapScreen(
                    state,
                    loadFailed,
                    vm,
                    onOpenInTracker = { layerId, entryId ->
                        when (layerId) {
                            MapLayers.SHRINES -> nav.navigate(Shrines(game.id))
                            MapLayers.KOROKS -> {
                                // The detail screen shares the list's view model, so the list goes under it.
                                nav.navigate(Koroks(game.id))
                                nav.navigate(KorokDetail(game.id, entryId))
                            }
                            MapLayers.TOWERS -> nav.navigate(MainQuest(game.id))
                            MapLayers.BOSSES -> {
                                nav.navigate(Bosses(game.id))
                                nav.navigate(BossDetail(game.id, entryId))
                            }
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<HeartPieces> { entry ->
                val game = Games.byId(entry.toRoute<HeartPieces>().gameId)
                val vm = viewModel(key = "hearts_${game.id}") { HeartViewModel(HeartStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                HeartTrackerScreen(
                    state,
                    loadFailed,
                    vm,
                    onBack = { nav.popBackStack() },
                    title = game.trackers.first { it.kind == TrackerKind.HEART_PIECES }.title,
                )
            }
            composable<Kinstones> { entry ->
                val game = Games.byId(entry.toRoute<Kinstones>().gameId)
                val vm = kinstoneViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                KinstoneTrackerScreen(
                    state,
                    loadFailed,
                    vm,
                    onOpenFusion = { nav.navigate(KinstoneDetail(game.id, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<KinstoneDetail> { entry ->
                val route = entry.toRoute<KinstoneDetail>()
                val listEntry = remember(entry) { nav.getBackStackEntry<Kinstones>() }
                val vm = kinstoneViewModel(listEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    KinstoneDetailScreen(it, route.fusionId, vm, onBack = { nav.popBackStack() })
                }
            }
            composable<Figurines> { entry ->
                val game = Games.byId(entry.toRoute<Figurines>().gameId)
                val vm = viewModel(key = "figurines_${game.id}") { FigurineViewModel(FigurineStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                FigurineTrackerScreen(state, loadFailed, vm, onBack = { nav.popBackStack() })
            }
            composable<Checklist> { entry ->
                val route = entry.toRoute<Checklist>()
                val game = Games.byId(route.gameId)
                val vm = checklistViewModel(entry, dataStore, game, route.dataFile)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                ChecklistScreen(
                    state,
                    loadFailed,
                    vm,
                    title = game.trackers.firstOrNull { it.dataFile == route.dataFile }?.title ?: "Checklist",
                    onOpenItem = { nav.navigate(ChecklistDetail(game.id, route.dataFile, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ChecklistDetail> { entry ->
                val route = entry.toRoute<ChecklistDetail>()
                // Several checklists can be on the back stack; the detail belongs to the one with its dataset.
                val listEntry = remember(entry) {
                    nav.currentBackStack.value.last { it.destination.hasRoute<Checklist>() && it.toRoute<Checklist>().dataFile == route.dataFile }
                }
                val vm = checklistViewModel(listEntry, dataStore, Games.byId(route.gameId), route.dataFile)
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    ChecklistDetailScreen(it, route.itemId, vm, onBack = { nav.popBackStack() })
                }
            }
            composable<AdventureMap> { entry ->
                val route = entry.toRoute<AdventureMap>()
                val game = Games.byId(route.gameId)
                val vm = viewModel(key = "adventure_${game.id}_${route.dataFile}") {
                    AdventureMapViewModel(AdventureStorage(dataStore, game, route.dataFile))
                }
                AdventureMapScreen(
                    vm,
                    title = game.trackers.firstOrNull { it.dataFile == route.dataFile }?.title ?: "Adventure Map",
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ArmorTracker> { entry ->
                val game = Games.byId(entry.toRoute<ArmorTracker>().gameId)
                val vm = armorViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                ArmorTrackerScreen(
                    state = state,
                    loadFailed = loadFailed,
                    viewModel = vm,
                    onOpenPiece = { nav.navigate(ArmorDetail(game.id, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ArmorDetail> { entry ->
                val route = entry.toRoute<ArmorDetail>()
                val trackerEntry = remember(entry) { nav.getBackStackEntry<ArmorTracker>() }
                val vm = armorViewModel(trackerEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    ArmorDetailScreen(it, route.pieceId, vm, onBack = { nav.popBackStack() })
                }
            }
        }
        }
    }
}

/**
 * The [ArmorViewModel] for [game], owned by the armor list's back stack entry so the detail
 * screen opened on top of it shares the same one.
 */
@Composable
private fun armorViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): ArmorViewModel = viewModel(owner, key = game.id) { ArmorViewModel(ArmorStorage(dataStore, game)) }

/** The [CompendiumViewModel] for [game], owned by the Compendium list's back stack entry so its detail screen shares it. */
@Composable
private fun compendiumViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): CompendiumViewModel = viewModel(owner, key = game.id) { CompendiumViewModel(CompendiumStorage(dataStore, game)) }

/** The [ShrineViewModel] for [game], owned by the shrine list's entry so the detail screen shares it. */
@Composable
private fun shrineViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): ShrineViewModel = viewModel(owner, key = game.id) {
    // Freed Divine Beasts add Heart Containers to the Spirit Orb card.
    val mainQuest = MainQuestStorage(dataStore, game).takeIf { game.trackers.any { it.kind == TrackerKind.MAIN_QUEST } }
    ShrineViewModel(ShrineStorage(dataStore, game), mainQuest)
}

/** The [KorokViewModel] for [game], owned by the Korok list's entry so the detail screen shares it. */
@Composable
private fun korokViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): KorokViewModel = viewModel(owner, key = game.id) { KorokViewModel(KorokStorage(dataStore, game)) }

/**
 * The [ChecklistViewModel] for [dataFile], owned by its list's back stack entry so the detail screen
 * shares it.
 */
@Composable
private fun checklistViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
    dataFile: String,
): ChecklistViewModel = viewModel(owner, key = "checklist_${game.id}_$dataFile") {
    ChecklistViewModel(ChecklistStorage(dataStore, game, dataFile))
}

/**
 * Opens [kind]'s tracker for [game] from the Progress Overview ([dataFile] names a checklist or an Adventure map). The same mapping as the home
 * screen's; kept separate only so this change didn't edit lines other branches were changing.
 */
private fun NavController.openTrackerFromOverview(game: Game, kind: TrackerKind, dataFile: String?) {
    when (kind) {
        TrackerKind.ARMOR -> navigate(ArmorTracker(game.id))
        TrackerKind.FAIRIES -> navigate(Fairies(game.id))
        TrackerKind.MAIN_QUEST -> navigate(MainQuest(game.id))
        TrackerKind.SHRINES -> navigate(Shrines(game.id))
        TrackerKind.COMPENDIUM -> navigate(Compendium(game.id))
        TrackerKind.KOROKS -> navigate(Koroks(game.id))
        TrackerKind.OVERVIEW -> Unit
        TrackerKind.SIDE_QUESTS -> navigate(SideQuests(game.id))
        TrackerKind.BOSSES -> navigate(Bosses(game.id))
        TrackerKind.MAP -> navigate(WorldMap(game.id))
        TrackerKind.HEART_PIECES -> navigate(HeartPieces(game.id))
        TrackerKind.KINSTONES -> navigate(Kinstones(game.id))
        TrackerKind.FIGURINES -> navigate(Figurines(game.id))
        TrackerKind.CHECKLIST -> if (dataFile != null) navigate(Checklist(game.id, dataFile))
        TrackerKind.ADVENTURE_MAP -> if (dataFile != null) navigate(AdventureMap(game.id, dataFile))
    }
}

/**
 * The [SideQuestViewModel] for [game], owned by the quest list's entry so the detail screen shares
 * it. Shrine Quests show their shrine's state from the shrine tracker's storage.
 */
@Composable
private fun sideQuestViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): SideQuestViewModel = viewModel(owner, key = game.id) {
    SideQuestViewModel(SideQuestStorage(dataStore, game), ShrineStorage(dataStore, game).takeIf { game.has(TrackerKind.SHRINES) })
}

private fun Game.has(kind: TrackerKind): Boolean = trackers.any { it.kind == kind }

/** The [BossViewModel] for [game], owned by the boss list's entry so the detail screen shares it. */
@Composable
private fun bossViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): BossViewModel = viewModel(owner, key = game.id) { BossViewModel(BossStorage(dataStore, game)) }

/** The [KinstoneViewModel] for [game], owned by the fusion list's entry so the detail screen shares it. */
@Composable
private fun kinstoneViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): KinstoneViewModel = viewModel(owner, key = game.id) { KinstoneViewModel(KinstoneStorage(dataStore, game)) }

/**
 * The navigation drawer around [content] (the NavHost). The menu button on the home screen and on
 * trackers opened from it or from the menu opens it; it lists the shown games in release order,
 * each opening to its trackers, and opens the current game's section when that game changes.
 */
@Composable
private fun AppMenuDrawer(
    nav: NavController,
    settings: AppSettings?,
    settingsVm: AppSettingsViewModel,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStack.collectAsStateWithLifecycle()
    // The screen directly on Home: the one the menu or the home screen opened.
    val homeIndex = backStack.indexOfFirst { it.destination.hasRoute<Home>() }
    val topLevel = backStack.getOrNull(homeIndex + 1).takeIf { homeIndex >= 0 } ?: backStack.lastOrNull()
    val selection = topLevel?.menuSelection() ?: MenuSelection.None
    val currentGameId = (selection as? MenuSelection.Tracker)?.gameId

    LaunchedEffect(currentGameId) {
        if (currentGameId != null && currentGameId !in settingsVm.settings.value?.expandedGames.orEmpty()) {
            settingsVm.setExpanded(currentGameId, true)
        }
    }
    val menu = remember(nav, drawerState) {
        AppMenu(
            open = { scope.launch { drawerState.open() } },
            isTopLevel = { entry ->
                // Read now rather than from the collected state, which lags a frame behind a new
                // entry. What's under a live entry never changes, so no recomposition is needed.
                val stack = nav.currentBackStack.value
                val index = stack.indexOfFirst { it.id == entry.id }
                if (index < 0) null else index > 0 && stack[index - 1].destination.hasRoute<Home>()
            },
        )
    }
    // Every menu choice lands directly on Home, so Back from it always returns home.
    fun openFromMenu(route: Any) {
        scope.launch { drawerState.close() }
        nav.navigate(route) {
            popUpTo<Home>()
            launchSingleTop = true
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only swipe to close: trackers' pagers and the map use horizontal swipes of their own.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawerSheet(
                drawerState = drawerState,
                sections = settings?.gameMenu().orEmpty(),
                selection = selection,
                onHome = {
                    scope.launch { drawerState.close() }
                    nav.popBackStack<Home>(inclusive = false)
                },
                onSettings = { openFromMenu(Settings) },
                onToggleGame = { gameId, expanded -> settingsVm.setExpanded(gameId, expanded) },
                onOpenTracker = { game, tracker -> openFromMenu(trackerRoute(game.id, tracker)) },
            )
        },
    ) {
        CompositionLocalProvider(LocalAppMenu provides menu, content = content)
    }
}

/** The route of [tracker] for the game [gameId]. */
private fun trackerRoute(gameId: String, tracker: Tracker): Any = when (tracker.kind) {
    TrackerKind.ARMOR -> ArmorTracker(gameId)
    TrackerKind.FAIRIES -> Fairies(gameId)
    TrackerKind.MAIN_QUEST -> MainQuest(gameId)
    TrackerKind.SHRINES -> Shrines(gameId)
    TrackerKind.COMPENDIUM -> Compendium(gameId)
    TrackerKind.KOROKS -> Koroks(gameId)
    TrackerKind.OVERVIEW -> Overview(gameId)
    TrackerKind.SIDE_QUESTS -> SideQuests(gameId)
    TrackerKind.BOSSES -> Bosses(gameId)
    TrackerKind.MAP -> WorldMap(gameId)
    TrackerKind.HEART_PIECES -> HeartPieces(gameId)
    TrackerKind.KINSTONES -> Kinstones(gameId)
    TrackerKind.FIGURINES -> Figurines(gameId)
    TrackerKind.CHECKLIST -> Checklist(gameId, tracker.dataFile!!)
    TrackerKind.ADVENTURE_MAP -> AdventureMap(gameId, tracker.dataFile!!)
}

/** What the menu highlights for this entry: a tracker's list screen, Home or Settings. */
private fun NavBackStackEntry.menuSelection(): MenuSelection {
    val d = destination
    return when {
        d.hasRoute<Home>() -> MenuSelection.Home
        d.hasRoute<Settings>() -> MenuSelection.Settings
        d.hasRoute<ArmorTracker>() -> MenuSelection.Tracker(toRoute<ArmorTracker>().gameId, TrackerKind.ARMOR)
        d.hasRoute<Fairies>() -> MenuSelection.Tracker(toRoute<Fairies>().gameId, TrackerKind.FAIRIES)
        d.hasRoute<MainQuest>() -> MenuSelection.Tracker(toRoute<MainQuest>().gameId, TrackerKind.MAIN_QUEST)
        d.hasRoute<Shrines>() -> MenuSelection.Tracker(toRoute<Shrines>().gameId, TrackerKind.SHRINES)
        d.hasRoute<Compendium>() -> MenuSelection.Tracker(toRoute<Compendium>().gameId, TrackerKind.COMPENDIUM)
        d.hasRoute<Koroks>() -> MenuSelection.Tracker(toRoute<Koroks>().gameId, TrackerKind.KOROKS)
        d.hasRoute<Overview>() -> MenuSelection.Tracker(toRoute<Overview>().gameId, TrackerKind.OVERVIEW)
        d.hasRoute<SideQuests>() -> MenuSelection.Tracker(toRoute<SideQuests>().gameId, TrackerKind.SIDE_QUESTS)
        d.hasRoute<Bosses>() -> MenuSelection.Tracker(toRoute<Bosses>().gameId, TrackerKind.BOSSES)
        d.hasRoute<WorldMap>() -> MenuSelection.Tracker(toRoute<WorldMap>().gameId, TrackerKind.MAP)
        d.hasRoute<HeartPieces>() -> MenuSelection.Tracker(toRoute<HeartPieces>().gameId, TrackerKind.HEART_PIECES)
        d.hasRoute<Kinstones>() -> MenuSelection.Tracker(toRoute<Kinstones>().gameId, TrackerKind.KINSTONES)
        d.hasRoute<Figurines>() -> MenuSelection.Tracker(toRoute<Figurines>().gameId, TrackerKind.FIGURINES)
        d.hasRoute<Checklist>() -> toRoute<Checklist>().let { MenuSelection.Tracker(it.gameId, TrackerKind.CHECKLIST, it.dataFile) }
        d.hasRoute<AdventureMap>() -> toRoute<AdventureMap>().let { MenuSelection.Tracker(it.gameId, TrackerKind.ADVENTURE_MAP, it.dataFile) }
        else -> MenuSelection.None
    }
}
