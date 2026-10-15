package gx.zelda.koroks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.DiscoveryState
import gx.zelda.core.ui.CardInset
import gx.zelda.core.ui.FoundToggle
import gx.zelda.core.ui.HiddenRow
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.groupRow
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokPuzzleType

/** Korok Seeds by tower region, which are collected on the active save file, and Hestu's upgrades. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorokTrackerScreen(
    state: KorokUiState?,
    loadFailed: Boolean,
    viewModel: KorokViewModel,
    onOpenKorok: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Korok Seeds", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map {
                                    SaveFileOption(it.file, "${it.collected} / ${state.catalog.koroks.size} seeds")
                                },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state != null) KorokMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The Korok list couldn't be loaded.", padding)
            return@Scaffold
        }
        KorokList(state, viewModel, onOpenKorok, padding)
    }
}

@Composable
private fun KorokMenu(state: KorokUiState, viewModel: KorokViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide undiscovered Koroks") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUndiscovered, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUndiscovered(!state.prefs.hideUndiscovered)
                open = false
            },
        )
    }
}

@Composable
private fun KorokList(
    state: KorokUiState,
    viewModel: KorokViewModel,
    onOpenKorok: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val progress = state.progress
    var pickingTypes by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        // While searching, the Hestu card would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_hestu") {
                HestuCard(
                    state.hestu,
                    onBoughtChange = viewModel::setBought,
                    modifier = Modifier.padding(horizontal = CardInset).padding(top = 8.dp),
                )
            }
        }
        item(key = "_filters") { KorokFilterBar(state, viewModel, onPickTypes = { pickingTypes = true }) }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No Koroks match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, koroks) in state.groups) {
            // The header counts the whole region, not just the Koroks the filters leave showing.
            val count = state.regionCounts.getValue(region)
            item(key = "h_$region", contentType = "header") {
                RegionHeader(region, found = count.found, done = count.collected, total = count.total)
            }
            itemsIndexed(koroks, key = { _, korok -> korok.id }, contentType = { _, _ -> "korok" }) { i, korok ->
                val last = i == koroks.lastIndex
                if (state.isHidden(korok)) {
                    HiddenRow(
                        "Undiscovered Korok",
                        onReveal = { viewModel.reveal(korok.id) },
                        onFound = { viewModel.setState(korok.id, DiscoveryState.FOUND) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                } else {
                    KorokRow(
                        korok,
                        type = state.data.typeOf(korok),
                        korokState = progress.state(korok.id),
                        onStateChange = { viewModel.setState(korok.id, it) },
                        onOpen = { onOpenKorok(korok.id) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                }
            }
        }
    }

    if (pickingTypes) {
        PuzzleTypeSheet(state, viewModel, onDismiss = { pickingTypes = false })
    }
}

@Composable
private fun KorokFilterBar(state: KorokUiState, viewModel: KorokViewModel, onPickTypes: () -> Unit) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search place or puzzle type")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.incompleteOnly,
                onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
                label = { Text("Incomplete") },
            )
            FilterChip(
                selected = filters.types.isNotEmpty(),
                onClick = onPickTypes,
                label = { Text(typeChipLabel(state)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
        }
        Text(
            "${state.collected} / ${state.catalog.koroks.size} seeds collected · ${state.found} found",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun typeChipLabel(state: KorokUiState): String {
    val types = state.filters.types
    return when (types.size) {
        0 -> "Puzzle type"
        1 -> state.data.types.types.first { it.id in types }.name
        else -> "${types.size} puzzle types"
    }
}

/** Multi-select puzzle types, grouped as the taxonomy groups them, with how many seeds each has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PuzzleTypeSheet(state: KorokUiState, viewModel: KorokViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val counts = remember(state.catalog) { state.catalog.koroks.groupingBy { it.puzzleType }.eachCount() }
    val byGroup = remember(state.data) { state.data.types.types.groupBy { it.group } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Puzzle types", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clearTypes, enabled = state.filters.types.isNotEmpty()) { Text("Clear") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            for (group in state.data.types.groups) {
                val types = byGroup[group].orEmpty()
                if (types.isEmpty()) continue
                item(key = "g_$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(types, key = { it.id }) { type ->
                    TypeOption(
                        type,
                        count = counts[type.id] ?: 0,
                        selected = type.id in state.filters.types,
                        onToggle = { viewModel.toggleType(type.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeOption(type: KorokPuzzleType, count: Int, selected: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 8.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(type.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

@Composable
private fun KorokRow(
    korok: Korok,
    type: KorokPuzzleType,
    korokState: DiscoveryState,
    onStateChange: (DiscoveryState) -> Unit,
    onOpen: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val collected = korokState == DiscoveryState.COMPLETED
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Unticking a collected seed leaves its Korok found.
                .toggleable(value = collected, role = Role.Checkbox) {
                    onStateChange(if (it) DiscoveryState.COMPLETED else DiscoveryState.FOUND)
                }
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Checkbox(checked = collected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    type.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (collected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                // objmap's own name says what a one-off puzzle actually is.
                if (type.id == "other") {
                    Text(korok.internalType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Near ${korok.nearestPlace} · ${korok.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FoundToggle(korokState, onStateChange)
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "How to solve ${korok.id}")
            }
        }
        if (showDivider) RowDivider()
    }
}
