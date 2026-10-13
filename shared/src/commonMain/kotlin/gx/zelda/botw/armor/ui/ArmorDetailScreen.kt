package gx.zelda.botw.armor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gx.zelda.botw.armor.logic.ArmorCalculator
import gx.zelda.botw.armor.logic.UpgradeTier
import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.core.ui.StarGold
import gx.zelda.core.ui.Stars
import gx.zelda.core.ui.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmorDetailScreen(
    state: ArmorUiState,
    pieceId: String,
    viewModel: ArmorViewModel,
    onBack: () -> Unit,
) {
    val piece = state.catalog.piece(pieceId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(piece?.name ?: "Unknown armor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (piece == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This armor piece isn't in the catalog.")
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PieceDetail(state, piece, viewModel)
        }
    }
}

@Composable
private fun PieceDetail(state: ArmorUiState, piece: ArmorPiece, viewModel: ArmorViewModel) {
    val progress = state.progress
    val owned = progress.of(piece.id).owned
    val level = ArmorCalculator.level(piece, progress)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(piece.slot.label)
        Tag(piece.source.label)
        state.catalog.set(piece.setId)?.let { Tag(it.name) }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Collected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = owned, onCheckedChange = { viewModel.setOwned(piece.id, it) })
        }
        if (piece.isUpgradable) {
            Text("Upgrade level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                for (lvl in 0..piece.maxLevel) {
                    SegmentedButton(
                        selected = owned && level == lvl,
                        onClick = { viewModel.setLevel(piece.id, lvl) },
                        enabled = owned,
                        shape = SegmentedButtonDefaults.itemShape(lvl, piece.maxLevel + 1),
                        icon = {},
                    ) { Text(if (lvl == 0) "—" else "★$lvl") }
                }
            }
            if (!owned) {
                Text(
                    "Mark this piece as collected to track its upgrades.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "This piece can't be upgraded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard(title = "Stats") {
        DefenseTable(piece, current = if (owned) level else null)
        Row {
            Text("Effect", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(piece.effect ?: "None", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }

    SetBonusCard(state.catalog, piece, state)

    val remaining = ArmorCalculator.remainingUpgrades(piece, progress)
    if (piece.isUpgradable) {
        SectionCard(title = if (owned) "Remaining upgrades" else "Upgrade path") {
            piece.upgradeNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (remaining.isEmpty()) {
                Text("Fully upgraded.", style = MaterialTheme.typography.bodyMedium, color = StarGold)
            }
            remaining.forEach { tier -> TierCosts(state.catalog, tier, piece.maxLevel) }
        }
    }
}

@Composable
private fun DefenseTable(piece: ArmorPiece, current: Int?) {
    Row(Modifier.fillMaxWidth()) {
        piece.defense.forEachIndexed { lvl, def ->
            val highlight = lvl == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    if (lvl == 0) "Base" else "★$lvl",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$def",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
            if (lvl != piece.defense.lastIndex) Box(Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun SetBonusCard(catalog: ArmorCatalog, piece: ArmorPiece, state: ArmorUiState) {
    val set = catalog.set(piece.setId) ?: return
    val status = ArmorCalculator.setBonusStatus(catalog, set, state.progress)
    SectionCard(title = set.name) {
        if (!status.hasBonus) {
            Text("This set has no set bonus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set bonus: ${set.bonus}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                SetBonusBadge(status)
            }
            val requirement = if (status.pieces.any { it.isUpgradable }) {
                "Requires a piece in every slot at ★${ArmorCalculator.SET_BONUS_LEVEL} or higher."
            } else {
                "Active while wearing a piece in every slot."
            }
            Text(requirement, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status.slots.filter { it.options.size > 1 }.forEach { slot ->
                Text(
                    "Any one ${slot.slot.label.lowercase()} piece below counts toward the bonus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        status.pieces.forEach { p ->
            val pOwned = state.progress.of(p.id).owned
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (p.id == piece.id) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (pOwned) Stars(ArmorCalculator.level(p, state.progress), p.maxLevel) else Tag("Missing")
            }
        }
    }
}

@Composable
private fun TierCosts(catalog: ArmorCatalog, tier: UpgradeTier, maxLevel: Int) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("To ", style = MaterialTheme.typography.labelLarge)
            Stars(tier.toLevel, maxLevel)
        }
        tier.costs.forEach { cost ->
            Row {
                Text(catalog.material(cost.material).name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("×${cost.qty}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

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
