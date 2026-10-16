package gx.zelda.hearts.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.FoundToggleStyle
import gx.zelda.core.ui.HiddenRow
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import gx.zelda.hearts.model.HeartEntry
import gx.zelda.hearts.model.HeartKind
import gx.zelda.hearts.model.HeartTotal

/** Pieces of Heart and Heart Containers on the active save file, grouped by region, under a hearts meter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartTrackerScreen(
    state: HeartUiState?,
    loadFailed: Boolean,
    viewModel: HeartViewModel,
    onBack: () -> Unit,
    title: String = "Heart Pieces",
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it.file, it.total.heartsLabel) },
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
                    if (state != null) HeartMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The heart list couldn't be loaded.", padding)
            return@Scaffold
        }
        HeartList(state, viewModel, Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun HeartMenu(state: HeartUiState, viewModel: HeartViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide uncollected") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUncollected, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUncollected(!state.prefs.hideUncollected)
                open = false
            },
        )
    }
}

private val HeartToggle = FoundToggleStyle(Icons.Filled.Favorite, Icons.Filled.FavoriteBorder, "Collected", "Mark collected")

@Composable
private fun HeartList(state: HeartUiState, viewModel: HeartViewModel, modifier: Modifier) {
    LazyColumn(modifier) {
        // While searching, the meter would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_meter") { HeartMeterCard(state.total) }
        }
        item(key = "_filters") { HeartFilterBar(state, viewModel) }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No hearts match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, entries) in state.groups) {
            // The header counts the whole region, not just the entries the filters leave showing.
            val count = state.regionCounts.getValue(region)
            item(key = "h_$region", contentType = "header") { RegionHeader(region, found = null, done = count.collected, total = count.total, suffix = "") }
            itemsIndexed(entries, key = { _, entry -> entry.id }, contentType = { _, _ -> "heart" }) { i, entry ->
                val last = i == entries.lastIndex
                if (state.isHidden(entry)) {
                    HiddenRow(
                        "Uncollected ${entry.kind.label}",
                        onReveal = { viewModel.reveal(entry.id) },
                        onFound = { viewModel.setCollected(entry.id, true) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                        toggleStyle = HeartToggle,
                    )
                } else {
                    HeartRow(
                        entry,
                        collected = state.progress.isCollected(entry.id),
                        onCollectedChange = { viewModel.setCollected(entry.id, it) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                }
            }
        }
    }
}

/** Link's hearts as the game draws them, ten to a row, with the next heart filled by quarters. */
@Composable
private fun HeartMeterCard(total: HeartTotal) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(total.heartsLabel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "of ${total.maxHearts}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = "${total.heartsLabel} of ${total.maxHearts}" },
            ) {
                for (row in (0 until total.maxHearts).chunked(HEARTS_PER_ROW)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (i in row) {
                            val fill = when {
                                i < total.hearts -> 1f
                                i == total.hearts -> total.quarters / 4f
                                else -> 0f
                            }
                            HeartIcon(fill)
                        }
                    }
                }
            }
            Text(
                "Pieces of Heart ${total.pieces} / ${total.piecesTotal} · Heart Containers ${total.containers} / ${total.containersTotal}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Link starts with ${total.startingHearts}; every four Pieces of Heart make another.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val HEARTS_PER_ROW = 10

/** A heart outline, filled from the left by [fill] (0–1). */
@Composable
private fun HeartIcon(fill: Float) {
    val size = Modifier.size(22.dp)
    Box {
        Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = size)
        if (fill > 0f) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = size.drawWithContent { clipRect(right = this.size.width * fill) { this@drawWithContent.drawContent() } },
            )
        }
    }
}

@Composable
private fun HeartFilterBar(state: HeartUiState, viewModel: HeartViewModel) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search place, item or region")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.missingOnly,
                onClick = { viewModel.setMissingOnly(!filters.missingOnly) },
                label = { Text("Not collected") },
            )
            for (requirement in HeartRequirement.entries) {
                FilterChip(
                    selected = requirement in filters.requirements,
                    onClick = { viewModel.toggleRequirement(requirement) },
                    label = { Text(requirement.label) },
                )
            }
        }
    }
}

@Composable
private fun HeartRow(
    entry: HeartEntry,
    collected: Boolean,
    onCollectedChange: (Boolean) -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = collected, role = Role.Checkbox, onValueChange = onCollectedChange)
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        ) {
            Checkbox(checked = collected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (collected) dim else MaterialTheme.colorScheme.onSurface,
                )
                val tagged = entry.kind == HeartKind.CONTAINER || entry.kinstone || entry.minish
                if (tagged) Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (entry.kind == HeartKind.CONTAINER) {
                        Tag(
                            "Heart Container",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (entry.kinstone) Tag("Kinstone")
                    if (entry.minish) Tag("Minish")
                }
                Text(entry.area, style = MaterialTheme.typography.labelMedium, color = dim)
                Text(entry.howTo, style = MaterialTheme.typography.bodySmall, color = dim)
                if (entry.items.isNotEmpty()) {
                    Text("Needs ${entry.items.joinToString()}", style = MaterialTheme.typography.bodySmall, color = dim)
                }
                entry.fusionNote?.let { Text("Fusion: $it", style = MaterialTheme.typography.bodySmall, color = dim) }
                entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = dim) }
            }
        }
        if (showDivider) RowDivider()
    }
}
