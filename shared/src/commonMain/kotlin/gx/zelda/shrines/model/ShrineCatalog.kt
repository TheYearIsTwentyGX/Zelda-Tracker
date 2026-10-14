package gx.zelda.shrines.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What waits inside a shrine. */
@Serializable
enum class TrialType(val label: String) {
    @SerialName("puzzle") PUZZLE("Puzzle"),
    /** A Test of Strength, or another fight such as Ta'loh Naeg's combat lesson. */
    @SerialName("combat") COMBAT("Combat"),
    /** No trial inside, just the monk: the challenge was finding or reaching the shrine. */
    @SerialName("blessing") BLESSING("Blessing"),
}

@Serializable
data class Shrine(
    val id: String,
    /** Without the word "Shrine"; each monk shares his shrine's name. */
    val name: String,
    /** The Sheikah Tower region the shrine sits in; one of [ShrineCatalog.regions]. */
    val region: String,
    /** The subtitle shown on entering, e.g. "Magnesis Trial" or "A Major Test of Strength". */
    val trial: String,
    val type: TrialType,
    /** The Shrine Quest that reveals or opens it, if any. */
    val quest: String? = null,
    /** The Champions' Ballad's shrines. */
    val dlc: Boolean = false,
    /**
     * Whether completing it gives a Spirit Orb. Every base-game shrine does, and so do the four
     * Champions' Ballad shrines on the Great Plateau; the DLC's other twelve give an emblem instead.
     */
    val orb: Boolean = true,
    /** Where it is, in one line. */
    val location: String,
)

/** What Spirit Orbs buy at a Goddess Statue, and the limits on both. */
@Serializable
data class OrbUpgrades(
    val orbsPerUpgrade: Int,
    val startingHearts: Int,
    val maxHearts: Int,
    val startingStaminaWheels: Int,
    /** Stamina Vessels from the starting wheels to the maximum; [VESSELS_PER_WHEEL] make a wheel. */
    val maxStaminaVessels: Int,
    /** Heart Containers awarded outside the Goddess Statues (the Divine Beasts); not tracked. */
    val bossHearts: Int,
) {
    /** Heart Containers a Goddess Statue will sell at most, before any boss hearts. */
    val maxHeartsBought: Int get() = maxHearts - startingHearts

    companion object {
        const val VESSELS_PER_WHEEL = 5
    }
}

@Serializable
data class ShrineCatalog(
    /** Tower regions in the order the list shows them. */
    val regions: List<String>,
    val upgrades: OrbUpgrades,
    val shrines: List<Shrine>,
) {
    val base: List<Shrine> get() = shrines.filterNot { it.dlc }
    val dlc: List<Shrine> get() = shrines.filter { it.dlc }

    /** How many base-game shrines are in [completed]. */
    fun baseCompleted(completed: Set<String>): Int = base.count { it.id in completed }

    /** How many of [ids] are shrines in this catalog. */
    fun completedCount(ids: Set<String>): Int = shrines.count { it.id in ids }

    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<Shrine>): List<Pair<String, List<Shrine>>> {
        val grouped = visible.groupBy { it.region }
        return regions.mapNotNull { region -> grouped[region]?.let { region to it } }
    }
}
