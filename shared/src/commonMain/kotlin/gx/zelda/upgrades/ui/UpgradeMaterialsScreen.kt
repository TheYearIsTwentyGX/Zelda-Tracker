package gx.zelda.upgrades.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import gx.zelda.armor.model.MaterialSort
import gx.zelda.core.countOf
import gx.zelda.core.ui.SortMenu
import gx.zelda.core.ui.Stars
import gx.zelda.upgrades.logic.MaterialTotal
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.logic.thousands

/** Total Materials Required: every remaining upgrade summed, one copy of each pouch item. */
@Composable
fun UpgradeMaterialsScreen(
    state: UpgradeUiState,
    viewModel: UpgradeViewModel,
    onOpenItem: (itemId: String, copy: Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val totals = remember(state.catalog, state.progress, state.prefs, state.totalsMode) {
        UpgradeCalculator.totals(state.catalog, state.progress, state.prefs, state.totalsMode)
    }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val materialSort = state.prefs.materialSort
    val lines = remember(totals, materialSort) { totals.sorted(materialSort) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        item(key = "_controls") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalsModeSwitch(state.totalsMode, viewModel::setTotalsMode)
                SectionFilterChips(state.catalog, state.prefs, viewModel)
                RupeeTotal(totals.rupees)
                val pouchItems = state.catalog.items.filter { it.multiple }.map { it.name }
                if (pouchItems.isNotEmpty()) {
                    Text(
                        "One of each counts: your most upgraded ${listed(pouchItems)}, however many you carry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TotalsCountRow(
                    "${countOf(totals.stepsRemaining, "upgrade")} on ${countOf(totals.itemsCounted, "item")}",
                    materialSort,
                    viewModel::setMaterialSort,
                )
            }
        }
        if (lines.isEmpty()) {
            item(key = "_empty") {
                Text(
                    when {
                        totals.stepsRemaining > 0 -> "Only free upgrades are left."
                        state.totalsMode == UpgradeTotalsMode.COLLECTED ->
                            "Nothing left to upgrade on your collected items. Mark items as collected on the Items tab, or switch to All items."
                        else -> "Everything is fully upgraded. Nice."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        materialTotalRows(lines, expanded, onToggle = { id -> expanded = if (expanded == id) null else id }, onOpenItem = onOpenItem)
    }
}

/** The totals' scope: what you have, or everything. Potion Infusions names them its own way. */
@Composable
internal fun TotalsModeSwitch(
    selected: UpgradeTotalsMode,
    onSelect: (UpgradeTotalsMode) -> Unit,
    label: (UpgradeTotalsMode) -> String = { it.label },
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        UpgradeTotalsMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, UpgradeTotalsMode.entries.size),
                icon = {},
            ) { Text(label(mode), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, minLines = 2) }
        }
    }
}

@Composable
internal fun RupeeTotal(rupees: Int) {
    Text(
        "${thousands(rupees)} Rupees",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
    )
}

/** "12 upgrades on 7 items", and the material sort beside it. */
@Composable
internal fun TotalsCountRow(text: String, sort: MaterialSort, onSort: (MaterialSort) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        SortMenu(sort, MaterialSort.entries, { it.label }, onSort)
    }
}

/**
 * One row per material, [expanded] (a material id) showing where to get it and what needs it;
 * tapping a use opens [onOpenItem] with its item and copy. [usesNoun] names those uses for the arrow.
 */
internal fun LazyListScope.materialTotalRows(
    lines: List<MaterialTotal>,
    expanded: String?,
    onToggle: (materialId: String) -> Unit,
    onOpenItem: (itemId: String, copy: Int) -> Unit,
    usesNoun: String = "upgrades",
) {
    items(lines, key = { it.material.id }) { line ->
        MaterialRow(
            line = line,
            expanded = expanded == line.material.id,
            onToggle = { onToggle(line.material.id) },
            onOpenItem = onOpenItem,
            usesNoun = usesNoun,
        )
    }
}

@Composable
private fun MaterialRow(line: MaterialTotal, expanded: Boolean, onToggle: () -> Unit, onOpenItem: (String, Int) -> Unit, usesNoun: String) {
    val material = line.material
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            MaterialIcon(material, size = 36.dp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(material.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    (listOf(material.kind.label) + material.regions).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "×${line.qty}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide $usesNoun" else "Show $usesNoun",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 64.dp, end = 16.dp, bottom = 8.dp)) {
                if (material.farm.isNotBlank()) {
                    Text(
                        "Where to get it: ${material.farm}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                line.uses.forEach { use ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenItem(use.copy.item.id, use.copy.number) }.padding(vertical = 6.dp),
                    ) {
                        Text(
                            if (use.copy.number == 1) use.name else "${use.name} (#${use.copy.number})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Stars(use.toLevel, use.copy.item.maxLevel, Modifier.padding(end = 12.dp))
                        Text("×${use.qty}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "A", "A and B", "A, B and C". */
private fun listed(names: List<String>): String =
    if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
