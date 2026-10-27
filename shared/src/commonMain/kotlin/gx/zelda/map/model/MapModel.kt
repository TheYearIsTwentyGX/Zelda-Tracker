package gx.zelda.map.model

import gx.zelda.core.DiscoveryState
import gx.zelda.shrines.scan.WorldPoint
import kotlinx.serialization.Serializable

/** Which in-game map icon a layer's markers use; each entry's state picks its variant (see [markerLook]). */
enum class MarkerKind {
    SHRINE,
    KOROK,
    TOWER,

    /** Overworld bosses: each entry names its kind's icon in [MapEntry.icon]. */
    BOSS,

    /** A checklist's items: the layer's [MapLayer.icon], or an entry's own, on a state-coloured disc. */
    CHECKLIST,
}

/** One toggleable set of markers on the map, such as every shrine. */
data class MapLayer(
    /** Stable id, saved in [MapPrefs.hiddenLayers] and used in marker keys. */
    val id: String,
    /** The chip's label, e.g. "Shrines". */
    val title: String,
    /** One entry, for "Undiscovered shrine". */
    val noun: String,
    /** What each state is called in this layer, in order; a layer only offers the states listed. */
    val stateLabels: Map<DiscoveryState, String>,
    val kind: MarkerKind,
    /** Whether markers this close together merge into a counted cluster at low zoom. */
    val clustered: Boolean = false,
    /** The tracker setting that hides undiscovered entries, as its menu names it; null if the layer has none. */
    val spoilerSetting: String? = null,
    /** The icon a [MarkerKind.CHECKLIST] layer's markers draw, unless an entry names its own. */
    val icon: MarkerIcon? = null,
    /** What an anonymous marker's sheet calls it, as its tracker's hidden rows do; null says "Undiscovered <noun>". */
    val hiddenLabel: String? = null,
) {
    val states: List<DiscoveryState> get() = stateLabels.keys.toList()

    fun label(state: DiscoveryState): String = stateLabels[state] ?: state.label

    /** The clusterer this layer's markers join, one per layer so clusters never mix layers; null if unclustered. */
    val clustererId: String? get() = if (clustered) "cluster:$id" else null
}

/** One place on the map and where the active save file stands with it. */
data class MapEntry(
    /** The id its tracker uses. */
    val id: String,
    val name: String,
    /** A second line under the name, e.g. the shrine's trial. */
    val subtitle: String,
    val region: String,
    val world: WorldPoint,
    val state: DiscoveryState,
    /** From a DLC. The Champions' Ballad's shrines have their own icon. */
    val dlc: Boolean = false,
    /** The entry's own icon, in a layer whose entries differ in kind (bosses); null uses the layer's. */
    val icon: MarkerIcon? = null,
    /** How many things the place holds, shown as a "×3" badge when more than one (a chest of Might Crystals). */
    val count: Int = 1,
)

/** A marker the map draws: an entry, its layer, and whether its tracker's spoiler setting hides its name. */
data class MapMarker(val layer: MapLayer, val entry: MapEntry, val anonymous: Boolean) {
    val key: String get() = markerKey(layer.id, entry.id)
}

fun markerKey(layerId: String, entryId: String): String = "$layerId:$entryId"

/** Map viewing preferences, shared across save files. */
@Serializable
data class MapPrefs(
    /** Ids of the layers switched off. */
    val hiddenLayers: Set<String> = emptySet(),
    val hideCompleted: Boolean = false,
    /** Show entries a tracker's spoiler setting hides as unnamed grey markers instead of leaving them off. */
    val undiscoveredAsDots: Boolean = false,
)

/**
 * A layer's entries on the active save file, with its tracker's "Hide undiscovered" setting, and
 * how many more the story hasn't unlocked yet (left out of [entries], as their tracker leaves them out).
 */
data class LayerData(val layer: MapLayer, val entries: List<MapEntry>, val hideUndiscovered: Boolean, val locked: Int = 0)

/**
 * Whether [entry]'s name is a spoiler: its tracker hides undiscovered entries, it is undiscovered,
 * and the user hasn't revealed it this session. Mirrors the trackers' own hidden rows.
 */
fun isAnonymous(entry: MapEntry, hideUndiscovered: Boolean, revealed: Boolean): Boolean =
    hideUndiscovered && entry.state == DiscoveryState.UNDISCOVERED && !revealed

/**
 * Every marker the map shows.
 * - Layers in [MapPrefs.hiddenLayers] show nothing, and [MapPrefs.hideCompleted] drops completed entries.
 * - An entry its tracker hides as a spoiler is left off the map, the way the game's own map leaves
 *   off what you haven't found, or shown as an unnamed dot with [MapPrefs.undiscoveredAsDots].
 * - [revealed] keys show their names; [pinned] keys (the entry "Show on map" opened) always show, named.
 */
fun visibleMarkers(
    layers: List<LayerData>,
    prefs: MapPrefs,
    revealed: Set<String> = emptySet(),
    pinned: Set<String> = emptySet(),
): List<MapMarker> = layers.flatMap { data ->
    val layerOn = data.layer.id !in prefs.hiddenLayers
    data.entries.mapNotNull { entry ->
        val key = markerKey(data.layer.id, entry.id)
        val anonymous = isAnonymous(entry, data.hideUndiscovered, key in revealed)
        when {
            key in pinned -> MapMarker(data.layer, entry, anonymous = false)
            !layerOn -> null
            prefs.hideCompleted && entry.state == DiscoveryState.COMPLETED -> null
            anonymous && !prefs.undiscoveredAsDots -> null
            else -> MapMarker(data.layer, entry, anonymous)
        }
    }
}

/** What a cluster of markers shows: how many it holds, how many aren't completed, and the least-done state, which colours it. */
data class ClusterSummary(val total: Int, val remaining: Int, val state: DiscoveryState)

/** Anonymous markers count as undiscovered, so a cluster never gives away a hidden entry's state. */
fun clusterSummary(markers: List<MapMarker>): ClusterSummary {
    val states = markers.map { if (it.anonymous) DiscoveryState.UNDISCOVERED else it.entry.state }
    return ClusterSummary(
        total = states.size,
        remaining = states.count { it != DiscoveryState.COMPLETED },
        state = states.minByOrNull { it.ordinal } ?: DiscoveryState.COMPLETED,
    )
}

/** A point on the map image, as a fraction of its width and height from the top-left corner. */
data class MapPoint(val x: Double, val y: Double)

/** The part of a game's world its map art covers, in the game's units: x west to east, z north to south. */
data class WorldBounds(val minX: Double, val maxX: Double, val minZ: Double, val maxZ: Double) {
    fun toMap(p: WorldPoint): MapPoint = MapPoint((p.x - minX) / (maxX - minX), (p.z - minZ) / (maxZ - minZ))

    fun toWorld(p: MapPoint): WorldPoint = WorldPoint(minX + p.x * (maxX - minX), minZ + p.y * (maxZ - minZ))
}

/**
 * Converts BotW world positions to its bundled map. The map covers the playable area, x −5000…5000
 * (west to east) and z −4000…4000 (north to south); see docs/korok-puzzle-types.md.
 */
object MapProjection {
    const val MIN_X = -5000.0
    const val MAX_X = 5000.0
    const val MIN_Z = -4000.0
    const val MAX_Z = 4000.0

    val bounds = WorldBounds(MIN_X, MAX_X, MIN_Z, MAX_Z)

    fun toMap(p: WorldPoint): MapPoint = bounds.toMap(p)

    fun toWorld(p: MapPoint): WorldPoint = bounds.toWorld(p)
}
