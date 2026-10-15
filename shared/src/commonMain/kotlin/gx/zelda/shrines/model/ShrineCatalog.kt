package gx.zelda.shrines.model

import gx.zelda.core.groupByRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What waits inside a shrine. */
@Serializable
enum class TrialType(val label: String) {
    @SerialName("puzzle") PUZZLE("Puzzle"),
    /**
     * A fight: a Test of Strength, Ta'loh Naeg's combat lesson, or in TotK a Proving Grounds or
     * Combat Training shrine.
     */
    @SerialName("combat") COMBAT("Combat"),
    /** No trial inside, just the monk (BotW) or Rauru's Blessing (TotK): the challenge was reaching it. */
    @SerialName("blessing") BLESSING("Blessing"),
}

/** Where a shrine stands. Only TotK's shrines say; BotW's are all [SURFACE]. */
@Serializable
enum class ShrineLayer(val label: String) {
    /** On a sky island. */
    @SerialName("sky") SKY("Sky"),
    @SerialName("surface") SURFACE("Surface"),
    /** Inside a cave on the surface. */
    @SerialName("cave") CAVE("Cave"),
}

@Serializable
data class Shrine(
    val id: String,
    /** Without the word "Shrine"; each monk shares his shrine's name. */
    val name: String,
    /** The region the shrine is listed under; one of [ShrineCatalog.regions]. */
    val region: String,
    /** The subtitle shown on entering, e.g. "Magnesis Trial" or "A Major Test of Strength". */
    val trial: String,
    val type: TrialType,
    /** The Shrine Quest that reveals or opens it, if any. */
    val quest: String? = null,
    /** The Champions' Ballad's shrines. */
    val dlc: Boolean = false,
    /**
     * Whether completing it gives a Spirit Orb (or a Light of Blessing). Every base-game shrine
     * does, and so do the four Champions' Ballad shrines on the Great Plateau; the DLC's other
     * twelve give an emblem instead.
     */
    val orb: Boolean = true,
    /** Where it is, in one line. */
    val location: String,
    /** The game's own map area it stands in or above, e.g. "Hebra Mountains Sky" (TotK only). */
    val area: String? = null,
    val layer: ShrineLayer = ShrineLayer.SURFACE,
    /** World position in game units (x east, z south, y height), where the dataset has one. */
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
)

/**
 * A TotK Lightroot: it grows in the Depths directly beneath [shrine], and its name is that
 * shrine's name spelled backwards. Lighting it is tracked separately from the shrine.
 */
@Serializable
data class Lightroot(
    val id: String,
    /** Without the word "Lightroot", e.g. "Oromuwak" under Kawumoro Shrine. */
    val name: String,
    /** The [Shrine.id] of the shrine above it. */
    val shrine: String,
    /** World position in game units; y is far below zero in the Depths. */
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
)

/** The two things a Goddess Statue sells. */
@Serializable
enum class UpgradeKind {
    @SerialName("heart") HEART,
    @SerialName("stamina") STAMINA,
}

/** What a game's shrines reward, with the wording the planner card uses for it. */
@Serializable
enum class BlessingCurrency(val title: String, val one: String, val many: String) {
    @SerialName("spirit_orb") SPIRIT_ORB("Spirit Orbs", "orb", "orbs"),
    @SerialName("light_of_blessing") LIGHT_OF_BLESSING("Lights of Blessing", "Light", "Lights"),
    ;

    /** "1 orb", "5 Lights". */
    fun count(n: Int): String = if (n == 1) "1 $one" else "$n $many"
}

/** What Spirit Orbs (or Lights of Blessing) buy at a Goddess Statue, and the limits on both. */
@Serializable
data class OrbUpgrades(
    val orbsPerUpgrade: Int,
    val startingHearts: Int,
    val maxHearts: Int,
    val startingStaminaWheels: Int,
    /** Stamina Vessels from the starting wheels to the maximum; [VESSELS_PER_WHEEL] make a wheel. */
    val maxStaminaVessels: Int,
    /**
     * Heart Containers awarded outside the Goddess Statues: one per Divine Beast freed in BotW,
     * one per boss in TotK.
     */
    val bossHearts: Int,
    val currency: BlessingCurrency = BlessingCurrency.SPIRIT_ORB,
    /**
     * A one-off upgrade that costs nothing and is the player's choice of a Heart Container or a
     * Stamina Vessel, named after where it comes from (TotK's "A Call from the Depths").
     */
    val freeUpgrade: String? = null,
) {
    /** Heart Containers a Goddess Statue will sell at most, before any boss hearts. */
    val maxHeartsBought: Int get() = maxHearts - startingHearts

    companion object {
        const val VESSELS_PER_WHEEL = 5
    }
}

@Serializable
data class ShrineCatalog(
    /** Tower regions in the order the list shows them: Sheikah Towers in BotW, Skyview Towers in TotK. */
    val regions: List<String>,
    val upgrades: OrbUpgrades,
    val shrines: List<Shrine>,
    /** TotK's Lightroots; empty for a game without them. */
    val lightroots: List<Lightroot> = emptyList(),
) {
    val base: List<Shrine> get() = shrines.filterNot { it.dlc }
    val dlc: List<Shrine> get() = shrines.filter { it.dlc }

    private val shrinesById: Map<String, Shrine> by lazy { shrines.associateBy { it.id } }
    private val lightrootsByShrine: Map<String, Lightroot> by lazy { lightroots.associateBy { it.shrine } }

    fun shrine(id: String): Shrine? = shrinesById[id]

    /** The shrine above [lightroot]. */
    fun shrineOf(lightroot: Lightroot): Shrine = shrinesById.getValue(lightroot.shrine)

    /** The Lightroot beneath [shrine], or null (sky shrines have none). */
    fun lightrootOf(shrine: Shrine): Lightroot? = lightrootsByShrine[shrine.id]

    /** A Lightroot is listed under its shrine's region. */
    fun regionOf(lightroot: Lightroot): String = shrineOf(lightroot).region

    /** How many base-game shrines are in [completed]. */
    fun baseCompleted(completed: Set<String>): Int = base.count { it.id in completed }

    /** How many of [ids] are shrines in this catalog. */
    fun completedCount(ids: Set<String>): Int = shrines.count { it.id in ids }

    /** How many of [ids] are Lightroots in this catalog. */
    fun lightrootCount(ids: Set<String>): Int = lightroots.count { it.id in ids }

    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<Shrine>): List<Pair<String, List<Shrine>>> = groupByRegion(regions, visible) { it.region }

    /** [visible] split by their shrines' regions in [regions] order. */
    fun lightrootsByRegion(visible: List<Lightroot>): List<Pair<String, List<Lightroot>>> =
        groupByRegion(regions, visible, ::regionOf)
}
