package gx.zelda.upgrades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.armor.model.Acquisition
import gx.zelda.core.ui.Stars
import gx.zelda.core.ui.Tag
import gx.zelda.theme.LocalGameDecor
import gx.zelda.upgrades.logic.RemainingStep
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.thousands
import gx.zelda.upgrades.model.ItemCopy
import gx.zelda.upgrades.model.UpgradeCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeDetailScreen(
    state: UpgradeUiState,
    itemId: String,
    copyNumber: Int,
    viewModel: UpgradeViewModel,
    onBack: () -> Unit,
) {
    val item = state.catalog.item(itemId)
    // A removed copy's page has nothing to show; the list is underneath.
    val copy = item?.let { state.progress.copiesOf(it).getOrNull(copyNumber - 1) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(copy?.label ?: item?.name ?: "Unknown item", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.saveFile.label,
                            style = MaterialTheme.typography.labelLarge,
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
        if (copy == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (item == null) "This item isn't in the list." else "This copy isn't tracked any more.")
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CopyDetail(state, copy, viewModel, onBack)
        }
    }
}

@Composable
private fun CopyDetail(state: UpgradeUiState, copy: ItemCopy, viewModel: UpgradeViewModel, onBack: () -> Unit) {
    val progress = state.progress
    val item = copy.item
    val owned = progress.owned(copy)
    val level = progress.level(copy)
    val counted = UpgradeCalculator.isCounted(copy, progress)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        UpgradeItemIcon(item, level, size = 96.dp)
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.catalog.section(item.section)?.let { Tag(it.title) }
        item.price?.let { PriceTag("Sold for ${thousands(it)} Rupees") } ?: Tag("Not sold")
        Tag(if (item.missable) "Missable" else "Can't be missed")
        if (item.multiple && !counted) Tag("Not in totals")
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Collected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = owned, onCheckedChange = { viewModel.setOwned(copy.key, it) })
        }
        if (item.isUpgradable) {
            Text("Upgrade level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                for (lvl in 0..item.maxLevel) {
                    SegmentedButton(
                        selected = owned && level == lvl,
                        onClick = { viewModel.setLevel(copy.key, lvl) },
                        enabled = owned,
                        shape = SegmentedButtonDefaults.itemShape(lvl, item.maxLevel + 1),
                        icon = {},
                    ) { Text(if (lvl == 0) "Base" else "★$lvl") }
                }
            }
            Text(
                if (owned) "You have the ${item.levelName(level)}." else "Mark it collected to track its upgrades.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("This can't be upgraded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (item.multiple) {
        val count = progress.copyCount(item)
        SectionCard(title = "Copies") {
            Text(
                "You're tracking ${count} ${if (count == 1) "copy" else "copies"} of the ${item.name}. Each takes an Adventure Pouch " +
                    "slot. The totals count one: your most upgraded, or #1 if you have none.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.addCopy(item.id) }) { Text("Add ${item.name} #${count + 1}") }
                if (copy.number > 1) {
                    TextButton(onClick = { viewModel.removeCopy(item.id, copy.number); onBack() }) { Text("Remove this one") }
                }
            }
        }
    }

    if (item.levels.size > 1) {
        SectionCard(title = "Forms") {
            item.levels.forEachIndexed { lvl, form ->
                val current = owned && lvl == level
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UpgradeItemIcon(item, lvl, size = 36.dp, dimmed = !owned || lvl > level, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(form.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                        form.effect?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (current) Tag("Yours", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }

    if (item.acquire.isNotEmpty()) {
        SectionCard(title = "How to get") {
            item.acquire.forEachIndexed { i, way ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AcquisitionEntry(way)
            }
        }
    }

    if (item.isUpgradable) {
        val remaining = UpgradeCalculator.remaining(copy, progress)
        SectionCard(title = if (owned) "Remaining upgrades" else "Upgrade path") {
            if (remaining.isEmpty()) {
                Text("Fully upgraded.", style = MaterialTheme.typography.bodyMedium, color = LocalGameDecor.current.star)
            } else if (remaining.size > 1) {
                Text(
                    "${thousands(remaining.sumOf { it.step.rupees })} Rupees in all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            remaining.forEach { StepCosts(state.catalog, it) }
        }
    }

    item.notes?.let {
        SectionCard(title = "Notes") {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepCosts(catalog: UpgradeCatalog, remaining: RemainingStep) {
    val step = remaining.step
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(remaining.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Stars(remaining.toLevel, remaining.copy.item.maxLevel)
        }
        Text(step.by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        step.materials.forEach { cost ->
            val material = catalog.material(cost.material)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialIcon(material, size = 24.dp, modifier = Modifier.padding(end = 8.dp))
                Text(material.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("×${cost.qty}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Row {
            Text("Rupees", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (step.free) "Free" else thousands(step.rupees), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        step.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AcquisitionEntry(way: Acquisition) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(way.method.label)
            way.cost?.rupees?.let { PriceTag("${thousands(it)} Rupees") }
        }
        Text(way.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(way.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PriceTag(text: String) = Tag(
    text,
    container = MaterialTheme.colorScheme.secondaryContainer,
    content = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun SectionCard(title: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}
