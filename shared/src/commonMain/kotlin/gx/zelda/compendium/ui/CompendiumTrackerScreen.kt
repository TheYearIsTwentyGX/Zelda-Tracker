package gx.zelda.compendium.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.compendium.logic.CompendiumSection
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.compendium.model.EntrySource
import gx.zelda.compendium.model.compendiumNumber
import gx.zelda.core.BulkAction
import gx.zelda.core.ui.BulkCompleteCheckbox
import gx.zelda.core.ui.BulkCompleteRow
import gx.zelda.core.ui.BulkCompleteState
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.rememberBulkCompleteState

/** Hyrule Compendium: a tab per category, each entry tickable as photographed on the active save file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompendiumTrackerScreen(
    state: CompendiumUiState?,
    loadFailed: Boolean,
    viewModel: CompendiumViewModel,
    onOpenEntry: (String) -> Unit,
    onBack: () -> Unit,
) {
    val bulk = rememberBulkCompleteState()
    val snackbar = remember { SnackbarHostState() }
    val bulkChange by viewModel.bulkChange.collectAsStateWithLifecycle()
    BulkUndoSnackbar(bulkChange, snackbar, onUndo = { viewModel.undoBulkChange(it) }, onDismiss = viewModel::dismissBulkChange)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hyrule Compendium", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map {
                                    SaveFileOption(it.file, "${it.photographed} / ${it.total} photographed")
                                },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = { MenuOrBackButton(onBack) },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The Compendium couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoryTabs(state, viewModel)
            EntryList(state, viewModel, bulk, onOpenEntry)
        }
        BulkConfirmDialog(bulk, state.saveFile.label)
    }
}

/** All, then one tab per category, each with its photographed count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTabs(state: CompendiumUiState, viewModel: CompendiumViewModel) {
    val tabs = listOf<CompendiumCategory?>(null) + CompendiumCategory.entries
    PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(state.category), edgePadding = 8.dp) {
        tabs.forEach { category ->
            val (done, total) = if (category == null) {
                state.photographedCount to state.entries.size
            } else {
                state.progress.first { it.category == category }.let { it.photographed to it.total }
            }
            Tab(
                selected = state.category == category,
                onClick = { viewModel.setCategory(category) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(category?.label ?: "All", maxLines = 1)
                        Text(
                            "$done / $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (done == total) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EntryList(state: CompendiumUiState, viewModel: CompendiumViewModel, bulk: BulkCompleteState, onOpenEntry: (String) -> Unit) {
    val masterMode = state.saveFile.masterMode
    val sections = state.sections
    // A new tab or save file starts at the top rather than mid-way down the previous list.
    val listState = rememberLazyListState()
    LaunchedEffect(state.category, state.saveFile) { listState.scrollToItem(0) }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "_filters") { Filters(state, viewModel, bulk) }
        if (state.category == null && state.query.isBlank() && !state.missingOnly) {
            item(key = "_summary") { SummaryCard(state) }
        }
        if (sections.isEmpty()) {
            item(key = "_empty") {
                Text(
                    if (state.missingOnly && state.query.isBlank()) "Everything here is photographed." else "No entries match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for (section in sections) {
            section.title?.let { title ->
                stickyHeader(key = "h_${section.key}") {
                    SectionHeader(section, title) {
                        BulkCompleteCheckbox(state.bulkScope(section.key), bulk, onApply = {
                            viewModel.setAllPhotographed(section.key, it == BulkAction.MARK)
                        })
                    }
                }
            }
            itemsIndexed(section.entries, key = { _, entry -> entry.id }) { i, entry ->
                EntryRow(
                    entry,
                    number = entry.numberIn(masterMode),
                    photographed = entry.id in state.photographed,
                    onPhotographedChange = { viewModel.setPhotographed(entry.id, it) },
                    onClick = { onOpenEntry(entry.id) },
                    showDivider = i != section.entries.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun Filters(state: CompendiumUiState, viewModel: CompendiumViewModel, bulk: BulkCompleteState) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Search name, number, place or drop") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        FilterChip(
            selected = state.missingOnly,
            onClick = { viewModel.setMissingOnly(!state.missingOnly) },
            label = { Text("Missing only") },
        )
        // Acts on what the search and chip leave showing on this tab, so it sits under them.
        if (state.sections.isNotEmpty()) {
            BulkCompleteRow(state.bulkScope(), bulk, onApply = { viewModel.setAllPhotographed(null, it == BulkAction.MARK) })
        }
    }
}

/** Overall progress, and the fallback for pictures that are hard to take. */
@Composable
private fun SummaryCard(state: CompendiumUiState) {
    val done = state.photographedCount
    val total = state.entries.size
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$done / $total photographed", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(
                if (state.saveFile.masterMode) {
                    "Master Mode adds five entries — Sky Octorok and the four Golden enemies — so its numbers run to $total."
                } else {
                    "With both DLC packs the Compendium has $total entries; Master Mode files add five more."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Nothing is lost for good: after the \"Sunshroom Sensing\" side quest, Symin at the Hateno Ancient Tech Lab " +
                    "sells the picture of any missing entry for 100 rupees. Elite enemy pictures (silver and golden enemies, " +
                    "bosses) cost 500 and only go on sale after Dark Beast Ganon is defeated. Entries marked Limited " +
                    "can only be photographed at certain times.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(section: CompendiumSection, title: String, leading: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "${section.photographed} / ${section.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: CompendiumEntry,
    number: Int?,
    photographed: Boolean,
    onPhotographedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Checkbox(checked = photographed, onCheckedChange = onPhotographedChange)
            Text(
                number?.let(::compendiumNumber).orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (photographed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.locations.isNotEmpty()) {
                    Text(
                        entry.locations.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            EntryTags(entry, Modifier.padding(start = 8.dp))
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = 92.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** DLC, Master Mode and Limited badges; nothing for an ordinary base-game entry. */
@Composable
fun EntryTags(entry: CompendiumEntry, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (entry.source) {
            EntrySource.BASE -> Unit
            EntrySource.DLC2 -> Tag("DLC")
            EntrySource.MASTER_MODE -> Tag("Master Mode")
        }
        if (entry.limited) {
            Tag(
                "Limited",
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
