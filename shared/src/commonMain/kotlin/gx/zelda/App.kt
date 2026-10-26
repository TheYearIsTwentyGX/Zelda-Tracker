package gx.zelda

import androidx.compose.animation.AnimatedContentScope
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable as navComposable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.savedstate.read
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
import gx.zelda.checklist.data.StoryProgress
import gx.zelda.checklist.ui.ChecklistDetailScreen
import gx.zelda.checklist.ui.ChecklistScreen
import gx.zelda.checklist.ui.ChecklistViewModel
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind
import gx.zelda.core.ui.HomeScreen
import gx.zelda.core.ui.AppTheme
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
import gx.zelda.theme.GameThemes
import gx.zelda.potions.ui.PotionBookScreen
import gx.zelda.potions.ui.PotionRecipeScreen
import gx.zelda.potions.ui.PotionTotalsScreen
import gx.zelda.potions.ui.PotionViewModel
import gx.zelda.thrilldigger.data.ThrillDiggerStorage
import gx.zelda.thrilldigger.ui.ThrillDiggerScreen
import gx.zelda.thrilldigger.ui.ThrillDiggerViewModel
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.ui.UpgradeDetailScreen
import gx.zelda.upgrades.ui.UpgradeTrackerScreen
import gx.zelda.upgrades.ui.UpgradeViewModel
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavDestination.Companion.hasRoute
import gx.zelda.core.AppSettings
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.menuOpenedIn
import gx.zelda.core.withExpanded
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
@Serializable private data class ThrillDigger(val gameId: String)
@Serializable private data class ItemUpgrades(val gameId: String)
@Serializable private data class Potions(val gameId: String)
@Serializable private data class PotionRecipe(val gameId: String, val potionId: String)
@Serializable private data class PotionBookTotals(val gameId: String)
/** One copy of an item: [copy] is 1, or 2 and up for extra Seed Satchels, Quivers and Bomb Bags. */
@Serializable private data class ItemUpgradeDetail(val gameId: String, val itemId: String, val copy: Int)
@Serializable private data object Settings
/** [focusLayer] and [focusId] name the entry "Show on map" centres on, if any. */
@Serializable private data class WorldMap(val gameId: String, val focusLayer: String? = null, val focusId: String? = null)

/** [onDarkTheme] hears whether the app is light or dark, as Settings chose, for the system bars. */
@Composable
fun App(dataStore: DataStore<Preferences>, onDarkTheme: (Boolean) -> Unit = {}) {
    val settingsVm = viewModel(key = "app_settings") { AppSettingsViewModel(AppSettingsStorage(dataStore)) }
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    AppTheme(settings, onDarkTheme) {
        val nav = rememberNavController()
        // The NavHost keeps its old indent inside the drawer, so wrapping it didn't touch every route.
        AppMenuDrawer(nav, settings) {
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
            composable<ThrillDigger> { entry ->
                val game = Games.byId(entry.toRoute<ThrillDigger>().gameId)
                val vm = viewModel(key = "thrill_digger_${game.id}") { ThrillDiggerViewModel(ThrillDiggerStorage(dataStore, game)) }
                ThrillDiggerScreen(
                    vm,
                    title = game.trackers.first { it.kind == TrackerKind.THRILL_DIGGER }.title,
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ItemUpgrades> { entry ->
                val game = Games.byId(entry.toRoute<ItemUpgrades>().gameId)
                val vm = upgradeViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                UpgradeTrackerScreen(
                    state = state,
                    loadFailed = loadFailed,
                    viewModel = vm,
                    title = game.trackers.first { it.kind == TrackerKind.ITEM_UPGRADES }.title,
                    onOpenItem = { itemId, copy -> nav.navigate(ItemUpgradeDetail(game.id, itemId, copy)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Potions> { entry ->
                val game = Games.byId(entry.toRoute<Potions>().gameId)
                val vm = potionViewModel(entry, dataStore, game)
                PotionBookScreen(
                    vm,
                    title = game.trackers.first { it.kind == TrackerKind.POTIONS }.title,
                    onOpenPotion = { nav.navigate(PotionRecipe(game.id, it)) },
                    onOpenTotals = { nav.navigate(PotionBookTotals(game.id)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<PotionRecipe> { entry ->
                val route = entry.toRoute<PotionRecipe>()
                val bookEntry = remember(entry) { nav.getBackStackEntry<Potions>() }
                val vm = potionViewModel(bookEntry, dataStore, Games.byId(route.gameId))
                PotionRecipeScreen(vm, route.potionId, onBack = { nav.popBackStack() })
            }
            composable<PotionBookTotals> { entry ->
                val route = entry.toRoute<PotionBookTotals>()
                val bookEntry = remember(entry) { nav.getBackStackEntry<Potions>() }
                val vm = potionViewModel(bookEntry, dataStore, Games.byId(route.gameId))
                PotionTotalsScreen(
                    vm,
                    onOpenPotion = { nav.navigate(PotionRecipe(route.gameId, it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ItemUpgradeDetail> { entry ->
                val route = entry.toRoute<ItemUpgradeDetail>()
                val trackerEntry = remember(entry) { nav.getBackStackEntry<ItemUpgrades>() }
                val vm = upgradeViewModel(trackerEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    UpgradeDetailScreen(it, route.itemId, route.copy, vm, onBack = { nav.popBackStack() })
                }
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
 * Every destination goes through here rather than straight to [navComposable], so a screen inside a
 * game wears that game's theme: its route's `gameId` names the game ([GameThemes.of]). Home and
 * Settings have none and keep the app's own look. The theme is per destination, so a screen
 * fading out during navigation keeps its colours.
 */
private inline fun <reified T : Any> NavGraphBuilder.composable(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    navComposable<T> { entry ->
        ZeldaTheme(GameThemes.of(entry.arguments?.read { getStringOrNull("gameId") })) { content(entry) }
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

/** The [UpgradeViewModel] for [game], owned by the upgrade list's back stack entry so its detail screen shares it. */
@Composable
private fun upgradeViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): UpgradeViewModel = viewModel(owner, key = "upgrades_${game.id}") { UpgradeViewModel(UpgradeStorage(dataStore, game)) }

/** The [PotionViewModel] for [game], owned by the recipe book's back stack entry so each recipe page shares it. */
@Composable
private fun potionViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): PotionViewModel = viewModel(owner, key = "potions_${game.id}") { PotionViewModel(UpgradeStorage(dataStore, game)) }

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
    ChecklistViewModel(ChecklistStorage(dataStore, game, dataFile), storyGate = StoryProgress(dataStore, game, dataFile)::gate)
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
        TrackerKind.THRILL_DIGGER -> navigate(ThrillDigger(game.id))
        TrackerKind.ITEM_UPGRADES -> navigate(ItemUpgrades(game.id))
        TrackerKind.POTIONS -> navigate(Potions(game.id))
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
 * each opening to its trackers. Each time it opens, only the current game's section is open.
 */
@Composable
private fun AppMenuDrawer(
    nav: NavController,
    settings: AppSettings?,
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

    var expanded by remember { mutableStateOf(menuOpenedIn(currentGameId)) }
    val currentGame by rememberUpdatedState(currentGameId)
    LaunchedEffect(drawerState) {
        // Reset as the drawer starts to open, so it never slides in showing the last open's sections.
        snapshotFlow { drawerState.targetValue }.collect { if (it == DrawerValue.Open) expanded = menuOpenedIn(currentGame) }
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
                sections = settings?.gameMenu(expanded).orEmpty(),
                selection = selection,
                onHome = {
                    scope.launch { drawerState.close() }
                    nav.popBackStack<Home>(inclusive = false)
                },
                onSettings = { openFromMenu(Settings) },
                onToggleGame = { gameId, open -> expanded = expanded.withExpanded(gameId, open) },
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
    TrackerKind.THRILL_DIGGER -> ThrillDigger(gameId)
    TrackerKind.ITEM_UPGRADES -> ItemUpgrades(gameId)
    TrackerKind.POTIONS -> Potions(gameId)
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
        d.hasRoute<ThrillDigger>() -> MenuSelection.Tracker(toRoute<ThrillDigger>().gameId, TrackerKind.THRILL_DIGGER)
        d.hasRoute<ItemUpgrades>() -> MenuSelection.Tracker(toRoute<ItemUpgrades>().gameId, TrackerKind.ITEM_UPGRADES)
        d.hasRoute<Potions>() -> MenuSelection.Tracker(toRoute<Potions>().gameId, TrackerKind.POTIONS)
        else -> MenuSelection.None
    }
}
