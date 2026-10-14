package gx.zelda.koroks.ui

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
import gx.zelda.core.DiscoveryState
import kotlin.math.roundToInt

/** One Korok: how its puzzle type is solved, where it is, and a link to it on objmap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorokDetailScreen(state: KorokUiState, korokId: String, viewModel: KorokViewModel, onBack: () -> Unit) {
    val korok = state.catalog.koroks.firstOrNull { it.id == korokId } ?: return
    val type = state.data.typeOf(korok)
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(type.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Korok ${korok.id} · ${state.saveFile.label}",
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
            val current = state.progress.state(korok.id)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = DiscoveryState.entries
                options.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = current == option,
                        onClick = { viewModel.setState(korok.id, option) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        label = { Text(if (option == DiscoveryState.COMPLETED) "Collected" else option.label) },
                    )
                }
            }

            Section("How to solve") {
                if (type.id == "other") {
                    Text(korok.internalType, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(type.description, style = MaterialTheme.typography.bodyLarge)
            }

            Section("Where") {
                Text("${korok.region} Tower region", style = MaterialTheme.typography.bodyLarge)
                Text("Nearest named place: ${korok.nearestPlace}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "x ${korok.x.roundToInt()} · z ${korok.z.roundToInt()} · height ${korok.y.roundToInt()} " +
                        "(game units; x east, z south)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = { uriHandler.openUri(korok.objmapUrl) }) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Open in objmap")
            }
            Text(
                "objmap.zeldamods.org centres its map on this Korok and opens its game data: the puzzle's " +
                    "type, its linked objects and its exact position.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
