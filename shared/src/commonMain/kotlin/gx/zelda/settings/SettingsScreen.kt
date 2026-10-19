package gx.zelda.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gx.zelda.core.AppSettings
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.core.splitSpinOffs
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SpinOffsHeading

/** App-wide settings: for now, which games the home screen and the menu show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings?, viewModel: AppSettingsViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { MenuOrBackButton(onBack) },
            )
        },
    ) { padding ->
        if (settings == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "games_header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Games",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Choose the games on the home screen and in the menu. Hiding a game keeps its progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val (mainline, spinOffs) = splitSpinOffs(Games.all) { it }
            items(mainline, key = { it.id }) { game -> GameToggle(game, settings, viewModel) }
            if (spinOffs.isNotEmpty()) {
                item(key = "_spin_offs") { SpinOffsHeading(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp)) }
                items(spinOffs, key = { it.id }) { game -> GameToggle(game, settings, viewModel) }
            }
        }
    }
}

@Composable
private fun GameToggle(game: Game, settings: AppSettings, viewModel: AppSettingsViewModel) {
    val shown = settings.isShown(game)
    val locked = shown && !settings.canHide(game)
    val details = buildList {
        add("${game.year} · ${game.platform}")
        if (game.comingSoon) add("Coming soon")
        if (locked) add("At least one game stays shown")
    }
    ListItem(
        headlineContent = { Text(game.title) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = { Switch(checked = shown, onCheckedChange = null, enabled = !locked) },
        modifier = Modifier.toggleable(
            value = shown,
            enabled = !locked,
            role = Role.Switch,
            onValueChange = { viewModel.setShown(game.id, it) },
        ),
    )
}
