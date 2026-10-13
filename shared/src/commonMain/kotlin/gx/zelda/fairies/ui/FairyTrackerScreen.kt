package gx.zelda.fairies.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.fairies.model.FairyCatalog
import gx.zelda.fairies.model.GreatFairy
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.Stars
import gx.zelda.core.ui.Tag

/** Great Fairy Fountains: where each one is, and which are open on the active save file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FairyTrackerScreen(state: FairyUiState?, viewModel: FairyViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Great Fairy Fountains", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map {
                                    SaveFileOption(it.file, "${it.unlocked} / ${state.catalog.fairies.size} ${state.catalog.openedWord}")
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "summary") { SummaryCard(state) }
            items(state.catalog.fairies, key = { it.id }) { fairy ->
                FairyCard(
                    fairy,
                    paid = state.catalog.isPaid,
                    unlocked = fairy.id in state.unlocked,
                    onUnlockedChange = { viewModel.setUnlocked(fairy.id, it) },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(state: FairyUiState) {
    val catalog = state.catalog
    val open = state.unlockedCount
    val total = catalog.fairies.size
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$open / $total ${catalog.openedWord}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Stars(open, total, modifier = Modifier.semantics { contentDescription = "Armor upgradable to $open stars" })
        }
        if (catalog.isPaid) PaidSummary(catalog, open, total) else QuestSummary(open, total)
    }
}

/** TotK: fairies are freed by escorting a Stable Trotters musician, not bought. */
@Composable
private fun QuestSummary(open: Int, total: Int) {
    Text(
        when (open) {
            total -> "Every Great Fairy is free, so armor can be upgraded all the way to ★$total."
            0 -> "Armor can't be upgraded until a Great Fairy is freed."
            else -> "Armor upgrades currently stop at ★$open."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Each fountain is sealed until you bring a member of the Stable Trotters to play for her. " +
            "Every Great Fairy you free lets the fairies upgrade armor one star higher, whichever one it is.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** BotW: each fountain costs more rupees than the last. */
@Composable
private fun PaidSummary(catalog: FairyCatalog, open: Int, total: Int) {
    val next = catalog.nextCost(open)
    Text(
        if (next == null) {
            "Every fountain is open, so armor can be upgraded all the way to ★$total."
        } else {
            "The next fountain you open costs ${rupees(next)}; opening all the rest costs " +
                "${rupees(catalog.remainingCost(open))}. " +
                if (open == 0) "Armor can't be upgraded until one is open." else "Armor upgrades currently stop at ★$open."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "The price goes by how many fountains are already open, not by which fairy it is: " +
            catalog.unlockCosts.joinToString(" → ") { grouped(it) } + " rupees. " +
            "Each fountain you open lets the Great Fairies upgrade armor one star higher.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "fountains open" for BotW, "fairies freed" for TotK. */
private val FairyCatalog.openedWord: String get() = if (isPaid) "fountains open" else "fairies freed"

@Composable
private fun FairyCard(fairy: GreatFairy, paid: Boolean, unlocked: Boolean, onUnlockedChange: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Great Fairy ${fairy.name}", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(fairy.region)
                    if (unlocked) {
                        Tag(
                            if (paid) "Open" else "Freed",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Switch(
                checked = unlocked,
                onCheckedChange = onUnlockedChange,
                modifier = Modifier.semantics {
                    contentDescription = if (paid) "${fairy.name}'s fountain open" else "${fairy.name} freed"
                },
            )
        }
        Text(fairy.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(fairy.directions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fairy.unlockQuest?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Freed by", style = MaterialTheme.typography.labelLarge)
                Text("Side adventure: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (fairy.nearby.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Nearby", style = MaterialTheme.typography.labelLarge)
                Text(fairy.nearby.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        fairy.notes?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

private fun rupees(amount: Int) = "${grouped(amount)} rupees"

/** 10000 → "10,000". */
private fun grouped(amount: Int): String = amount.toString().reversed().chunked(3).joinToString(",").reversed()
