package gx.zelda.compendium.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.compendium.model.EntrySource
import gx.zelda.compendium.model.PictureSale
import gx.zelda.compendium.model.compendiumNumber
import gx.zelda.core.ui.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompendiumDetailScreen(
    state: CompendiumUiState,
    entryId: String,
    viewModel: CompendiumViewModel,
    onBack: () -> Unit,
) {
    val entry = state.catalog.entry(entryId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry?.name ?: "Unknown entry", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        if (entry == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This entry isn't in the Compendium.")
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EntryDetail(state, entry, viewModel)
        }
    }
}

@Composable
private fun EntryDetail(state: CompendiumUiState, entry: CompendiumEntry, viewModel: CompendiumViewModel) {
    val masterMode = state.saveFile.masterMode
    val number = entry.numberIn(masterMode)
    val photographed = entry.id in state.photographed

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            number?.let { "No. ${compendiumNumber(it)}" } ?: "Master Mode only",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Tag(if (entry.food) "${entry.category.label} · Food" else entry.category.label)
        if (entry.source != EntrySource.BASE) Tag(entry.source.label)
    }
    otherModeNumber(entry, masterMode)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Photographed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = photographed,
                onCheckedChange = { viewModel.setPhotographed(entry.id, it) },
                modifier = Modifier.semantics { contentDescription = "${entry.name} photographed" },
            )
        }
        if (!entry.isIn(masterMode)) {
            Text(
                "${state.saveFile.label} doesn't have this entry; it only appears in Master Mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    entry.note?.let {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (entry.limited) Tag("Limited", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
        }
    }

    SectionCard {
        Text("Common locations", style = MaterialTheme.typography.titleMedium)
        Text(
            entry.locations.ifEmpty { listOf("Unknown") }.joinToString("\n") { "• $it" },
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (entry.drops.isNotEmpty()) {
        SectionCard {
            Text(dropsTitle(entry), style = MaterialTheme.typography.titleMedium)
            Text(entry.drops.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }

    SectionCard {
        Text("Can't get the shot?", style = MaterialTheme.typography.titleMedium)
        Text(
            when (entry.picture) {
                PictureSale.STANDARD ->
                    "After the \"Sunshroom Sensing\" side quest, Symin at the Hateno Ancient Tech Lab sells this picture for 100 rupees."
                PictureSale.ELITE ->
                    "Symin at the Hateno Ancient Tech Lab sells this as an Elite Enemy Picture for 500 rupees, " +
                        "but only after Dark Beast Ganon is defeated (and \"Sunshroom Sensing\" is done)."
                PictureSale.NONE -> "Symin doesn't sell this picture."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Master Mode: No. 147" on a Normal file, and the reverse, so either number can be looked up. */
private fun otherModeNumber(entry: CompendiumEntry, masterMode: Boolean): String? {
    val other = entry.numberIn(!masterMode) ?: return null
    return if (masterMode) "Normal Mode: No. ${compendiumNumber(other)}" else "Master Mode: No. ${compendiumNumber(other)}"
}

/** Monsters list what they drop; creatures, ore deposits and chests yield materials. */
private fun dropsTitle(entry: CompendiumEntry): String = when (entry.category) {
    CompendiumCategory.MONSTERS -> "Common drops"
    else -> "Recoverable materials"
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
