package gx.zelda.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistSection
import gx.zelda.core.BulkAction
import gx.zelda.core.ui.BulkCompleteCheckbox
import gx.zelda.core.ui.BulkCompleteRow
import gx.zelda.core.ui.BulkCompleteState
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.HiddenRow
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import gx.zelda.core.ui.rememberBulkCompleteState
import gx.zelda.theme.GameProgressBar

/**
 * Any checklist tracker on the active save file: a tab per section (with "All" first), the file's
 * summaries, and the items grouped by region. Tapping a row opens its page; the box ticks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    state: ChecklistUiState?,
    loadFailed: Boolean,
    viewModel: ChecklistViewModel,
    title: String,
    onOpenItem: (String) -> Unit,
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
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it.file, "${it.count.done} / ${it.count.total}") },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = { MenuOrBackButton(onBack) },
                actions = {
                    if (state != null) ChecklistMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "This checklist couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.showTabs) SectionTabs(state, viewModel)
            ChecklistList(state, viewModel, bulk, onOpenItem, Modifier.fillMaxSize())
        }
        BulkConfirmDialog(bulk, state.saveFile.label)
    }
}

@Composable
private fun ChecklistMenu(state: ChecklistUiState, viewModel: ChecklistViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide unchecked") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUnchecked, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUnchecked(!state.prefs.hideUnchecked)
                open = false
            },
        )
    }
}

@Composable
private fun SectionTabs(state: ChecklistUiState, viewModel: ChecklistViewModel) {
    val tabs = listOf<ChecklistSection?>(null) + state.catalog.sections
    PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(state.section).coerceAtLeast(0), edgePadding = 8.dp) {
        tabs.forEach { section ->
            val count = if (section == null) state.count else state.sectionCount(section)
            Tab(
                selected = state.section == section,
                onClick = { viewModel.setSection(section?.id) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(section?.title ?: "All", maxLines = 1)
                        Text(
                            "${count.done} / ${count.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (count.complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ChecklistList(
    state: ChecklistUiState,
    viewModel: ChecklistViewModel,
    bulk: BulkCompleteState,
    onOpenItem: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier) {
        // While searching, the summary would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_summary") { SummaryCard(state) }
        }
        item(key = "_filters") { FilterBar(state, viewModel, bulk) }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    if (state.filtered || state.lockedOnTab == 0) "Nothing matches these filters." else "The story hasn't unlocked anything here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((group, items) in state.groups) {
            // The header counts the whole heading on this tab, not just the rows the filters leave.
            val count = state.groupCounts.getValue(group)
            item(key = "h_$group", contentType = "header") {
                RegionHeader(group, found = null, done = count.done, total = count.total, suffix = "", leading = {
                    BulkCompleteCheckbox(state.bulkScope(group), bulk, onApply = { viewModel.setAllDone(group, it == BulkAction.MARK) })
                })
            }
            // What story gating leaves out under this heading closes its card, under the rows.
            val locked = state.lockedCounts[group]
            itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "item" }) { i, item ->
                val last = i == items.lastIndex && locked == null
                if (state.isHidden(item)) {
                    HiddenRow(
                        state.catalog.hiddenLabelOf(item),
                        onReveal = { viewModel.reveal(item.id) },
                        onFound = { viewModel.setLevel(item.id, 1) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                } else {
                    ItemRow(state, item, viewModel, onOpen = { onOpenItem(item.id) }, showDivider = !last, modifier = Modifier.groupRow(last))
                }
            }
            if (locked != null) {
                item(key = "l_$group", contentType = "locked") { LockedLine(locked, Modifier.groupRow(last = true).padding(horizontal = 16.dp)) }
            }
        }
        // Headings whose every item is locked have no card, so their count goes at the end.
        if (state.lockedWithoutRows > 0 && !state.filtered) {
            item(key = "_locked") {
                LockedLine(state.lockedWithoutRows, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), more = state.groups.isNotEmpty())
            }
        }
    }
}

/** "3 more unlock later in the story": what story gating leaves out under a heading, unnamed. */
@Composable
private fun LockedLine(count: Int, modifier: Modifier = Modifier, more: Boolean = true) {
    Text(
        "${if (more) "$count more" else "$count"} unlock${if (count == 1) "s" else ""} later in the story",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    )
}

/** Steps done on this tab, and the file's summaries (e.g. hearts). */
@Composable
private fun SummaryCard(state: ChecklistUiState) {
    val count = state.section?.let(state::sectionCount) ?: state.count
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${count.done} / ${count.total}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    state.section?.title ?: "Everything",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GameProgressBar(
                progress = { if (count.total == 0) 0f else count.done.toFloat() / count.total },
                modifier = Modifier.fillMaxWidth(),
            )
            state.summaries.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
            state.section?.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FilterBar(state: ChecklistUiState, viewModel: ChecklistViewModel, bulk: BulkCompleteState) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search name, place or region")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.incompleteOnly,
                onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
                label = { Text("Not done") },
            )
            if (state.catalog.items.any { it.missable }) {
                FilterChip(
                    selected = filters.missableOnly,
                    onClick = { viewModel.setMissableOnly(!filters.missableOnly) },
                    label = { Text("Missable") },
                )
            }
        }
        // Acts on what the search and chips leave showing, so it sits under them.
        if (state.groups.isNotEmpty()) {
            BulkCompleteRow(state.bulkScope(), bulk, onApply = { viewModel.setAllDone(null, it == BulkAction.MARK) })
        }
    }
}

@Composable
private fun ItemRow(
    state: ChecklistUiState,
    item: ChecklistItem,
    viewModel: ChecklistViewModel,
    onOpen: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val done = state.progress.isDone(item)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open", onClick = onOpen)
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        ) {
            if (item.tiers.isEmpty()) {
                Checkbox(checked = done, onCheckedChange = { viewModel.setDone(item.id, it) })
            } else {
                TierButton(state.progress.level(item), item.steps, item.name) { viewModel.setLevel(item.id, it) }
            }
            Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = if (done) dim else MaterialTheme.colorScheme.onSurface)
                val sectionTag = state.catalog.sectionOf(item).title.takeIf { state.showTabs && state.section == null }
                if (item.missable || sectionTag != null || item.tags.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.missable) {
                            Tag("Missable", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        sectionTag?.let { Tag(it) }
                        item.tags.forEach { Tag(it) }
                    }
                }
                item.area?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = dim) }
                item.howTo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                val missing = state.requirements(item).filterNot { it.second }.map { it.first }
                if (missing.isNotEmpty() && !done) {
                    Text("Needs ${missing.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (showDivider) RowDivider()
    }
}

/** "2 / 4": each tap does the next tier, wrapping from all done back to none. */
@Composable
private fun TierButton(level: Int, max: Int, name: String, onLevelChange: (Int) -> Unit) {
    FilledTonalButton(
        onClick = { onLevelChange(if (level >= max) 0 else level + 1) },
        modifier = Modifier.padding(4.dp).semantics { contentDescription = "$name: $level of $max done" },
    ) {
        Text("$level / $max", style = MaterialTheme.typography.labelLarge)
    }
}
