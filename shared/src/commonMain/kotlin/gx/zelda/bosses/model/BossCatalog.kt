package gx.zelda.bosses.model

import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.core.SaveFile
import kotlinx.serialization.Serializable

/** A kind of overworld boss, which the tracker gives its own tab: Hinox, Talus, Molduga or Lynel. */
@Serializable
data class BossKind(
    val id: String,
    val name: String,
    val plural: String,
    /** Kilton's Medal of Honor for beating every one of this kind; Lynels have none. */
    val medal: String? = null,
    /** How many spawns the medal counts: the base game's, without DLC or Master Mode-only ones. */
    val medalTotal: Int? = null,
    /** This kind's variants, as Compendium entry ids, in display order. */
    val variants: List<String>,
)

/** One fixed overworld boss spawn. Positions and ids come from objmap.zeldamods.org; see docs/overworld-bosses.md. */
@Serializable
data class BossSpawn(
    /** HashId of the boss actor in the game's map data, which is also objmap's id for it. */
    val hashId: Long,
    /** Map section holding the actor, e.g. `E-6`. */
    val mapSection: String,
    /** A [BossKind] id. */
    val kind: String,
    /** The Compendium entry id of the boss placed here (e.g. `blue_hinox`); for a Lynel that scales, its weakest colour. */
    val variant: String,
    val region: String,
    /** World position in game units: x east, z south, y height. */
    val x: Double,
    val y: Double,
    val z: Double,
    /** Nearest named place on the map (inside Hyrule Castle, the room), as a location hint. */
    val nearestPlace: String,
    /** The variant's Compendium drops. */
    val drops: List<String> = emptyList(),
    /** Added by The Champions' Ballad; doesn't count toward a Medal of Honor. */
    val dlc: Boolean = false,
    /** Only placed on Master Mode files. */
    val masterModeOnly: Boolean = false,
    /** A Lynel whose colour rises with the world level, from [variant] up. */
    val scalesWithWorldLevel: Boolean = false,
    /** The game exempts it from Master Mode raising enemies a tier. */
    val noMasterModeRankUp: Boolean = false,
    /** Other actors that are the same fight at the same place (one per colour, or one per quest stage). */
    val otherHashIds: List<Long> = emptyList(),
    val note: String? = null,
) {
    /** The id progress is stored under. */
    val id: String get() = hashId.toString()

    /** Counts toward its kind's Medal of Honor, if the kind has one. */
    val countsForMedal: Boolean get() = !dlc && !masterModeOnly

    /** objmap centred on this spawn with its details open; `?id=` takes MapType,MapName,HashId. */
    val objmapUrl: String get() = "https://objmap.zeldamods.org/#/map/z7,$x,$z?id=MainField,$mapSection,$hashId"
}

@Serializable
data class BossCatalog(
    /** Description of the coordinate system used by [BossSpawn.x], [BossSpawn.y] and [BossSpawn.z]. */
    val coordinates: String,
    /** The tower regions, in tower order. */
    val regions: List<String>,
    val kinds: List<BossKind>,
    val spawns: List<BossSpawn>,
) {
    /** The spawns a save file has: Master Mode-only ones are left out of Normal Mode files. */
    fun spawnsFor(file: SaveFile): List<BossSpawn> =
        if (file.masterMode) spawns else spawns.filter { !it.masterModeOnly }

    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<BossSpawn>): List<Pair<String, List<BossSpawn>>> {
        val grouped = visible.groupBy { it.region }
        return regions.mapNotNull { region -> grouped[region]?.let { region to it } }
    }
}

/** The bosses and the Compendium entries they're named and numbered by, loaded together. */
data class BossData(val catalog: BossCatalog, val compendium: CompendiumCatalog) {
    /** The Compendium entry for [variant]; the dataset test checks every variant has one. */
    fun entry(variant: String): CompendiumEntry? = compendium.entry(variant)

    /** The variant's Compendium name, e.g. "Blue Hinox". */
    fun variantName(variant: String): String = entry(variant)?.name ?: variant
}
