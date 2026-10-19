package gx.zelda.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import gx.zelda.core.ThemeMode
import gx.zelda.core.ui.AccentDot
import gx.zelda.core.ui.MenuOrBackButton

/** App-wide settings: light or dark, game themes, and which games the home screen and the menu show. */
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
            item(key = "appearance") { AppearanceSettings(settings, viewModel) }
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
            items(Games.all, key = { it.id }) { game -> GameToggle(game, settings, viewModel) }
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
        leadingContent = { AccentDot(game) },
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

/** Light, dark or the system's, and whether each game's screens wear its own theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(settings: AppSettings, viewModel: AppSettingsViewModel) {
    Column {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label) }
                }
            }
        }
        ListItem(
            headlineContent = { Text("Game themes") },
            supportingContent = {
                Text("Screens inside a game take on its colours and lettering. Off keeps every screen in the app's own look.")
            },
            trailingContent = { Switch(checked = settings.gameThemes, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = settings.gameThemes,
                role = Role.Switch,
                onValueChange = { viewModel.setGameThemes(it) },
            ),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
    }
}

