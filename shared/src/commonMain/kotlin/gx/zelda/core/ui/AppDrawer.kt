package gx.zelda.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import gx.zelda.core.Game
import gx.zelda.core.GameMenuSection
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind

/** What the menu highlights: the screen open now, or the tracker it was opened from. */
sealed interface MenuSelection {
    data object Home : MenuSelection
    data object Settings : MenuSelection
    /** [dataFile] tells a game's checklists (or Adventure maps) apart; it's null for every other kind. */
    data class Tracker(val gameId: String, val kind: TrackerKind, val dataFile: String? = null) : MenuSelection
    data object None : MenuSelection
}

/**
 * What a screen's top bar needs from the app's menu. Provided around the NavHost by `App`; null
 * outside it (tests, previews), where screens keep their Back button.
 */
class AppMenu(
    val open: () -> Unit,
    /**
     * True when [entry] sits directly on the home screen, so its top bar shows the menu button
     * rather than Back; null once it has left the back stack.
     */
    val isTopLevel: (entry: NavBackStackEntry) -> Boolean?,
)

val LocalAppMenu = staticCompositionLocalOf<AppMenu?> { null }

/**
 * A tracker screen's navigation icon: the menu button when it was opened from the home screen or
 * the menu, otherwise Back to the screen that opened it (the Overview, the map, a quest's page).
 */
@Composable
fun MenuOrBackButton(onBack: () -> Unit) {
    val menu = LocalAppMenu.current
    // Inside the NavHost each screen's ViewModelStoreOwner is its back stack entry.
    val entry = LocalViewModelStoreOwner.current as? NavBackStackEntry
    val topLevel = if (menu != null && entry != null) menu.isTopLevel(entry) else false
    // Keep the last answer while the screen animates out after leaving the back stack.
    var showMenu by remember { mutableStateOf(topLevel == true) }
    if (topLevel != null) showMenu = topLevel
    if (showMenu && menu != null) {
        MenuButton(menu.open)
    } else {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
fun MenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
    }
}

/**
 * The navigation drawer: Home, then each shown game in release order as a section that opens to
 * list its trackers (a coming-soon game is a single row), the spin-offs after a heading of their
 * own, then Settings.
 */
@Composable
fun AppDrawerSheet(
    drawerState: DrawerState,
    sections: List<GameMenuSection>,
    selection: MenuSelection,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onToggleGame: (gameId: String, expanded: Boolean) -> Unit,
    onOpenTracker: (Game, Tracker) -> Unit,
) {
    ModalDrawerSheet(drawerState) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            item(key = "title") {
                Text(
                    "ZeldaGX",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
                )
            }
            item(key = "home") {
                NavigationDrawerItem(
                    label = { Text("Home") },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    selected = selection == MenuSelection.Home,
                    onClick = onHome,
                )
            }
            item(key = "games_divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            val firstSpinOff = sections.firstOrNull { it.game.spinOff }
            sections.forEach { section ->
                if (section == firstSpinOff) {
                    item(key = "_spin_offs") { SpinOffsHeading(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
                item(key = "game_${section.game.id}") {
                    GameHeader(section, onToggle = { onToggleGame(section.game.id, !section.expanded) })
                }
                items(section.rows, key = { "tracker_${section.game.id}_${it.kind}_${it.dataFile}" }) { tracker ->
                    NavigationDrawerItem(
                        label = { Text(tracker.title) },
                        selected = selection == MenuSelection.Tracker(section.game.id, tracker.kind, tracker.dataFile),
                        onClick = { onOpenTracker(section.game, tracker) },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
            item(key = "settings_divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item(key = "settings") {
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = selection == MenuSelection.Settings,
                    onClick = onSettings,
                )
            }
        }
    }
}

/** The heading the spin-offs are listed under, after the mainline games. */
@Composable
fun SpinOffsHeading(modifier: Modifier = Modifier) {
    Text(
        "Spin-offs",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** A game's title and year; tapping it opens or closes its trackers. A coming-soon game doesn't open. */
@Composable
private fun GameHeader(section: GameMenuSection, onToggle: () -> Unit) {
    val game = section.game
    val toggle = if (section.expandable) {
        Modifier.clickable(
            onClickLabel = if (section.expanded) "Collapse" else "Expand",
            role = Role.Button,
            onClick = onToggle,
        ).semantics { stateDescription = if (section.expanded) "Expanded" else "Collapsed" }
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .then(toggle)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        AccentDot(game, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                game.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (section.expandable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (section.expandable) "${game.year} · ${game.platform}" else "${game.year} · Coming soon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (section.expandable) {
            Icon(
                if (section.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A dot in [game]'s accent colour, or a gap the same size when it has none, so titles line up. */
@Composable
fun AccentDot(game: Game, modifier: Modifier = Modifier) {
    val accent = gameAccent(game)
    Box(
        modifier
            .size(10.dp)
            .then(if (accent == null) Modifier else Modifier.background(accent, CircleShape)),
    )
}
