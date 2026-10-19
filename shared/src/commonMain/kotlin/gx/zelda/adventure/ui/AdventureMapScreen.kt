package gx.zelda.adventure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.adventure.model.AdventureChrome
import gx.zelda.adventure.model.AdventureTab
import gx.zelda.adventure.model.MarkerKind
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.SquareFilter
import gx.zelda.adventure.model.checkCount
import gx.zelda.core.BulkAction
import gx.zelda.core.ui.BulkCompleteCheckbox
import gx.zelda.core.ui.BulkCompleteRow
import gx.zelda.core.ui.BulkCompleteState
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.groupRow
import gx.zelda.core.ui.rememberBulkCompleteState

/**
 * One Adventure Mode map on the active save file: the map itself, or the same squares as a list,
 * with filter chips over both. Tapping a square opens its sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdventureMapScreen(viewModel: AdventureMapViewModel, title: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val bulk = rememberBulkCompleteState()
    val snackbar = remember { SnackbarHostState() }
    val bulkChange by viewModel.bulkChange.collectAsStateWithLifecycle()
    var showCards by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    BulkUndoSnackbar(bulkChange, snackbar, onUndo = { viewModel.undoBulkChange(it) }, onDismiss = viewModel::dismissBulkChange)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // On a phone on its side, the Map tab gives the bars' height to the map (AdventureChrome).
        val chrome = AdventureChrome.visible(maxWidth.value, maxHeight.value, state?.mapPrefs?.tab ?: AdventureTab.MAP)
        HideSystemBars(hidden = !chrome)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (chrome) TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            state?.let { s ->
                                SaveFilePicker(
                                    current = s.saveFile,
                                    options = s.saveFiles.map { SaveFileOption(it.file, "${it.all.done} / ${it.all.total}") },
                                    onSelect = viewModel::setSaveFile,
                                )
                            }
                        }
                    },
                    navigationIcon = { MenuOrBackButton(onBack) },
                    actions = {
                        state?.let { s ->
                            TextButton(onClick = { showCards = true }) { Text("Cards") }
                            AdventureMenu(s, viewModel, onAbout = { showAbout = true })
                        }
                    },
                )
            },
        ) { padding ->
            val s = state
            if (s == null) {
                LoadingOrError(loadFailed, "This map couldn't be loaded.", padding)
                return@Scaffold
            }
            val images = remember(s.map) { SquareImages(s.map) }
            Column(Modifier.fillMaxSize().padding(padding)) {
                val tabs = AdventureTab.entries
                if (chrome) {
                    PrimaryTabRow(selectedTabIndex = tabs.indexOf(s.mapPrefs.tab)) {
                        tabs.forEach { tab ->
                            Tab(selected = s.mapPrefs.tab == tab, onClick = { viewModel.setTab(tab) }, text = { Text(tab.label) })
                        }
                    }
                    FilterChips(s, viewModel)
                } else {
                    // The bars are hidden: the filter row carries what they had.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MenuOrBackButton(onBack)
                        FilterChips(s, viewModel, Modifier.weight(1f))
                        if (s.saveFiles.size > 1) {
                            SaveFilePicker(
                                current = s.saveFile,
                                options = s.saveFiles.map { SaveFileOption(it.file, "${it.all.done} / ${it.all.total}") },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                        TextButton(onClick = { showCards = true }) { Text("Cards") }
                        TextButton(onClick = { viewModel.setTab(AdventureTab.LIST) }) { Text("List") }
                        AdventureMenu(s, viewModel, onAbout = { showAbout = true })
                    }
                }
                when (s.mapPrefs.tab) {
                    AdventureTab.MAP -> MapPane(s, images, selected, viewModel, Modifier.weight(1f).fillMaxWidth())
                    AdventureTab.LIST -> SquareList(s, viewModel, bulk, Modifier.weight(1f).fillMaxWidth())
                }
            }
            selected?.let(s.map::square)?.let { square ->
                SquareSheet(s, square, images, viewModel, onDismiss = { viewModel.select(null) })
            }
            if (showCards) ItemCardsSheet(s, viewModel, onDismiss = { showCards = false })
            if (showAbout) AboutSheet(onDismiss = { showAbout = false })
            BulkConfirmDialog(bulk, s.saveFile.label)
        }
    }
}

@Composable
private fun AdventureMenu(state: AdventureUiState, viewModel: AdventureMapViewModel, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = {
                Column {
                    Text("Spoiler-safe")
                    Text(
                        "Hide squares you haven't reached, and search spots until you use the Compass",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = { Checkbox(checked = state.prefs.spoilerSafe, onCheckedChange = null) },
            onClick = {
                viewModel.setSpoilerSafe(!state.prefs.spoilerSafe)
                open = false
            },
        )
        DropdownMenuItem(text = { Text("About this map") }, onClick = { onAbout(); open = false })
    }
}

@Composable
private fun FilterChips(state: AdventureUiState, viewModel: AdventureMapViewModel, modifier: Modifier = Modifier.fillMaxWidth()) {
    var warriorMenu by remember { mutableStateOf(false) }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            val warrior = state.mapPrefs.warrior
            FilterChip(
                selected = warrior != null,
                onClick = { warriorMenu = true },
                label = { Text(warrior?.let { "For ${state.map.warriorName(it)}" } ?: "Any warrior") },
            )
            DropdownMenu(expanded = warriorMenu, onDismissRequest = { warriorMenu = false }) {
                DropdownMenuItem(text = { Text("Any warrior") }, onClick = { viewModel.setWarrior(null); warriorMenu = false })
                state.warriors.forEach { w ->
                    DropdownMenuItem(text = { Text(w.name) }, onClick = { viewModel.setWarrior(w.id); warriorMenu = false })
                }
            }
        }
        SquareFilter.entries.forEach { f ->
            val on = f in state.mapPrefs.filters
            FilterChip(selected = on, onClick = { viewModel.setFilter(f, !on) }, label = { Text(f.label) })
        }
    }
}

@Composable
private fun MapPane(state: AdventureUiState, images: SquareImages, selected: String?, viewModel: AdventureMapViewModel, modifier: Modifier) {
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    Box(modifier) {
        GridMapView(
            state = state,
            images = images,
            viewport = viewport,
            selected = selected,
            onViewport = viewModel::setViewport,
            onTapSquare = { viewModel.select(it.id) },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.filtered) {
            Text(
                "${state.matching.size} of ${state.map.squares.size} squares match",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val v = viewport
            SmallFloatingActionButton(onClick = { v?.let { viewModel.setViewport(it.zoomBy(1.6f)) } }, modifier = Modifier.semantics { contentDescription = "Zoom in" }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
            SmallFloatingActionButton(onClick = { v?.let { viewModel.setViewport(it.zoomBy(1 / 1.6f)) } }, modifier = Modifier.semantics { contentDescription = "Zoom out" }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            SmallFloatingActionButton(onClick = { v?.let { viewModel.setViewport(it.fitted()) } }, modifier = Modifier.semantics { contentDescription = "Show the whole map" }) {
                Text("Fit", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SquareList(state: AdventureUiState, viewModel: AdventureMapViewModel, bulk: BulkCompleteState, modifier: Modifier) {
    LazyColumn(modifier) {
        if (state.session.query.isBlank()) item(key = "_summary") { SummaryCard(state) }
        item(key = "_search") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(state.session.query, viewModel::setQuery, "Search squares, rewards, warriors, keeps or cards")
                if (state.groups.isNotEmpty()) {
                    BulkCompleteRow(state.bulkScope(), bulk, onApply = { viewModel.setAllDone(null, it == BulkAction.MARK) })
                }
            }
        }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No square matches these filters.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { viewModel.clearFilters(); viewModel.setQuery("") }) { Text("Clear filters") }
                }
            }
        }
        for ((region, squares) in state.groups) {
            val tally = state.regionTally(region)
            item(key = "h_${region.id}", contentType = "header") {
                RegionHeader("${region.name} squares", found = null, done = tally.done, total = tally.total, suffix = "", leading = {
                    BulkCompleteCheckbox(state.bulkScope(region.id), bulk, onApply = { viewModel.setAllDone(region.id, it == BulkAction.MARK) })
                })
            }
            itemsIndexed(squares, key = { _, sq -> sq.id }, contentType = { _, _ -> "square" }) { i, square ->
                val last = i == squares.lastIndex
                SquareRow(state, square, onOpen = { viewModel.select(square.id) }, showDivider = !last, modifier = Modifier.groupRow(last))
            }
        }
    }
}

@Composable
private fun SummaryCard(state: AdventureUiState) {
    val t = state.totals
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t.all.done} / ${t.all.total}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("Everything", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { if (t.all.total == 0) 0f else t.all.done.toFloat() / t.all.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Cleared ${t.cleared.done}/${t.cleared.total} · A-rank ${t.aRanked.done} · Complete ${t.complete.done}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val hc = t.rewards(RewardType.HEART_CONTAINER)
            val poh = t.rewards(RewardType.HEART_PIECE)
            Text(
                "Heart Containers ${hc.done}/${hc.total} · Pieces of Heart ${poh.done}/${poh.total} · Gold Skulltulas ${t.skulltulas.done}/${t.skulltulas.total}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val w = t.rewards(RewardType.WEAPON)
            Text("Weapons ${w.done}/${w.total} · Searches ${t.searches.done}/${t.searches.total}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SquareRow(state: AdventureUiState, square: Square, onOpen: () -> Unit, showDivider: Boolean, modifier: Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val hidden = state.spoilers.isHidden(square)
    Column(modifier) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open", onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOfNotNull(square.label, square.title).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                Text(state.progress.status(square).label, style = MaterialTheme.typography.labelMedium, color = dim, modifier = Modifier.weight(1f))
                Text("${state.progress.doneCount(square)} / ${square.checkCount}", style = MaterialTheme.typography.labelMedium, color = dim)
            }
            if (hidden) {
                Text("Unexplored square", style = MaterialTheme.typography.bodyMedium, color = dim)
            } else {
                Text(square.mission, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val look = state.look(square, selected = false)
                if (look.markers.isNotEmpty()) {
                    Text(
                        look.markers.joinToString(" · ") { "${it.count} ${markerName(it.kind, it.count)}" } + " left",
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                    )
                }
            }
        }
        if (showDivider) RowDivider()
    }
}

private fun markerName(kind: MarkerKind, n: Int): String = when (kind) {
    MarkerKind.HEART_CONTAINER -> if (n == 1) "Heart Container" else "Heart Containers"
    MarkerKind.HEART_PIECE -> if (n == 1) "Piece of Heart" else "Pieces of Heart"
    MarkerKind.SKULLTULA -> if (n == 1) "Skulltula" else "Skulltulas"
    MarkerKind.SEARCH -> if (n == 1) "search" else "searches"
}

/** The map's item cards: what each does, where it comes from, and whether this save file has it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCardsSheet(state: AdventureUiState, viewModel: AdventureMapViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Item cards", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "A card counts as found once a square that gives it is cleared: after that, DE's Item Card Shop sells it. Tap one to set it yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.map.cards, key = { it.id }) { card ->
                val has = state.inventory.has(card.id)
                val auto = state.inventory.isAutomatic(card.id)
                val from = state.map.sourcesOf(card.id).map { it.label }
                val open = state.map.squares.sumOf { sq -> sq.searches.count { it.card == card.id && !state.progress.isDone(it.id) && !state.spoilers.secretsHidden(sq) } }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Automatic → set by hand to the other value → back to automatic.
                        .clickable(onClickLabel = "Change") { viewModel.setCard(card.id, if (auto) !has else null) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    CardIcon(card.id, 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                        Text(card.text, style = MaterialTheme.typography.bodySmall)
                        val details = listOfNotNull(
                            from.takeIf { it.isNotEmpty() }?.let { "From ${it.joinToString()}" },
                            card.shopPrice?.let { "Shop ${thousandsRupees(it)}" },
                            "$open search${if (open == 1) "" else "es"} left".takeIf { open > 0 },
                        )
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (has) "Found" else "Not yet", style = MaterialTheme.typography.labelLarge, color = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (auto) "automatic" else "set by you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun thousandsRupees(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed() + " rupees"

/** Where the map's data and art come from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About this map", style = MaterialTheme.typography.titleLarge)
            Text(
                "Squares, rewards and search spots: the Hyrule Warriors Helper by Fabian-F (DE data), checked against SBAllen's GameFAQs guide, " +
                    "Nintendo Life and RPG Site. Item card text: Zelda Wiki. Map art: the Helper's pictures of the in-game map. " +
                    "Hyrule Warriors is © Nintendo and Koei Tecmo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Labels follow Prima's guide and Zelda Wiki (a row letter, then the column); SBAllen's guide writes the column letter first. " +
                    "A-rank limits are the guides' findings: the game doesn't show them. " +
                    "A square can be challenged once a neighbour across a straight edge is cleared; in the game some " +
                    "need a B or A rank, which isn't recorded, so any clear counts here. Dim squares can't be challenged yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
