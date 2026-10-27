package gx.zelda.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.adventure.ui.CloseOnBack
import gx.zelda.adventure.ui.HideSystemBars
import gx.zelda.core.DiscoveryState
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.map.model.MapChrome
import gx.zelda.map.model.MapMarker
import gx.zelda.map.model.MarkerContainer
import ovh.plrapps.mapcompose.api.getLayoutSize
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scroll
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.setVisibleAreaPadding
import ovh.plrapps.mapcompose.ui.MapUI

/**
 * The map of Hyrule with every tracker's places on it, for the active save file. Tapping a marker
 * opens its details: in a bottom sheet, or in a short landscape window a panel down the right with
 * the map still in use beside it, and the top bar's controls moved into the chip row
 * ([MapChrome]). [onOpenInTracker] gets the layer id and the entry id.
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
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // On a phone on its side, the bars' height goes to the map (MapChrome).
        val chrome = MapChrome.barsVisible(maxWidth.value, maxHeight.value)
        HideSystemBars(hidden = !chrome)
        val container = MapChrome.markerContainer(maxWidth.value, maxHeight.value)
        val panelWidth = MapChrome.panelWidthDp(maxWidth.value).dp
        Scaffold(
            topBar = {
                if (chrome) TopAppBar(
                    title = {
                        Column {
                            Text("Map", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state != null) SavePicker(state, viewModel)
                        }
                    },
                    navigationIcon = { MenuOrBackButton(onBack) },
                    actions = {
                        AboutButton(onClick = { showAbout = true })
                        if (state != null) MapMenu(state, viewModel)
                    },
                )
            },
        ) { padding ->
            if (state == null) {
                LoadingOrError(loadFailed, "The map couldn't be loaded.", padding)
                return@Scaffold
            }
            val panel = selected?.takeIf { container == MarkerContainer.PANEL }
            // The chips move clear of the panel; the map stays full width under it.
            val clear = Modifier.padding(end = if (panel != null) panelWidth else 0.dp)
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    if (chrome) {
                        LayerChips(state, viewModel, clear.fillMaxWidth())
                    } else {
                        // The bars are hidden: the chip row carries what they had.
                        Row(clear.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MenuOrBackButton(onBack)
                            LayerChips(state, viewModel, Modifier.weight(1f))
                            if (state.saveFiles.size > 1) SavePicker(state, viewModel)
                            AboutButton(onClick = { showAbout = true })
                            MapMenu(state, viewModel)
                        }
                    }
                    MapPane(state, viewModel, panelWidth.takeIf { panel != null }, Modifier.weight(1f).fillMaxWidth())
                }
                panel?.let { marker ->
                    MarkerPanel(
                        marker = marker,
                        onClose = viewModel::dismiss,
                        onSetState = { viewModel.setState(marker, it) },
                        onReveal = { viewModel.reveal(marker.key) },
                        onOpenInTracker = { onOpenInTracker(marker.layer.id, marker.entry.id) },
                        modifier = Modifier.align(Alignment.TopEnd).width(panelWidth).fillMaxHeight(),
                    )
                }
            }
        }
        if (container == MarkerContainer.SHEET) selected?.let { marker ->
            MarkerSheet(
                marker = marker,
                onDismiss = viewModel::dismiss,
                onSetState = { viewModel.setState(marker, it) },
                onReveal = { viewModel.reveal(marker.key) },
                onOpenInTracker = { onOpenInTracker(marker.layer.id, marker.entry.id) },
            )
        }
    }
    if (showAbout) AboutDialog(viewModel.spec.about, onDismiss = { showAbout = false })
}

@Composable
private fun SavePicker(state: MapUiState, viewModel: MapViewModel) {
    SaveFilePicker(
        current = state.saveFile,
        options = state.saveFiles.map { summary ->
            SaveFileOption(summary.file, summary.done.joinToString(" · ") { (layer, done) -> "$done ${layer.title.lowercase()}" })
        },
        onSelect = viewModel::setSaveFile,
    )
}

@Composable
private fun AboutButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Info, contentDescription = "About the map")
    }
}

/**
 * The map. While the side panel is open ([panelWidth]), the part it covers isn't counted as showing:
 * "Show on map" centres in what's left, and a marker tapped under the panel is panned out from it.
 * Tapping the open marker again closes the panel, as tapping the open square does on the HW:DE maps.
 */
@Composable
private fun MapPane(state: MapUiState, viewModel: MapViewModel, panelWidth: Dp?, modifier: Modifier) {
    val view = viewModel.view
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val pendingFocus by viewModel.pendingFocus.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(selected?.key.takeIf { panelWidth != null })

    DisposableEffect(view) {
        view.onMarkerTap = { key -> if (key == open) viewModel.dismiss() else viewModel.select(key) }
        onDispose { view.onMarkerTap = {} }
    }
    LaunchedEffect(state.markers) { view.sync(state.markers) }
    LaunchedEffect(selected) { view.selected = selected?.key }
    LaunchedEffect(panelWidth) { view.state.setVisibleAreaPadding(right = panelWidth ?: 0.dp) }
    val density = LocalDensity.current
    LaunchedEffect(selected?.key, panelWidth) {
        val marker = selected ?: return@LaunchedEffect
        val inset = with(density) { panelWidth?.toPx() } ?: return@LaunchedEffect
        val at = view.spec.world.toMap(marker.entry.world)
        val x = (at.x * view.spec.tiles.fullWidth * view.state.scale - view.state.scroll.x).toFloat()
        val visible = view.state.getLayoutSize().width - inset
        if (MapChrome.needsReveal(x, visible, margin = with(density) { REVEAL_MARGIN.toPx() })) {
            view.state.scrollTo(at.x, at.y, destScale = view.state.scale)
        }
    }
    LaunchedEffect(pendingFocus, state.all) {
        val marker = pendingFocus?.let { state.all[it] } ?: return@LaunchedEffect
        viewModel.focusShown()
        val at = view.spec.world.toMap(marker.entry.world)
        view.state.scrollTo(at.x, at.y, destScale = MapView.FOCUS_SCALE)
    }
    MapUI(modifier.background(mapBackground(view.spec)), state = view.state)
}

/** How close to the panel (or the left edge) a tapped marker may sit before the map pans it into view. */
private val REVEAL_MARGIN = 40.dp

@Composable
private fun LayerChips(state: MapUiState, viewModel: MapViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.layers.forEach { summary ->
            FilterChip(
                selected = summary.on,
                onClick = { viewModel.setLayerOn(summary.layer.id, !summary.on) },
                label = {
                    Text(
                        "${summary.layer.title} ${summary.done} / ${summary.total}" +
                            if (summary.locked > 0) " · ${summary.locked} later" else "",
                    )
                },
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
                        "Places a tracker's spoiler setting hides appear as unnamed grey markers",
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

/** A marker's details in a bottom sheet, opened all the way so nothing needs dragging up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerSheet(
    marker: MapMarker,
    onDismiss: () -> Unit,
    onSetState: (DiscoveryState) -> Unit,
    onReveal: () -> Unit,
    onOpenInTracker: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MarkerDetails(marker, onClose = null, onSetState, onReveal, onOpenInTracker, Modifier.navigationBarsPadding())
    }
}

/**
 * A marker's details down the right of a short landscape window ([MapChrome.markerContainer]), with
 * the map still usable beside it. Tapping another marker swaps what it shows; ✕, back or tapping the
 * same marker closes it.
 */
@Composable
private fun MarkerPanel(
    marker: MapMarker,
    onClose: () -> Unit,
    onSetState: (DiscoveryState) -> Unit,
    onReveal: () -> Unit,
    onOpenInTracker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CloseOnBack(enabled = true, onBack = onClose)
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        MarkerDetails(
            marker,
            onClose,
            onSetState,
            onReveal,
            onOpenInTracker,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp).navigationBarsPadding(),
        )
    }
}

/** One marker, as the sheet and the side panel both show it; [onClose] adds a ✕ for the panel. */
@Composable
private fun MarkerDetails(
    marker: MapMarker,
    onClose: (() -> Unit)?,
    onSetState: (DiscoveryState) -> Unit,
    onReveal: () -> Unit,
    onOpenInTracker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layer = marker.layer
    val entry = marker.entry
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MapMarkerIcon(marker, selected = false, scale = 1.25f)
            Column(Modifier.weight(1f)) {
                Text(
                    if (marker.anonymous) layer.hiddenLabel ?: "Undiscovered ${layer.noun}" else entry.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${entry.region} · ${layer.label(entry.state)}" + if (entry.count > 1) " · ×${entry.count}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
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
        // The choices come first, so the panel's short height needs no scrolling to tick.
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
        if (entry.subtitle.isNotEmpty()) Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AboutDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("About the map") },
        text = { Text(text) },
    )
}
