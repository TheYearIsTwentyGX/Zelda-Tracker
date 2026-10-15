package gx.zelda.bosses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.bosses.model.BossSpawn
import gx.zelda.bosses.model.bossLabel
import gx.zelda.compendium.model.compendiumNumber
import gx.zelda.core.DiscoveryState
import kotlin.math.roundToInt

/** One boss spawn: what it is, its Compendium entry and drops, where it is, and a link to it on objmap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossDetailScreen(state: BossUiState, spawnId: String, viewModel: BossViewModel, onBack: () -> Unit) {
    val spawn = state.spawns.firstOrNull { it.id == spawnId } ?: return
    val kind = state.catalog.kinds.firstOrNull { it.id == spawn.kind }
    val entry = state.data.entry(spawn.variant)
    val name = state.data.variantName(spawn.variant)
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Near ${spawn.nearestPlace} · ${state.saveFile.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            val current = state.progress.state(spawn.id)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = DiscoveryState.entries
                options.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = current == option,
                        onClick = { viewModel.setState(spawn.id, option) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        label = { Text(option.bossLabel) },
                    )
                }
            }
            Text(
                "Defeated means beaten at least once. Bosses come back after a Blood Moon, but the game " +
                    "and Kilton's medals count the first win.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Hyrule Compendium") {
                val number = entry?.numberIn(state.saveFile.masterMode)
                Text(
                    if (number != null) "${compendiumNumber(number)} $name" else name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (spawn.drops.isEmpty()) "No drops listed in the Compendium." else "Drops: ${spawn.drops.joinToString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Section("Counts toward") {
                Text(medalText(spawn, kind?.medal, kind?.plural), style = MaterialTheme.typography.bodyMedium)
            }

            colourNote(spawn, state.saveFile.masterMode, state.data::variantName)?.let { note ->
                Section("Colour") { Text(note, style = MaterialTheme.typography.bodyMedium) }
            }

            spawn.note?.let { note ->
                Section("Note") { Text(note, style = MaterialTheme.typography.bodyMedium) }
            }

            Section("Where") {
                Text("${spawn.region} Tower region", style = MaterialTheme.typography.bodyLarge)
                Text("Nearest named place: ${spawn.nearestPlace}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "x ${spawn.x.roundToInt()} · z ${spawn.z.roundToInt()} · height ${spawn.y.roundToInt()} " +
                        "(game units; x east, z south)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = { uriHandler.openUri(spawn.objmapUrl) }) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Open in objmap")
            }
            Text(
                "objmap.zeldamods.org centres its map on this boss and opens its game data: its equipment, " +
                    "linked objects and exact position. Actor HashId ${spawn.hashId}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun medalText(spawn: BossSpawn, medal: String?, plural: String?): String = when {
    medal == null -> "Nothing: there's no Medal of Honor for ${plural ?: "this kind"}."
    spawn.dlc -> "Not the $medal: Champions' Ballad bosses don't count."
    spawn.masterModeOnly -> "Not the $medal."
    else -> "$medal, from Kilton at Fang and Bone once every one is beaten."
}

/** Why this boss's colour may differ from the one listed, or null when it's fixed. */
internal fun colourNote(spawn: BossSpawn, masterMode: Boolean, variantName: (String) -> String): String? {
    val parts = buildList {
        if (spawn.scalesWithWorldLevel) {
            add(
                "Shown as ${variantName(spawn.variant)}, the weakest it can be. Its colour rises with the world " +
                    "level as you defeat enemies, so later in a playthrough it can be a stronger Lynel.",
            )
        }
        if (masterMode) {
            add(
                if (spawn.noMasterModeRankUp) {
                    "The game exempts this one from Master Mode's rank-up."
                } else {
                    "Master Mode ranks many enemies up a tier, so it may be stronger on this file."
                },
            )
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
