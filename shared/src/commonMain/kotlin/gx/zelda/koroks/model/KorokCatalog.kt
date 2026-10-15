package gx.zelda.koroks.model

import gx.zelda.core.groupByRegion
import kotlinx.serialization.Serializable

/** One Korok Seed puzzle. Positions and ids come from objmap.zeldamods.org; see docs/korok-puzzle-types.md. */
@Serializable
data class Korok(
    /** objmap's Korok id: the tower region's letter plus a number, e.g. `Z07`. */
    val id: String,
    /** HashId of the hidden Korok actor in the game's map data. */
    val hashId: Long,
    /** Map section holding the actor, e.g. `E-6`. */
    val mapSection: String,
    val region: String,
    /** World position in game units: x east, z south, y height. */
    val x: Double,
    val y: Double,
    val z: Double,
    /** Nearest named place on the map, as a location hint. */
    val nearestPlace: String,
    /** objmap/radar's `korok_type` for this Korok; for [puzzleType] `other` it names the one-off puzzle. */
    val internalType: String,
    /** The puzzle's game actors, minus the Korok itself and logic tags. */
    val actors: List<String> = emptyList(),
    /** A [KorokPuzzleType] id, mapped from [internalType]. */
    val puzzleType: String? = null,
    /** Where [puzzleType] came from: `internal` when it is mapped from objmap's [internalType]. */
    val typeSource: String? = null,
) {
    /** objmap centred on this Korok with its details open; `?id=` takes MapType,MapName,HashId. */
    val objmapUrl: String get() = "https://objmap.zeldamods.org/#/map/z7,$x,$z?id=MainField,$mapSection,$hashId"
}

/** One of Hestu's inventory upgrades: a pouch (named in the singular, e.g. "Weapon"), its starting slots and the seeds each extra slot costs. */
@Serializable
data class HestuPouch(
    val id: String,
    val name: String,
    val startingSlots: Int,
    /** Seeds for each upgrade in turn; the list's length is how many upgrades the pouch takes. */
    val costs: List<Int>,
) {
    val maxSlots: Int get() = startingSlots + costs.size
}

@Serializable
data class HestuCosts(val pouches: List<HestuPouch>) {
    /** Seeds to max every pouch: 441 in BotW. */
    val total: Int get() = pouches.sumOf { it.costs.sum() }
}

@Serializable
data class KorokCatalog(
    /** Description of the coordinate system used by [Korok.x], [Korok.y] and [Korok.z]. */
    val coordinates: String,
    /** The tower regions, in tower order. */
    val regions: List<String>,
    val hestu: HestuCosts,
    val koroks: List<Korok>,
) {
    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<Korok>): List<Pair<String, List<Korok>>> = groupByRegion(regions, visible) { it.region }

    /** How many of [ids] are Koroks in this catalog. */
    fun count(ids: Set<String>): Int = koroks.count { it.id in ids }
}

@Serializable
data class KorokPuzzleType(
    val id: String,
    val name: String,
    val group: String,
    /** How to solve it, for players. */
    val description: String,
    /** What counts and what doesn't, for whoever classifies seeds. */
    val rules: List<String>,
    /** objmap `korok_type` values that map onto this type. */
    val internalTypes: List<String>,
    /** Ids of example Koroks. */
    val examples: List<String>,
)

@Serializable
data class KorokPuzzleTypes(
    val groups: List<String>,
    val types: List<KorokPuzzleType>,
)

/** The Koroks and the puzzle types they're sorted into, loaded together. */
data class KorokData(val catalog: KorokCatalog, val types: KorokPuzzleTypes) {
    private val typesById = types.types.associateBy { it.id }

    /** The puzzle type of [korok]; `other` covers any id the taxonomy doesn't know. */
    fun typeOf(korok: Korok): KorokPuzzleType =
        typesById[korok.puzzleType] ?: typesById.getValue("other")
}
