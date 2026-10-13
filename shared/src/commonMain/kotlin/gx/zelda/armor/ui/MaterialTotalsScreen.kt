package gx.zelda.armor.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gx.zelda.armor.logic.ArmorCalculator
import gx.zelda.armor.logic.MaterialTotal
import gx.zelda.armor.logic.TotalsMode
import gx.zelda.armor.model.MaterialSort
import gx.zelda.core.ui.SortMenu
import gx.zelda.core.ui.Stars

@Composable
fun MaterialTotalsScreen(
    state: ArmorUiState,
    viewModel: ArmorViewModel,
    onOpenPiece: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val totals = remember(state.catalog, state.progress, state.totalsMode) {
        ArmorCalculator.totals(state.catalog, state.progress, state.totalsMode)
    }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val materialSort = state.progress.materialSort
    val lines = remember(totals, materialSort) { totals.sorted(materialSort) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        item(key = "_controls") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TotalsMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = state.totalsMode == mode,
                            onClick = { viewModel.setTotalsMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, TotalsMode.entries.size),
                            icon = {},
                        ) { Text(mode.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, minLines = 2) }
                    }
                }
                SourceFilterChips(state.catalog, state.progress, viewModel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${totals.tiersRemaining} upgrades across ${totals.piecesCounted} pieces",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SortMenu(materialSort, MaterialSort.entries, { it.label }, viewModel::setMaterialSort)
                }
            }
        }
        if (lines.isEmpty()) {
            item(key = "_empty") {
                Text(
                    if (state.totalsMode == TotalsMode.COLLECTED) {
                        "Nothing left to upgrade on your collected armor. Mark pieces as collected on the Armor tab, or switch to All Armor."
                    } else {
                        "Every piece is fully upgraded. Nice."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(lines, key = { it.material.id }) { line ->
            MaterialRow(
                line = line,
                expanded = expanded == line.material.id,
                onToggle = { expanded = if (expanded == line.material.id) null else line.material.id },
                onOpenPiece = onOpenPiece,
            )
        }
    }
}

@Composable
private fun MaterialRow(line: MaterialTotal, expanded: Boolean, onToggle: () -> Unit, onOpenPiece: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.material.name, style = MaterialTheme.typography.bodyLarge)
                line.material.category?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "×${line.qty}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide pieces" else "Show pieces",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 32.dp, end = 16.dp, bottom = 8.dp)) {
                line.uses.forEach { use ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenPiece(use.piece.id) }.padding(vertical = 6.dp),
                    ) {
                        Text(use.piece.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Stars(use.toLevel, use.piece.maxLevel, Modifier.padding(end = 12.dp))
                        Text("×${use.qty}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
