package gx.zelda.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gx.zelda.core.DiscoveryState
import gx.zelda.map.data.MapTiles
import gx.zelda.map.model.ClusterSummary
import gx.zelda.map.model.MapLayer
import gx.zelda.map.model.MapMarker
import gx.zelda.map.model.MapProjection
import gx.zelda.map.model.MarkerIcon
import gx.zelda.map.model.MarkerKind
import gx.zelda.map.model.clusterSummary
import gx.zelda.map.model.iconScale
import gx.zelda.map.model.layerIconScale
import gx.zelda.map.model.markerLook
import org.jetbrains.compose.resources.painterResource
import ovh.plrapps.mapcompose.api.ClusterScaleThreshold
import ovh.plrapps.mapcompose.api.addClusterer
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.ui.layout.Fit
import ovh.plrapps.mapcompose.ui.state.MapState
import ovh.plrapps.mapcompose.ui.state.markers.model.RenderingStrategy

/** Sea colour behind and around the map art, matching the padding of the edge tiles. */
internal val MapBackground = Color(0xFF283C46)

/**
 * The MapCompose state for the bundled map, and the markers on it. [sync] brings the markers in
 * line with a new list: only added and removed markers touch MapCompose, and a marker whose state
 * changed just recomposes, since each one draws whatever [shown] holds for its key.
 */
internal class MapView {
    val state = MapState(MapTiles.LEVELS, MapTiles.FULL_WIDTH, MapTiles.FULL_HEIGHT, MapTiles.TILE_SIZE) {
        minimumScaleMode(Fit)
        maxScale(MAX_SCALE)
        scale(0.0)
    }.apply {
        addLayer(MapTiles.provider)
        onMarkerClick { id, _, _ -> onMarkerTap(id) }
    }

    var onMarkerTap: (String) -> Unit = {}

    /** How big markers draw at the current zoom; see [iconScale]. */
    val markerScale: Float by derivedStateOf { iconScale(state.scale) }

    /** The marker whose sheet is open, drawn with a ring. */
    var selected: String? by mutableStateOf(null)

    private val shown = mutableStateMapOf<String, MapMarker>()
    private val clusterers = mutableSetOf<String>()

    fun sync(markers: List<MapMarker>) {
        val next = markers.associateBy { it.key }
        (shown.keys - next.keys).forEach { key ->
            state.removeMarker(key)
            shown.remove(key)
        }
        next.forEach { (key, marker) ->
            val isNew = key !in shown
            shown[key] = marker
            if (isNew) add(marker)
        }
    }

    private fun add(marker: MapMarker) {
        val key = marker.key
        val clusterer = marker.layer.clustererId
        if (clusterer != null && clusterers.add(clusterer)) {
            val layer = marker.layer
            state.addClusterer(
                clusterer,
                clusteringThreshold = 40.dp,
                scaleThreshold = ClusterScaleThreshold.FixedScale(CLUSTER_UNTIL_SCALE),
            ) { ids -> { ClusterBadge(layer, clusterSummary(ids.mapNotNull { shown[it] })) } }
        }
        val at = MapProjection.toMap(marker.entry.world)
        state.addMarker(
            key,
            at.x,
            at.y,
            relativeOffset = Offset(-0.5f, -0.5f),
            zIndex = zIndex(marker.layer.kind),
            clickableAreaScale = Offset(2f, 2f),
            renderingStrategy = clusterer?.let { RenderingStrategy.Clustering(it) } ?: RenderingStrategy.Default,
        ) {
            shown[key]?.let { MapMarkerIcon(it, selected = selected == key, scale = layerIconScale(it.layer.kind, markerScale)) }
        }
    }

    fun shutdown() = state.shutdown()

    companion object {
        /** Zoom past the top level, one pixel per world unit, by this much. */
        const val MAX_SCALE = 2.5

        /** Above this scale Koroks are drawn one by one. */
        const val CLUSTER_UNTIL_SCALE = 0.6

        /** The scale "Show on map" zooms to. */
        const val FOCUS_SCALE = 1.2

        private fun zIndex(kind: MarkerKind): Float = when (kind) {
            MarkerKind.KOROK -> 0f
            MarkerKind.BOSS -> 0.5f
            MarkerKind.SHRINE -> 1f
            MarkerKind.TOWER -> 2f
        }
    }
}

/** A state's colour: blue done, orange found, grey undiscovered. In a layer with no "found" (towers), not done is orange. */
internal fun stateColor(layer: MapLayer, state: DiscoveryState): Color = when {
    state == DiscoveryState.COMPLETED -> Color(0xFF3BB4F0)
    state == DiscoveryState.FOUND || DiscoveryState.FOUND !in layer.stateLabels -> Color(0xFFF5A524)
    else -> Color(0xFFB0B0B0)
}

/** Each icon's size at [iconScale] 1. */
private fun fullSize(icon: MarkerIcon): Dp = when (icon) {
    MarkerIcon.KOROK -> 20.dp
    MarkerIcon.HINOX, MarkerIcon.TALUS, MarkerIcon.MOLDUGA, MarkerIcon.LYNEL -> 24.dp
    MarkerIcon.TOWER, MarkerIcon.TOWER_ACTIVE -> 30.dp
    else -> 26.dp
}

private val GreyedOut = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * A marker's in-game icon at [scale] of its full size (see [markerLook]): greyed out when
 * undiscovered, on a state-coloured disc for Koroks and bosses, and in a white ring when [selected].
 */
@Composable
internal fun MapMarkerIcon(marker: MapMarker, selected: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    val look = markerLook(marker)
    val size = fullSize(look.icon) * scale
    Box(
        modifier
            .size(if (selected) size + 8.dp else size)
            .then(if (selected) Modifier.background(Color(0x66FFFFFF), CircleShape).border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (look.disc) {
            Box(Modifier.size(size).background(stateColor(marker.layer, look.state), CircleShape).border(1.dp, Color(0xCC000000), CircleShape))
        }
        val drawable = markerDrawable(look.icon)
        if (drawable == null) {
            Box(Modifier.size(size * 0.6f).background(stateColor(marker.layer, look.state), CircleShape))
        } else {
            Image(
                painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(size),
                alpha = if (look.greyed) 0.8f else 1f,
                colorFilter = if (look.greyed) GreyedOut else null,
            )
        }
    }
}

/** A counted cluster: how many aren't done yet (✓ when none), coloured by the least-done one. */
@Composable
private fun ClusterBadge(layer: MapLayer, summary: ClusterSummary) {
    Box(
        Modifier
            .size(26.dp)
            .background(stateColor(layer, summary.state), CircleShape)
            .border(1.5.dp, Color(0xCC000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (summary.remaining == 0) "✓" else summary.remaining.toString(),
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
