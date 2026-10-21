package gx.zelda.upgrades.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gx.zelda.armor.ui.OwnershipFilter
import gx.zelda.core.BulkAction
import gx.zelda.core.countOf
import gx.zelda.core.ui.BulkCompleteCheckbox
import gx.zelda.core.ui.BulkCompleteRow
import gx.zelda.core.ui.BulkCompleteState
import gx.zelda.core.ui.StarLevelButton
import gx.zelda.core.ui.Tag
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.model.ItemCopy
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradePrefs

@Composable
fun UpgradeListScreen(
    state: UpgradeUiState,
    viewModel: UpgradeViewModel,
    bulk: BulkCompleteState,
    onOpenItem: (itemId: String, copy: Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val progress = state.progress
    val owned = state.visible.count(progress::owned)

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        item(key = "_filters") {
            UpgradeFilters(state, viewModel, bulk, owned, state.visible.size)
        }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No items match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for (group in state.groups) {
            val section = group.section
            item(key = "h_${section.id}") {
                SectionHeader(section.title, section.note) {
                    BulkCompleteCheckbox(state.bulkScope(section.id), bulk, onApply = {
                        viewModel.setAllOwned(section.id, it == BulkAction.MARK)
                    })
                }
            }
            // A pouch item's last copy is followed by a row to add another, unless a filter is narrowing the list.
            val rows = group.copies.flatMap { copy ->
                val lastCopy = copy.number == progress.copyCount(copy.item)
                listOfNotNull(ListRow.Item(copy), ListRow.AddCopy(copy).takeIf { copy.item.multiple && lastCopy && !state.filtered })
            }
            rows.forEachIndexed { i, row ->
                val last = i == rows.lastIndex
                when (row) {
                    is ListRow.Item -> item(key = row.copy.key) {
                        CopyRow(
                            row.copy, state, viewModel,
                            onClick = { onOpenItem(row.copy.item.id, row.copy.number) },
                            showDivider = !last,
                            modifier = cardRow(last),
                        )
                    }
                    is ListRow.AddCopy -> item(key = "add_${row.copy.item.id}") {
                        AddCopyRow(row.copy, onAdd = { viewModel.addCopy(row.copy.item.id) }, showDivider = !last, modifier = cardRow(last))
                    }
                }
            }
        }
    }
}

private sealed interface ListRow {
    data class Item(val copy: ItemCopy) : ListRow
    data class AddCopy(val copy: ItemCopy) : ListRow
}

@Composable
private fun UpgradeFilters(state: UpgradeUiState, viewModel: UpgradeViewModel, bulk: BulkCompleteState, owned: Int, total: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Search items or upgrades") },
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
        SectionFilterChips(state.catalog, state.prefs, viewModel)
        Text(
            "$owned / $total collected",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Acts on what the search and filters leave showing, so it sits under them.
        if (state.groups.isNotEmpty()) {
            BulkCompleteRow(state.bulkScope(), bulk, onApply = { viewModel.setAllOwned(null, it == BulkAction.MARK) })
        }
    }
}

/** A chip per optional section (the potions): off leaves it out of the list, the totals and the overview. */
@Composable
fun SectionFilterChips(catalog: UpgradeCatalog, prefs: UpgradePrefs, viewModel: UpgradeViewModel) {
    if (catalog.optionalSections.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        catalog.optionalSections.forEach { section ->
            val shown = prefs.shows(section.id)
            FilterChip(
                selected = shown,
                onClick = { viewModel.setSectionShown(section.id, !shown) },
                label = { Text(section.optional!!) },
            )
        }
    }
}

/** A row inside a section's card; the [last] one rounds the card off. */
@Composable
private fun cardRow(last: Boolean): Modifier = Modifier
    .padding(bottom = if (last) GroupGap else 0.dp)
    .padding(horizontal = CardInset)
    .clip(if (last) CardBottom else RectangleShape)
    .background(MaterialTheme.colorScheme.surfaceContainer)

private val CardInset = 12.dp
private val GroupGap = 12.dp
private val CardTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val CardBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Composable
private fun SectionHeader(title: String, note: String?, toggle: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CardTop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 16.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                toggle()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            note?.let {
                // Lined up with the title, past the toggle.
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun CopyRow(
    copy: ItemCopy,
    state: UpgradeUiState,
    viewModel: UpgradeViewModel,
    onClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress
    val item = copy.item
    val owned = progress.owned(copy)
    val level = progress.level(copy)
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Checkbox(checked = owned, onCheckedChange = { viewModel.setOwned(copy.key, it) })
            UpgradeItemIcon(item, level, size = 40.dp, dimmed = !owned, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    // The form you have; its copy number after it, for a second satchel and on.
                    if (copy.number == 1) item.levelName(level) else "${item.levelName(level)} #${copy.number}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            !item.isUpgradable -> "Can't be upgraded"
                            !owned -> countOf(item.maxLevel, "upgrade")
                            level >= item.maxLevel -> "Fully upgraded"
                            else -> "Next: ${item.levelName(level + 1)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.missable) Tag("Missable")
                    if (item.multiple && !UpgradeCalculator.isCounted(copy, progress)) Tag("Not in totals")
                }
            }
            // Setting a level collects the item, so tapping an uncollected item's stars collects it upgraded once.
            if (item.isUpgradable) {
                StarLevelButton(level, item.maxLevel, onLevelChange = { viewModel.setLevel(copy.key, it) })
            }
            Spacer(Modifier.width(4.dp))
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = 100.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AddCopyRow(last: ItemCopy, onAdd: () -> Unit, showDivider: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        TextButton(onClick = onAdd, modifier = Modifier.padding(start = 52.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Add ${last.item.name} #${last.number + 1}")
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = 100.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
