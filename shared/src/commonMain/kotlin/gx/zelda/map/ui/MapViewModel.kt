package gx.zelda.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.DiscoveryState
import gx.zelda.core.SaveFile
import gx.zelda.map.data.MapLayerProvider
import gx.zelda.map.data.MapStorage
import gx.zelda.map.model.LayerData
import gx.zelda.map.model.MapLayer
import gx.zelda.map.model.MapMarker
import gx.zelda.map.model.MapPrefs
import gx.zelda.map.model.isAnonymous
import gx.zelda.map.model.markerKey
import gx.zelda.map.model.visibleMarkers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A layer's chip: whether it's on, and how much of it the active save file has done. */
data class LayerSummary(val layer: MapLayer, val on: Boolean, val done: Int, val total: Int)

/** A save file as the picker shows it: what it has done in each layer. */
data class MapSaveSummary(val file: SaveFile, val done: List<Pair<MapLayer, Int>>)

data class MapUiState(
    val saveFile: SaveFile,
    val saveFiles: List<MapSaveSummary>,
    val prefs: MapPrefs,
    val layers: List<LayerSummary>,
    /** Every marker the map shows. */
    val markers: List<MapMarker>,
    /** Every entry in every layer by marker key, shown or not, so the open sheet survives a filter. */
    val all: Map<String, MapMarker>,
)

/**
 * The map: every layer's entries on the active save file, filtered by the map's preferences and
 * each tracker's spoiler setting, plus the marker whose sheet is open.
 *
 * [focus] is the marker key "Show on map" opened the map for: it is shown and named whatever the
 * filters say, and its sheet opens once the map has loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val storage: MapStorage,
    private val providers: List<MapLayerProvider>,
    focus: String? = null,
) : ViewModel() {
    private val _loadFailed = MutableStateFlow(false)

    /** True when a layer's bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed

    /** Undiscovered entries the user tapped to name this session; nothing is saved. */
    private val revealed = MutableStateFlow(setOfNotNull(focus))
    private val pinned = setOfNotNull(focus)
    private val selectedKey = MutableStateFlow(focus)

    /** The marker to centre on once, then cleared by [focusShown]. */
    private val _pendingFocus = MutableStateFlow(focus)
    val pendingFocus: StateFlow<String?> = _pendingFocus

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    private fun layerData(saveFileId: String): Flow<List<LayerData>> = combine(
        providers.map { p -> combine(p.entries(saveFileId), p.hideUndiscovered) { e, h -> LayerData(p.layer, e, h) } },
    ) { it.toList() }

    private val activeLayers: Flow<Pair<SaveFile, List<LayerData>>> =
        activeFile.flatMapLatest { file -> layerData(file.id).map { file to it } }

    private val summariesByFile: Flow<List<MapSaveSummary>> = combine(
        storage.game.saveFiles.map { file ->
            layerData(file.id).map { layers ->
                MapSaveSummary(file, layers.map { d -> d.layer to d.entries.count { it.state == DiscoveryState.COMPLETED } })
            }
        },
    ) { it.toList() }

    val state: StateFlow<MapUiState?> = combine(
        activeLayers,
        summariesByFile,
        storage.prefs,
        revealed,
    ) { (file, layers), summaries, prefs, revealed ->
        MapUiState(
            saveFile = file,
            saveFiles = summaries,
            prefs = prefs,
            layers = layers.map { d ->
                LayerSummary(
                    layer = d.layer,
                    on = d.layer.id !in prefs.hiddenLayers,
                    done = d.entries.count { it.state == DiscoveryState.COMPLETED },
                    total = d.entries.size,
                )
            },
            markers = visibleMarkers(layers, prefs, revealed, pinned),
            all = layers.flatMap { d ->
                d.entries.map { e ->
                    val key = markerKey(d.layer.id, e.id)
                    key to MapMarker(d.layer, e, isAnonymous(e, d.hideUndiscovered, key in revealed))
                }
            }.toMap(),
        )
    }
        .catch { _loadFailed.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The marker whose sheet is open, kept up to date as it changes. */
    val selected: StateFlow<MapMarker?> = combine(state, selectedKey) { state, key -> key?.let { state?.all?.get(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(key: String) = selectedKey.update { key }

    fun dismiss() = selectedKey.update { null }

    fun focusShown() = _pendingFocus.update { null }

    /** Names one undiscovered entry until the map is closed. */
    fun reveal(key: String) = revealed.update { it + key }

    /** Edits always target the file that was on screen when the user acted. */
    fun setState(marker: MapMarker, newState: DiscoveryState) {
        val file = state.value?.saveFile?.id ?: return
        val provider = providers.firstOrNull { it.layer.id == marker.layer.id } ?: return
        viewModelScope.launch { provider.setState(file, marker.entry.id, newState) }
    }

    fun setLayerOn(layerId: String, on: Boolean) = updatePrefs {
        it.copy(hiddenLayers = if (on) it.hiddenLayers - layerId else it.hiddenLayers + layerId)
    }

    fun setHideCompleted(hide: Boolean) = updatePrefs { it.copy(hideCompleted = hide) }

    fun setUndiscoveredAsDots(show: Boolean) = updatePrefs { it.copy(undiscoveredAsDots = show) }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }

    private fun updatePrefs(transform: (MapPrefs) -> MapPrefs) {
        viewModelScope.launch { storage.updatePrefs(transform) }
    }

    private var mapView: MapView? = null

    /** The map view, kept here so it keeps its position while a tracker is opened on top of it. */
    internal val view: MapView get() = mapView ?: MapView().also { mapView = it }

    override fun onCleared() {
        mapView?.shutdown()
    }
}
