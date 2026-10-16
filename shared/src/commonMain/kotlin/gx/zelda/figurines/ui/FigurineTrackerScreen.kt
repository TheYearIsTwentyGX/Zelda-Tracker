package gx.zelda.figurines.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.CardInset
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import gx.zelda.figurines.model.Figurine
import gx.zelda.figurines.model.FigurineProgress

/**
 * Carlov's figurines on the active save file, grouped by the story checkpoint that adds them to
 * his machine. The card on top tracks the save file's checkpoint and Mysterious Shells and works
 * out the odds of a new figurine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FigurineTrackerScreen(
    state: FigurineUiState?,
    loadFailed: Boolean,
    viewModel: FigurineViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Carlov's Figurines", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it.file, "${it.owned} / ${it.total} owned") },
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
                    if (state != null) FigurineMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The figurine list couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            FigurineFilterBar(state, viewModel)
            FigurineList(state, viewModel)
        }
    }
}

@Composable
private fun FigurineMenu(state: FigurineUiState, viewModel: FigurineViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide figurines not yet available") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUnavailable, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUnavailable(!state.prefs.hideUnavailable)
                open = false
            },
        )
    }
}

@Composable
private fun FigurineFilterBar(state: FigurineUiState, viewModel: FigurineViewModel) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search name, number or who's pictured")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (show in FigurineShow.entries.drop(1)) {
                FilterChip(
                    selected = filters.show == show,
                    onClick = { viewModel.setShow(if (filters.show == show) FigurineShow.ALL else show) },
                    label = { Text(show.label) },
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (category in state.catalog.categories) {
                FilterChip(
                    selected = category.id in filters.categories,
                    onClick = { viewModel.toggleCategory(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
    }
}

@Composable
private fun FigurineList(state: FigurineUiState, viewModel: FigurineViewModel) {
    // Which rows show their description; not saved.
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        // While searching, the card would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_collection") { CollectionCard(state, viewModel) }
        }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No figurines match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for (group in state.groups) {
            item(key = "h_${group.checkpoint.id}", contentType = "header") { CheckpointHeader(group) }
            itemsIndexed(group.figurines, key = { _, f -> f.number }, contentType = { _, _ -> "figurine" }) { i, figurine ->
                val last = i == group.figurines.lastIndex
                if (state.isHidden(figurine)) {
                    HiddenFigurineRow(
                        figurine,
                        onReveal = { viewModel.reveal(figurine.number) },
                        onOwned = { viewModel.setOwned(figurine.number, true) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                } else {
                    FigurineRow(
                        state,
                        figurine,
                        expanded = figurine.number in expanded,
                        onExpand = { expanded = if (figurine.number in expanded) expanded - figurine.number else expanded + figurine.number },
                        viewModel = viewModel,
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                }
            }
        }
    }
}

/** Owned of 136 and by category, the save file's story checkpoint and shells, and the odds. */
@Composable
private fun CollectionCard(state: FigurineUiState, viewModel: FigurineViewModel) {
    val tally = state.tally
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset).padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tally.owned} / ${tally.total} owned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${tally.available} available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(progress = { tally.owned.toFloat() / tally.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Text(
                tally.categories.joinToString(" · ") { "${it.category.name} ${it.owned} / ${it.total}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckpointPicker(state, viewModel::setCheckpoint)
            ShellCounter(state.progress.shells, viewModel::setShells)
            for (line in oddsLines(tally)) {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Herb, outside Mama's Cafe, opens his house at 130 (a Piece of Heart inside); all 136 earn the Carlov Medal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Story: Fortress of Winds ▾": the furthest checkpoint the save file has reached. */
@Composable
private fun CheckpointPicker(state: FigurineUiState, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "Change story checkpoint", role = Role.DropdownList) { open = true }
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Story: ${state.reached.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(state.reached.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (checkpoint in state.checkpointChoices) {
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(checkpoint.name)
                            Text(checkpoint.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(checkpoint.id); open = false },
                    trailingIcon = {
                        if (checkpoint.id == state.reached.id) Icon(Icons.Filled.Check, contentDescription = "Current checkpoint")
                    },
                )
            }
        }
    }
}

/** Mysterious Shells held: type a number or step it by one, 0–999. */
@Composable
private fun ShellCounter(shells: Int, onChange: (Int) -> Unit) {
    // Restarts from the stored count whenever it changes, e.g. on switching save file.
    var text by remember(shells) { mutableStateOf(shells.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mysterious Shells", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(shells - 1) }, enabled = shells > 0) {
            Text("−", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "One fewer shell" })
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val digits = new.filter(Char::isDigit).take(3)
                text = digits
                digits.toIntOrNull()?.let(onChange)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(80.dp).semantics { contentDescription = "Mysterious Shells held" },
        )
        FilledTonalIconButton(onClick = { onChange(shells + 1) }, enabled = shells < FigurineProgress.MAX_SHELLS) {
            Text("+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "One more shell" })
        }
    }
}

/** The top of a checkpoint's card: its name and what reaches it, and owned / total. */
@Composable
private fun CheckpointHeader(group: FigurineGroup) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (group.hidden) "Later in the story" else group.checkpoint.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!group.hidden) {
                    Text(
                        group.checkpoint.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (group.owned == group.total) {
                Tag(
                    "${group.owned} / ${group.total}",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Tag("${group.owned} / ${group.total}")
            }
        }
    }
}

@Composable
private fun FigurineRow(
    state: FigurineUiState,
    figurine: Figurine,
    expanded: Boolean,
    onExpand: () -> Unit,
    viewModel: FigurineViewModel,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val owned = state.progress.isOwned(figurine.number)
    val available = state.isAvailable(figurine)
    val category = state.catalog.category(figurine.category)
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = owned, role = Role.Checkbox) { viewModel.setOwned(figurine.number, it) }
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Checkbox(checked = owned, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(figurine.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        figurine.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (owned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (category != null) Tag(category.name)
                    if (figurine.requirement != null && !owned) Tag("Side task")
                    if (!available) Tag("Not yet available")
                }
            }
            IconButton(onClick = onExpand) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide ${figurine.name}'s details" else "Show ${figurine.name}'s details",
                )
            }
        }
        if (expanded) FigurineDetails(state, figurine, viewModel)
        if (showDivider) RowDivider()
    }
}

/** The gallery's text, who's pictured, and the side task (with a tick for it) if there is one. */
@Composable
private fun FigurineDetails(state: FigurineUiState, figurine: Figurine, viewModel: FigurineViewModel) {
    Column(Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(figurine.description, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        if (figurine.pictured.isNotEmpty()) {
            Text("Pictured: ${figurine.pictured}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val requirement = figurine.requirement
        if (requirement != null) {
            val met = figurine.number in state.progress.requirementsMet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .toggleable(value = met, role = Role.Checkbox) { viewModel.setRequirementMet(figurine.number, it) },
            ) {
                Checkbox(checked = met, onCheckedChange = null)
                Text("Side task done: $requirement", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A figurine not yet available, with spoilers hidden: tap to see it, or tick it as owned. */
@Composable
private fun HiddenFigurineRow(
    figurine: Figurine,
    onReveal: () -> Unit,
    onOwned: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Reveal", onClick = onReveal)
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Figurine ${figurine.label}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(
                checked = false,
                onCheckedChange = { onOwned() },
                modifier = Modifier.semantics { contentDescription = "Mark ${figurine.label} owned" },
            )
        }
        if (showDivider) RowDivider()
    }
}
