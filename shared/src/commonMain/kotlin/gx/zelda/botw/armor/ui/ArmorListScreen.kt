package gx.zelda.botw.armor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gx.zelda.botw.armor.logic.ArmorCalculator
import gx.zelda.botw.armor.logic.SetBonusStatus
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorProgress
import gx.zelda.botw.armor.model.Source
import gx.zelda.core.ui.Stars
import gx.zelda.core.ui.Tag

private data class ArmorGroup(
    val key: String,
    val title: String,
    val source: Source?,
    val bonus: SetBonusStatus?,
    val pieces: List<ArmorPiece>,
)

@Composable
fun ArmorListScreen(
    state: ArmorUiState,
    viewModel: ArmorViewModel,
    onOpenPiece: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val catalog = state.catalog
    val progress = state.progress

    val visible = catalog.pieces.filter { progress.isVisible(it.source) }
    val ownedCount = visible.count { progress.of(it.id).owned }

    val groups = remember(catalog, progress, state.ownershipFilter, state.query) {
        val q = state.query.trim()
        val matching = visible.filter { piece ->
            val owned = progress.of(piece.id).owned
            val ownershipOk = when (state.ownershipFilter) {
                OwnershipFilter.ALL -> true
                OwnershipFilter.OWNED -> owned
                OwnershipFilter.MISSING -> !owned
            }
            val setName = catalog.set(piece.setId)?.name.orEmpty()
            ownershipOk && (q.isEmpty() || piece.name.contains(q, true) || setName.contains(q, true))
        }
        val bySet = matching.filter { it.setId != null }.groupBy { it.setId!! }
        val setGroups = catalog.sets.mapNotNull { set ->
            val pieces = bySet[set.id] ?: return@mapNotNull null
            ArmorGroup(set.id, set.name, set.source, ArmorCalculator.setBonusStatus(catalog, set, progress), pieces)
        }
        val singles = matching.filter { it.setId == null }
        setGroups + listOfNotNull(
            singles.takeIf { it.isNotEmpty() }?.let { ArmorGroup("_singles", "Other armor", null, null, it) },
        )
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        item(key = "_filters") {
            ArmorFilters(state, viewModel, ownedCount, visible.size)
        }
        if (groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No armor matches these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for (group in groups) {
            item(key = "h_${group.key}") { GroupHeader(group) }
            items(group.pieces, key = { it.id }) { piece ->
                PieceRow(piece, progress, viewModel, onClick = { onOpenPiece(piece.id) })
            }
        }
    }
}

@Composable
private fun ArmorFilters(state: ArmorUiState, viewModel: ArmorViewModel, owned: Int, total: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Search armor or set") },
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            OwnershipFilter.entries.forEachIndexed { i, filter ->
                SegmentedButton(
                    selected = state.ownershipFilter == filter,
                    onClick = { viewModel.setOwnershipFilter(filter) },
                    shape = SegmentedButtonDefaults.itemShape(i, OwnershipFilter.entries.size),
                ) { Text(filter.label) }
            }
        }
        SourceFilterChips(state.progress, viewModel)
        Text(
            "$owned / $total collected",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SourceFilterChips(progress: ArmorProgress, viewModel: ArmorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = progress.showDlc,
            onClick = { viewModel.setShowDlc(!progress.showDlc) },
            label = { Text("DLC") },
        )
        FilterChip(
            selected = progress.showAmiibo,
            onClick = { viewModel.setShowAmiibo(!progress.showAmiibo) },
            label = { Text("amiibo") },
        )
    }
}

@Composable
private fun GroupHeader(group: ArmorGroup) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (group.source != null && group.source != Source.BASE) Tag(group.source.label)
            }
            val bonus = group.bonus
            if (bonus != null && bonus.hasBonus) {
                Spacer(Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Set bonus: ${bonus.set.bonus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SetBonusBadge(bonus)
                }
            }
        }
    }
}

@Composable
fun SetBonusBadge(status: SetBonusStatus) {
    if (status.isActive) {
        Tag(
            "Active",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    } else {
        Tag("Inactive")
    }
}

@Composable
private fun PieceRow(piece: ArmorPiece, progress: ArmorProgress, viewModel: ArmorViewModel, onClick: () -> Unit) {
    val owned = progress.of(piece.id).owned
    val level = ArmorCalculator.level(piece, progress)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Checkbox(checked = owned, onCheckedChange = { viewModel.setOwned(piece.id, it) })
            Column(Modifier.weight(1f)) {
                Text(
                    piece.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        piece.slot.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    piece.effect?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Stars(level, piece.maxLevel)
                Text(
                    "${ArmorCalculator.defenseAt(piece, level)} def",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
