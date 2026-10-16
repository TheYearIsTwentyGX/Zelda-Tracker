package gx.zelda.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.core.DiscoveryState
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.map.model.MapMarker
import gx.zelda.map.model.MapProjection
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.ui.MapUI

/**
 * The map of Hyrule with every tracker's places on it, for the active save file. Tapping a marker
 * opens its sheet; [onOpenInTracker] gets the layer id and the entry id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    state: MapUiState?,
    loadFailed: Boolean,
    viewModel: MapViewModel,
    onOpenInTracker: (layerId: String, entryId: String) -> Unit,
    onBack: () -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Map", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { summary ->
                                    SaveFileOption(
                                        summary.file,
                                        summary.done.joinToString(" · ") { (layer, done) -> "$done ${layer.title.lowercase()}" },
                                    )
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
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "About the map")
                    }
                    if (state != null) MapMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The map couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            LayerChips(state, viewModel)
            MapPane(state, viewModel, Modifier.weight(1f).fillMaxWidth())
        }
    }

    val selected by viewModel.selected.collectAsStateWithLifecycle()
    selected?.let { marker ->
        MarkerSheet(
            marker = marker,
            onDismiss = viewModel::dismiss,
            onSetState = { viewModel.setState(marker, it) },
            onReveal = { viewModel.reveal(marker.key) },
            onOpenInTracker = { onOpenInTracker(marker.layer.id, marker.entry.id) },
        )
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun MapPane(state: MapUiState, viewModel: MapViewModel, modifier: Modifier) {
    val view = viewModel.view
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val pendingFocus by viewModel.pendingFocus.collectAsStateWithLifecycle()

    DisposableEffect(view) {
        view.onMarkerTap = viewModel::select
        onDispose { view.onMarkerTap = {} }
    }
    LaunchedEffect(state.markers) { view.sync(state.markers) }
    LaunchedEffect(selected) { view.selected = selected?.key }
    LaunchedEffect(pendingFocus, state.all) {
        val marker = pendingFocus?.let { state.all[it] } ?: return@LaunchedEffect
        viewModel.focusShown()
        val at = MapProjection.toMap(marker.entry.world)
        view.state.scrollTo(at.x, at.y, destScale = MapView.FOCUS_SCALE)
    }
    MapUI(modifier.background(MapBackground), state = view.state)
}

@Composable
private fun LayerChips(state: MapUiState, viewModel: MapViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.layers.forEach { summary ->
            FilterChip(
                selected = summary.on,
                onClick = { viewModel.setLayerOn(summary.layer.id, !summary.on) },
                label = { Text("${summary.layer.title} ${summary.done} / ${summary.total}") },
            )
        }
    }
}

@Composable
private fun MapMenu(state: MapUiState, viewModel: MapViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide completed") },
            trailingIcon = { Checkbox(checked = state.prefs.hideCompleted, onCheckedChange = null) },
            onClick = {
                viewModel.setHideCompleted(!state.prefs.hideCompleted)
                open = false
            },
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text("Show hidden places")
                    Text(
                        "Places a tracker's \"Hide undiscovered\" hides appear as unnamed grey markers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = { Checkbox(checked = state.prefs.undiscoveredAsDots, onCheckedChange = null) },
            onClick = {
                viewModel.setUndiscoveredAsDots(!state.prefs.undiscoveredAsDots)
                open = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerSheet(
    marker: MapMarker,
    onDismiss: () -> Unit,
    onSetState: (DiscoveryState) -> Unit,
    onReveal: () -> Unit,
    onOpenInTracker: () -> Unit,
) {
    val layer = marker.layer
    val entry = marker.entry
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapMarkerIcon(marker, selected = false, scale = 1.25f)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (marker.anonymous) "Undiscovered ${layer.noun}" else entry.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${entry.region} · ${layer.label(entry.state)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (marker.anonymous) {
                Text(
                    "Hidden by \"${layer.spoilerSetting ?: "Hide undiscovered"}\". Reveal it to see what it is and mark it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onReveal) { Text("Reveal") }
                return@Column
            }
            Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium)
            val states = layer.states
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                states.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = entry.state == option,
                        onClick = { onSetState(option) },
                        shape = SegmentedButtonDefaults.itemShape(i, states.size),
                        label = { Text(layer.label(option), maxLines = 1) },
                    )
                }
            }
            OutlinedButton(onClick = onOpenInTracker) { Text("Open in tracker") }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("About the map") },
        text = {
            Text(
                "Map art © Nintendo, from Breath of the Wild's game files, via objmap.zeldamods.org. " +
                    "It is bundled with the app, so the map works offline.\n\n" +
                    "Shrine, tower and Korok positions are objmap's too, and so are the shrine and tower icons, " +
                    "traced from the game's. As in the game, a shrine's glyph is orange until it's completed and " +
                    "a tower is orange until it's activated; both turn blue when done. Korok seeds sit on a blue " +
                    "disc once collected and an orange one when found. Undiscovered places are grey. Numbers on " +
                    "Korok clusters count the seeds not yet collected.",
            )
        },
    )
}
