package gx.zelda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import gx.zelda.botw.armor.data.ArmorStorage
import gx.zelda.botw.armor.ui.ArmorDetailScreen
import gx.zelda.botw.armor.ui.ArmorTrackerScreen
import gx.zelda.botw.armor.ui.ArmorViewModel
import gx.zelda.core.Games
import gx.zelda.core.ui.HomeScreen
import gx.zelda.core.ui.ZeldaTheme
import kotlinx.serialization.Serializable

@Serializable private data object Home
@Serializable private data object BotwArmorGraph
@Serializable private data object BotwArmorTracker
@Serializable private data class BotwArmorDetail(val pieceId: String)

@Composable
fun App(dataStore: DataStore<Preferences>) {
    ZeldaTheme {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Home) {
            composable<Home> {
                HomeScreen(onOpenTracker = { id ->
                    when (id) {
                        Games.BOTW_ARMOR -> nav.navigate(BotwArmorGraph)
                    }
                })
            }
            navigation<BotwArmorGraph>(startDestination = BotwArmorTracker) {
                composable<BotwArmorTracker> { entry ->
                    val vm = armorViewModel(nav, entry, dataStore)
                    val state by vm.state.collectAsStateWithLifecycle()
                    ArmorTrackerScreen(
                        state = state,
                        viewModel = vm,
                        onOpenPiece = { nav.navigate(BotwArmorDetail(it)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable<BotwArmorDetail> { entry ->
                    val vm = armorViewModel(nav, entry, dataStore)
                    val state by vm.state.collectAsStateWithLifecycle()
                    state?.let {
                        ArmorDetailScreen(it, entry.toRoute<BotwArmorDetail>().pieceId, vm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

/** One [ArmorViewModel] shared by every screen inside the BotW armor graph. */
@Composable
private fun armorViewModel(
    nav: NavHostController,
    entry: NavBackStackEntry,
    dataStore: DataStore<Preferences>,
): ArmorViewModel {
    val graphEntry = remember(entry) { nav.getBackStackEntry<BotwArmorGraph>() }
    return viewModel(graphEntry) {
        ArmorViewModel(ArmorStorage(dataStore))
    }
}
