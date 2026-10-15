package gx.zelda.shrines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.DiscoveryState
import gx.zelda.shrines.model.Lightroot
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineLayer
import kotlin.math.roundToInt

/**
 * One shrine and the Lightroot beneath it, each with its own state on the active save file. With
 * spoilers hidden, either half that hasn't been found stays hidden until tapped, as in the lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrineDetailScreen(state: ShrineUiState, shrineId: String, viewModel: ShrineViewModel, onBack: () -> Unit) {
    val catalog = state.catalog
    val shrine = catalog.shrine(shrineId) ?: return
    val lightroot = catalog.lightrootOf(shrine)
    val shrineHidden = state.isHidden(shrine)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (shrineHidden) "Undiscovered shrine" else "${shrine.name} Shrine",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${shrine.region} Tower · ${state.saveFile.label}",
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
            PairCard(heading = "Shrine") {
                if (shrineHidden) {
                    HiddenHalf("This shrine", onReveal = { viewModel.reveal(shrine.id) })
                } else {
                    StateButtons(state.progress.state(shrine.id), doneLabel = "Completed") { viewModel.setState(shrine.id, it) }
                    ShrineFacts(shrine)
                }
            }
            PairCard(heading = "Lightroot in the Depths") {
                when {
                    lightroot == null -> Text(noLightrootReason(shrine), style = MaterialTheme.typography.bodyMedium)
                    state.isHidden(lightroot) -> HiddenHalf("This Lightroot", onReveal = { viewModel.revealLightroot(lightroot.id) })
                    else -> {
                        StateButtons(state.progress.lightroots.state(lightroot.id), doneLabel = "Lit") {
                            viewModel.setLightrootState(lightroot.id, it)
                        }
                        LightrootFacts(lightroot, shrine)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShrineFacts(shrine: Shrine) {
    Text(shrine.trial, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text(
        listOfNotNull(shrine.type.label, shrine.layer.label.takeIf { shrine.layer != ShrineLayer.SURFACE }).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    shrine.quest?.let {
        Text("Shrine Quest: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    }
    Text(shrine.location, style = MaterialTheme.typography.bodyLarge)
    shrine.area?.let {
        Text("Map area: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Position(shrine.x, shrine.y, shrine.z)
}

@Composable
private fun LightrootFacts(lightroot: Lightroot, shrine: Shrine) {
    Text("${lightroot.name} Lightroot", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text(
        "Directly beneath ${shrine.name} Shrine; its name is the shrine's spelled backwards. Lighting it " +
            "brightens the Depths around it and makes it a travel point.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Position(lightroot.x, lightroot.y, lightroot.z)
}

private fun noLightrootReason(shrine: Shrine): String = when (shrine.layer) {
    ShrineLayer.SKY -> "None. The 32 shrines on sky islands are the only ones with no Lightroot beneath them."
    else -> "None."
}

/** Undiscovered / Found / [doneLabel], as the list rows' checkbox and pin set them. */
@Composable
private fun StateButtons(current: DiscoveryState, doneLabel: String, onSelect: (DiscoveryState) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val options = DiscoveryState.entries
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = current == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(if (option == DiscoveryState.COMPLETED) doneLabel else option.label) },
            )
        }
    }
}

@Composable
private fun HiddenHalf(what: String, onReveal: () -> Unit) {
    Text(
        "$what hasn't been found on this save file, and undiscovered ones are hidden.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onReveal) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Reveal")
    }
}

@Composable
private fun Position(x: Double?, y: Double?, z: Double?) {
    if (x == null || z == null) return
    Text(
        "x ${x.roundToInt()} · z ${z.roundToInt()}" + (y?.let { " · height ${it.roundToInt()}" } ?: "") +
            " (game units; x east, z south)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PairCard(heading: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                heading,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
