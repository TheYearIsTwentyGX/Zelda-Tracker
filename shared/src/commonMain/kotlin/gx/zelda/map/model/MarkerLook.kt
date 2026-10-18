package gx.zelda.map.model

import gx.zelda.core.DiscoveryState
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * The in-game map icons the markers draw, each bundled as `drawable/<resourceName>` by
 * scripts/build-map-icons.py (see docs/map-icons.md).
 */
enum class MarkerIcon(val resourceName: String) {
    /** Blue frame, orange glyph: the game's shrine not yet completed. */
    SHRINE("map_shrine"),
    /** All blue: a completed shrine. */
    SHRINE_DONE("map_shrine_done"),
    /** The Champions' Ballad's shrine icon, with its spire, not yet completed. */
    DLC_SHRINE("map_shrine_dlc"),
    DLC_SHRINE_DONE("map_shrine_dlc_done"),
    /** An orange Sheikah Tower, not yet activated. */
    TOWER("map_tower"),
    TOWER_ACTIVE("map_tower_active"),
    /** The Korok Seed; its state shows as the colour of the disc behind it. */
    KOROK("map_korok"),
    /** Kilton's Medal of Honor: Hinox, for every Hinox and Stalnox; on a state-coloured disc like the Korok. */
    HINOX("map_boss_hinox"),
    /** Medal of Honor: Talus. */
    TALUS("map_boss_talus"),
    /** Medal of Honor: Molduga. */
    MOLDUGA("map_boss_molduga"),
    /** The Lynel Mask: there's no Lynel medal. */
    LYNEL("map_boss_lynel"),
}

/** The icon for an overworld boss of kind [kindId] (`hinox`, `talus`, `molduga` or `lynel`). */
fun bossIcon(kindId: String): MarkerIcon = when (kindId) {
    "talus" -> MarkerIcon.TALUS
    "molduga" -> MarkerIcon.MOLDUGA
    "lynel" -> MarkerIcon.LYNEL
    else -> MarkerIcon.HINOX
}

/** How a marker draws: its icon, whether it is greyed out, and whether it sits on a state-coloured disc. */
data class MarkerLook(
    val icon: MarkerIcon,
    /** The state the marker shows; an anonymous marker shows as undiscovered. */
    val state: DiscoveryState,
    /** Undiscovered (or hidden by a spoiler setting): drawn in greyscale and faded. */
    val greyed: Boolean,
    /** Drawn on a disc in the state's colour, for an icon with no state variants of its own. */
    val disc: Boolean,
)

/**
 * The look for [marker]. Shrines and towers change icon with their state, as in the game: a
 * shrine's glyph turns from orange to blue when it is completed and a tower from orange to blue
 * when activated. Koroks keep the seed and bosses their kind's icon, and show their state on the
 * disc behind it. In a layer with a "found" state, undiscovered markers are greyed; a tower not
 * yet activated isn't, since the layer has no "found". An anonymous marker never gives away more
 * than its tracker's hidden rows do: it uses the layer's plain icon (never the DLC one) and shows
 * as undiscovered. A boss keeps its kind's icon, as the boss tracker's hidden rows say "Unfound Hinox".
 */
fun markerLook(marker: MapMarker): MarkerLook {
    val state = if (marker.anonymous) DiscoveryState.UNDISCOVERED else marker.entry.state
    val done = state == DiscoveryState.COMPLETED
    val dlc = marker.entry.dlc && !marker.anonymous
    val icon = when (marker.layer.kind) {
        MarkerKind.SHRINE -> when {
            dlc -> if (done) MarkerIcon.DLC_SHRINE_DONE else MarkerIcon.DLC_SHRINE
            else -> if (done) MarkerIcon.SHRINE_DONE else MarkerIcon.SHRINE
        }
        MarkerKind.TOWER -> if (done) MarkerIcon.TOWER_ACTIVE else MarkerIcon.TOWER
        MarkerKind.KOROK -> MarkerIcon.KOROK
        MarkerKind.BOSS -> marker.entry.icon ?: MarkerIcon.HINOX
    }
    return MarkerLook(
        icon = icon,
        state = state,
        greyed = state == DiscoveryState.UNDISCOVERED && DiscoveryState.FOUND in marker.layer.stateLabels,
        disc = marker.layer.kind == MarkerKind.KOROK || marker.layer.kind == MarkerKind.BOSS,
    )
}

/**
 * [scale] (from [iconScale]) for a [kind]'s markers. Bosses stop shrinking at three quarters size:
 * there are only about a hundred, spread out, so they have room, and the medals' ribbons are what
 * tell the kinds apart.
 */
fun layerIconScale(kind: MarkerKind, scale: Float): Float =
    if (kind == MarkerKind.BOSS) scale.coerceAtLeast(MIN_BOSS_ICON_SCALE) else scale

/**
 * How big markers draw at map scale [mapScale], as a fraction of their full size: half size
 * zoomed right out, where the whole map fits a phone and 136 shrines would otherwise pile up,
 * growing to full size by scale 0.5 (two zoom levels in). Rounded to 0.05, so markers only
 * redraw when it changes a step.
 */
fun iconScale(mapScale: Double): Float {
    if (mapScale <= 0.0) return MIN_ICON_SCALE
    val t = ((log2(mapScale) - log2(SMALL_AT)) / (log2(FULL_AT) - log2(SMALL_AT))).coerceIn(0.0, 1.0)
    val scale = MIN_ICON_SCALE + (1 - MIN_ICON_SCALE) * t
    return (scale * 20).roundToInt() / 20f
}

private const val MIN_ICON_SCALE = 0.5f
private const val MIN_BOSS_ICON_SCALE = 0.75f
private const val SMALL_AT = 0.125
private const val FULL_AT = 0.5
