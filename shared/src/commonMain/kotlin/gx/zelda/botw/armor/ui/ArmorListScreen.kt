package gx.zelda.botw.armor.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gx.zelda.botw.armor.logic.ArmorCalculator
import gx.zelda.botw.armor.logic.ArmorGroup
import gx.zelda.botw.armor.logic.ArmorGrouping
import gx.zelda.botw.armor.logic.SetBonusStatus
import gx.zelda.botw.armor.logic.SetUpgrade
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorProgress
import gx.zelda.botw.armor.model.ArmorGroupBy
import gx.zelda.botw.armor.model.ArmorOrder
import gx.zelda.botw.armor.model.Source
import gx.zelda.core.ui.SortMenu
import gx.zelda.core.ui.SortOption
import gx.zelda.core.ui.SortSection
import gx.zelda.core.ui.StarButton
import gx.zelda.core.ui.StarLevelButton
import gx.zelda.core.ui.Tag
import kotlin.math.roundToInt

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
        ArmorGrouping.group(catalog, matching, progress.order, progress.group)
    }
    // Without set headers, each row names its set alongside its slot.
    val showSetOnRows = progress.group == ArmorGroupBy.NONE

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
            // Titled groups render as one card (header + rows) so it's clear where each set ends.
            // Standalone runs sit on the plain background outside any card.
            val carded = group.title != null
            if (carded) {
                item(key = "h_${group.key}") {
                    GroupHeader(
                        group,
                        bonus = group.set?.let { ArmorCalculator.setBonusStatus(catalog, it, progress) },
                        setStars = group.set?.let {
                            SetUpgrade.targets(group.pieces, progress).takeIf { t -> t.isNotEmpty() }
                        }?.let { targets ->
                            SetStars(
                                average = SetUpgrade.averageLevel(targets, progress),
                                max = targets.maxOf { it.maxLevel },
                                onTap = { viewModel.setLevels(SetUpgrade.nextLevels(targets, progress)) },
                            )
                        },
                    )
                }
            }
            itemsIndexed(group.pieces, key = { _, piece -> piece.id }) { i, piece ->
                val last = i == group.pieces.lastIndex
                val subtitle = if (showSetOnRows) {
                    listOfNotNull(piece.slot.label, catalog.set(piece.setId)?.name).joinToString(" · ")
                } else {
                    piece.slot.label
                }
                PieceRow(
                    piece, subtitle, progress, viewModel,
                    onClick = { onOpenPiece(piece.id) },
                    showDivider = !last,
                    modifier = Modifier
                        .padding(bottom = if (last) GroupGap else 0.dp)
                        .padding(horizontal = CardInset)
                        .then(
                            if (carded) {
                                Modifier
                                    .clip(if (last) CardBottom else RectangleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                            } else {
                                Modifier
                            },
                        ),
                )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$owned / $total collected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val progress = state.progress
            SortMenu(
                summary = "${progress.order.label} · ${progress.group.label}",
                sections = listOf(
                    SortSection(
                        "Order",
                        ArmorOrder.entries.map { SortOption(it.label, it == progress.order) { viewModel.setOrder(it) } },
                    ),
                    SortSection(
                        "Group",
                        ArmorGroupBy.entries.map { SortOption(it.label, it == progress.group) { viewModel.setGroup(it) } },
                    ),
                ),
            )
        }
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

private val CardInset = 12.dp
private val GroupGap = 12.dp
private val CardTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val CardBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

/** The star counter on a set's header: average level of the set's pieces, tapping upgrades the lowest. */
private class SetStars(val average: Float, val max: Int, val onTap: () -> Unit)

/** 1.3333 → "1.33", 2.0 → "2". */
private fun formatLevel(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    return if (hundredths % 100 == 0) "${hundredths / 100}" else "${hundredths / 100.0}"
}

@Composable
private fun GroupHeader(group: ArmorGroup, bonus: SetBonusStatus?, setStars: SetStars?) {
    val title = group.title ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CardTop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val source = group.set?.source
                if (source != null && source != Source.BASE) Tag(source.label)
                setStars?.let {
                    val average = formatLevel(it.average)
                    StarButton(
                        value = it.average,
                        max = it.max,
                        description = "Set upgrade level $average of ${it.max}",
                        clickLabel = "Upgrade the least upgraded pieces",
                        onClick = it.onTap,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
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
private fun PieceRow(
    piece: ArmorPiece,
    subtitle: String?,
    progress: ArmorProgress,
    viewModel: ArmorViewModel,
    onClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val owned = progress.of(piece.id).owned
    val level = ArmorCalculator.level(piece, progress)
    Column(modifier) {
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
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    piece.effect?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Setting a level marks the piece collected, so tapping an uncollected piece's stars collects it at ★1.
                if (piece.isUpgradable) {
                    StarLevelButton(level, piece.maxLevel, onLevelChange = { viewModel.setLevel(piece.id, it) })
                }
                Text(
                    "${ArmorCalculator.defenseAt(piece, level)} def",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
