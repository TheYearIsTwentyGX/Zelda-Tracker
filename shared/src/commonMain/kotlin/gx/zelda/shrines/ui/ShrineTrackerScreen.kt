package gx.zelda.shrines.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.Tag
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.TrialType

/** Shrines by tower region, which are complete on the active save file, and the Spirit Orb planner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrineTrackerScreen(
    state: ShrineUiState?,
    loadFailed: Boolean,
    viewModel: ShrineViewModel,
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
            )
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                if (loadFailed) {
                    Text(
                        "The shrine list couldn't be loaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }
        ShrineList(state, viewModel, padding)
    }
}

@Composable
private fun ShrineList(state: ShrineUiState, viewModel: ShrineViewModel, contentPadding: PaddingValues) {
    val completed = state.progress.completed
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
                RegionHeader(region, done = inRegion.count { it.id in completed }, total = inRegion.size)
            }
            itemsIndexed(shrines, key = { _, shrine -> shrine.id }) { i, shrine ->
                val last = i == shrines.lastIndex
                ShrineRow(
                    shrine,
                    completed = shrine.id in completed,
                    onCompletedChange = { viewModel.setCompleted(shrine.id, it) },
                    showDivider = !last,
                    modifier = Modifier
                        .padding(bottom = if (last) GroupGap else 0.dp)
                        .padding(horizontal = CardInset)
                        .clip(if (last) CardBottom else RectangleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}

@Composable
private fun ShrineFilterBar(state: ShrineUiState, viewModel: ShrineViewModel) {
    val filters = state.filters
    val completed = state.progress.completed
    val catalog = state.catalog
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Search shrine, trial or quest") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filters.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
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
                append("${catalog.base.count { it.id in completed }} / ${catalog.base.size} shrines complete")
                if (state.prefs.showDlc && catalog.dlc.isNotEmpty()) {
                    append(" · DLC ${catalog.dlc.count { it.id in completed }} / ${catalog.dlc.size}")
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RegionHeader(region: String, done: Int, total: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CardTop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$region Tower",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (done == total) {
                Tag(
                    "$done / $total",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Tag("$done / $total")
            }
        }
    }
}

@Composable
private fun ShrineRow(
    shrine: Shrine,
    completed: Boolean,
    onCompletedChange: (Boolean) -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = completed, role = Role.Checkbox, onValueChange = onCompletedChange)
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
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
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
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

private val CardInset = 12.dp
private val GroupGap = 12.dp
private val CardTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val CardBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
