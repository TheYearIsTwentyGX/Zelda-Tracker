package gx.zelda.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.checklist.model.formatAmount
import gx.zelda.core.ui.Tag

/**
 * One checklist item: where it is, how to get it, what it needs first and what it gives. With
 * spoilers hidden, an item with nothing done shows only its heading until revealed or ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistDetailScreen(
    state: ChecklistUiState,
    itemId: String,
    viewModel: ChecklistViewModel,
    onBack: () -> Unit,
    /** The sections the game's map shows; an item in one gets "Show on map". */
    mapSections: Set<String> = emptySet(),
    onShowOnMap: () -> Unit = {},
) {
    val item = state.catalog.item(itemId) ?: return
    val section = state.catalog.sectionOf(item)
    val hidden = state.isHidden(item)
    val level = state.progress.level(item)
    val done = state.progress.isDone(item)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (hidden) state.catalog.hiddenLabelOf(item) else item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${section.title} · ${state.saveFile.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (item.tiers.isEmpty()) {
                FilterChip(
                    selected = done,
                    onClick = { viewModel.setDone(item.id, !done) },
                    label = { Text(if (done) "Done" else "Not done") },
                )
            } else {
                Text("$level of ${item.steps} done", style = MaterialTheme.typography.titleMedium)
            }

            if (hidden) {
                Section("Where") { Text(state.catalog.groupOf(item), style = MaterialTheme.typography.bodyLarge) }
                Text(
                    "Hidden until something on it is done.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { viewModel.reveal(item.id) }) { Text("Reveal") }
                return@Column
            }

            if (item.missable) Note("Missable: this can be missed for good on a save file.")

            if (item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.forEach { Tag(it) }
                }
            }

            if (item.region != null || item.area != null) {
                Section("Where") {
                    item.area?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    item.region?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (section.id in mapSections) {
                OutlinedButton(onClick = onShowOnMap) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Show on map")
                }
            }

            item.howTo?.let { Section("How") { Text(it, style = MaterialTheme.typography.bodyLarge) } }

            val requirements = state.requirements(item)
            val after = state.after(item)
            if (requirements.isNotEmpty() || after != null) {
                Section("First") {
                    after?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    requirements.forEach { (name, met) ->
                        Text(
                            (if (met) "✓ " else "• ") + name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (met) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (item.tiers.isNotEmpty()) {
                Section("Tiers") {
                    item.tiers.forEachIndexed { i, tier ->
                        val tierDone = i < level
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                // Ticking a tier does every tier before it; unticking undoes it and every one after.
                                .toggleable(value = tierDone, role = Role.Checkbox) { viewModel.setLevel(item.id, if (it) i + 1 else i) },
                        ) {
                            Checkbox(checked = tierDone, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(tier.name, style = MaterialTheme.typography.bodyLarge)
                                tier.howTo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                tier.reward?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item.reward?.let { Section("Reward") { Text(it, style = MaterialTheme.typography.bodyLarge) } }

            state.sharedWith(item)?.let { Section("Shared") { Text(it, style = MaterialTheme.typography.bodyLarge) } }

            if (item.value != 1.0) {
                Section("Counts as") { Text(formatAmount(item.value), style = MaterialTheme.typography.bodyLarge) }
            }

            item.notes?.let { Section("Notes") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** A highlighted line, e.g. how the item can be missed. */
@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
