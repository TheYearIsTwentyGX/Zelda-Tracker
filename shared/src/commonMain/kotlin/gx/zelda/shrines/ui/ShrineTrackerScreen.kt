package gx.zelda.shrines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.CardInset
import gx.zelda.core.ui.FoundToggle
import gx.zelda.core.ui.HiddenRow
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineState
import gx.zelda.shrines.model.TrialType

/** Shrines by tower region, which are complete on the active save file, and the Spirit Orb planner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrineTrackerScreen(
    state: ShrineUiState?,
    loadFailed: Boolean,
    viewModel: ShrineViewModel,
    onScanMap: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shrines", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map {
                                    SaveFileOption(it.file, "${it.completed} / ${state.catalog.base.size} shrines complete")
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
                    if (state != null) ShrineMenu(state, viewModel, onScanMap)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The shrine list couldn't be loaded.", padding)
            return@Scaffold
        }
        ShrineList(state, viewModel, padding)
    }
}

@Composable
private fun ShrineMenu(state: ShrineUiState, viewModel: ShrineViewModel, onScanMap: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide undiscovered shrines") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUndiscovered, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUndiscovered(!state.prefs.hideUndiscovered)
                open = false
            },
        )
        DropdownMenuItem(
            text = { Text("Scan map screenshots") },
            onClick = {
                open = false
                onScanMap()
            },
        )
    }
}

@Composable
private fun ShrineList(state: ShrineUiState, viewModel: ShrineViewModel, contentPadding: PaddingValues) {
    val progress = state.progress
    val shownByRegion = state.catalog.byRegion(state.shown).toMap()
    val groups = state.catalog.byRegion(state.matching)

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        // While searching, the orb card would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_orbs") {
                SpiritOrbCard(
                    state.orbs,
                    onBoughtChange = viewModel::setBought,
                    modifier = Modifier.padding(horizontal = CardInset).padding(top = 8.dp),
                )
            }
        }
        item(key = "_filters") { ShrineFilterBar(state, viewModel) }
        if (groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No shrines match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, shrines) in groups) {
            // The header counts the whole region, not just the shrines the filters leave showing.
            val inRegion = shownByRegion[region].orEmpty()
            item(key = "h_$region") {
                RegionHeader(
                    region,
                    found = inRegion.count { progress.isFound(it.id) },
                    done = inRegion.count { it.id in progress.completed },
                    total = inRegion.size,
                )
            }
            itemsIndexed(shrines, key = { _, shrine -> shrine.id }) { i, shrine ->
                val last = i == shrines.lastIndex
                val modifier = Modifier.groupRow(last)
                if (state.isHidden(shrine)) {
                    HiddenRow(
                        "Undiscovered shrine",
                        onReveal = { viewModel.reveal(shrine.id) },
                        onFound = { viewModel.setState(shrine.id, ShrineState.FOUND) },
                        showDivider = !last,
                        modifier = modifier,
                    )
                } else {
                    ShrineRow(
                        shrine,
                        shrineState = progress.state(shrine.id),
                        onStateChange = { viewModel.setState(shrine.id, it) },
                        showDivider = !last,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShrineFilterBar(state: ShrineUiState, viewModel: ShrineViewModel) {
    val filters = state.filters
    val progress = state.progress
    val catalog = state.catalog
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search shrine, trial or quest")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.incompleteOnly,
                onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
                label = { Text("Incomplete") },
            )
            FilterChip(
                selected = filters.questsOnly,
                onClick = { viewModel.setQuestsOnly(!filters.questsOnly) },
                label = { Text("Shrine Quests") },
            )
            if (catalog.dlc.isNotEmpty()) {
                FilterChip(
                    selected = state.prefs.showDlc,
                    onClick = { viewModel.setShowDlc(!state.prefs.showDlc) },
                    label = { Text("DLC") },
                )
            }
        }
        Text(
            buildString {
                append("${catalog.base.count { it.id in progress.completed }} / ${catalog.base.size} shrines complete")
                append(" · ${catalog.base.count { progress.isFound(it.id) }} found")
                if (state.prefs.showDlc && catalog.dlc.isNotEmpty()) {
                    append(" · DLC ${catalog.dlc.count { it.id in progress.completed }} / ${catalog.dlc.size}")
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShrineRow(
    shrine: Shrine,
    shrineState: ShrineState,
    onStateChange: (ShrineState) -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val completed = shrineState == ShrineState.COMPLETED
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Unticking a completed shrine leaves it found.
                .toggleable(value = completed, role = Role.Checkbox) {
                    onStateChange(if (it) ShrineState.COMPLETED else ShrineState.FOUND)
                }
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Checkbox(checked = completed, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${shrine.name} Shrine",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        shrine.trial,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    typeTag(shrine)?.let { Tag(it) }
                    if (shrine.dlc) Tag("DLC")
                }
                shrine.quest?.let {
                    Text(
                        "Shrine Quest: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    shrine.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FoundToggle(shrineState, onStateChange)
        }
        if (showDivider) RowDivider()
    }
}

/**
 * Puzzles go untagged, and most trial names already say "Test of Strength" or "Blessing";
 * tag a fight or a blessing only when its name doesn't.
 */
private fun typeTag(shrine: Shrine): String? = when (shrine.type) {
    TrialType.PUZZLE -> null
    TrialType.COMBAT -> shrine.type.label.takeUnless { "Test of Strength" in shrine.trial }
    TrialType.BLESSING -> shrine.type.label.takeUnless { "Blessing" in shrine.trial }
}
