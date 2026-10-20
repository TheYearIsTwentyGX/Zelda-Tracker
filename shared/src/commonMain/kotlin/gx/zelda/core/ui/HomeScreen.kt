package gx.zelda.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gx.zelda.core.Game
import gx.zelda.core.Tracker
import gx.zelda.core.splitSpinOffs
import gx.zelda.theme.titleStyle

/**
 * The landing page: a card per game the user shows, in release order, the same games and order as
 * the menu. [games] is null until the settings have loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    games: List<Game>?,
    onOpenTracker: (Game, Tracker) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("ZeldaGX", fontWeight = FontWeight.Bold) },
                navigationIcon = { MenuButton(onOpenMenu) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val (mainline, spinOffs) = splitSpinOffs(games.orEmpty()) { it }
            items(mainline, key = { it.id }) { game -> GameCard(game, onOpenTracker) }
            if (spinOffs.isNotEmpty()) {
                item(key = "_spin_offs") { SpinOffsHeading(Modifier.padding(top = 8.dp)) }
                items(spinOffs, key = { it.id }) { game -> GameCard(game, onOpenTracker) }
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, onOpenTracker: (Game, Tracker) -> Unit) {
    val available = game.trackers.isNotEmpty()
    // The game's own accent and lettering preview its theme; the card itself stays neutral.
    val accent = gameAccent(game)
    val accentBar = if (accent == null) Modifier else Modifier.drawBehind { drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(accentBar.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.title, style = gameDisplayFont(game).titleStyle(), modifier = Modifier.weight(1f))
                Text("${game.year}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (available) game.platform else "${game.platform} · Coming soon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (tools, trackers) = game.trackers.partition { it.isTool }
            (trackers + tools).forEach { tracker ->
                if (tracker == tools.firstOrNull()) ToolsHeading(Modifier.padding(top = 4.dp))
                Card(
                    onClick = { onOpenTracker(game, tracker) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tracker.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                tracker.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
