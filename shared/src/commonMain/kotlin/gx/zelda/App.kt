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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.ui.ArmorDetailScreen
import gx.zelda.armor.ui.ArmorTrackerScreen
import gx.zelda.armor.ui.ArmorViewModel
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.core.ui.HomeScreen
import gx.zelda.core.ui.ZeldaTheme
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.fairies.ui.FairyTrackerScreen
import gx.zelda.fairies.ui.FairyViewModel
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.ui.KorokDetailScreen
import gx.zelda.koroks.ui.KorokTrackerScreen
import gx.zelda.koroks.ui.KorokViewModel
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.ui.ShrineTrackerScreen
import gx.zelda.shrines.ui.ShrineViewModel
import kotlinx.serialization.Serializable

@Serializable private data object Home
@Serializable private data class ArmorTracker(val gameId: String)
@Serializable private data class ArmorDetail(val gameId: String, val pieceId: String)
@Serializable private data class Fairies(val gameId: String)
@Serializable private data class Shrines(val gameId: String)
@Serializable private data class Koroks(val gameId: String)
@Serializable private data class KorokDetail(val gameId: String, val korokId: String)

@Composable
fun App(dataStore: DataStore<Preferences>) {
    ZeldaTheme {
        val nav = rememberNavController()
        // Edge-to-edge (forced from targetSdk 35) means adjustResize no longer shrinks the window
        // for the keyboard, so lift every screen above it here. imePadding consumes the IME
        // insets, so each Scaffold drops its own navigation-bar padding while the keyboard is up.
        NavHost(navController = nav, startDestination = Home, modifier = Modifier.fillMaxSize().imePadding()) {
            composable<Home> {
                HomeScreen(onOpenTracker = { game, kind ->
                    when (kind) {
                        TrackerKind.ARMOR -> nav.navigate(ArmorTracker(game.id))
                        TrackerKind.FAIRIES -> nav.navigate(Fairies(game.id))
                        TrackerKind.SHRINES -> nav.navigate(Shrines(game.id))
                        TrackerKind.KOROKS -> nav.navigate(Koroks(game.id))
                    }
                })
            }
            composable<Fairies> { entry ->
                val game = Games.byId(entry.toRoute<Fairies>().gameId)
                val vm = viewModel(key = game.id) { FairyViewModel(FairyStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                FairyTrackerScreen(state, vm, onBack = { nav.popBackStack() })
            }
            composable<Shrines> { entry ->
                val game = Games.byId(entry.toRoute<Shrines>().gameId)
                val vm = viewModel(key = game.id) { ShrineViewModel(ShrineStorage(dataStore, game)) }
                val state by vm.state.collectAsStateWithLifecycle()
                val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
                ShrineTrackerScreen(state, loadFailed, vm, onBack = { nav.popBackStack() })
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
                )
            }
            composable<KorokDetail> { entry ->
                val route = entry.toRoute<KorokDetail>()
                val listEntry = remember(entry) { nav.getBackStackEntry<Koroks>() }
                val vm = korokViewModel(listEntry, dataStore, Games.byId(route.gameId))
                val state by vm.state.collectAsStateWithLifecycle()
                state?.let {
                    KorokDetailScreen(it, route.korokId, vm, onBack = { nav.popBackStack() })
                }
            }
            composable<ArmorTracker> { entry ->
                val game = Games.byId(entry.toRoute<ArmorTracker>().gameId)
                val vm = armorViewModel(entry, dataStore, game)
                val state by vm.state.collectAsStateWithLifecycle()
                ArmorTrackerScreen(
                    state = state,
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

/** The [KorokViewModel] for [game], owned by the Korok list's entry so the detail screen shares it. */
@Composable
private fun korokViewModel(
    owner: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
    game: Game,
): KorokViewModel = viewModel(owner, key = game.id) { KorokViewModel(KorokStorage(dataStore, game)) }
