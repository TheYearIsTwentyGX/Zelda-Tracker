package gx.zelda.kinstones.model

import gx.zelda.core.groupByRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A Kinstone Piece's colour. Gold pieces are story items, and their fusions open the way on. */
@Serializable
enum class KinstoneColor(val label: String) {
    @SerialName("green") GREEN("Green"),
    @SerialName("blue") BLUE("Blue"),
    @SerialName("red") RED("Red"),
    @SerialName("gold") GOLD("Gold"),
}

/**
 * What a fusion gives, for the result filter. A fusion can give more than one (a beanstalk with a
 * Piece of Heart and a chest of rupees), so each one lists all that apply.
 */
@Serializable
enum class FusionReward(val label: String) {
    /** A Piece of Heart, or the one Heart Container. */
    @SerialName("heart") HEART("Hearts"),
    /** Equipment, upgrades and Joy Butterflies: Big Wallet, Empty Bottle, Magical Boomerang… */
    @SerialName("item") ITEM("Items & upgrades"),
    /** A Blade Brother's dojo opens, to learn a sword technique. */
    @SerialName("technique") TECHNIQUE("Sword techniques"),
    /** The story fusions and other new ways through: a boulder, a tornado, a portal, a cave. */
    @SerialName("way") WAY("Opens the way"),
    /** Someone new arrives or something changes in town (a shop, a house, a Goron). */
    @SerialName("people") PEOPLE("People & shops"),
    @SerialName("fountain") FOUNTAIN("Fairy Fountains"),
    @SerialName("beanstalk") BEANSTALK("Beanstalks"),
    /** A Golden Octorok, Rope or Tektite, worth a lot of rupees when beaten. */
    @SerialName("golden_monster") GOLDEN_MONSTER("Golden monsters"),
    @SerialName("kinstone") KINSTONE("Kinstone Pieces"),
    @SerialName("rupees") RUPEES("Rupees"),
    @SerialName("shells") SHELLS("Mysterious Shells"),
}

/** Who or what Link fuses with. */
@Serializable
enum class PartnerKind {
    @SerialName("character") CHARACTER,
    /** A Mysterious Statue, Wall or Cloud, or the Source of the Flow. */
    @SerialName("object") OBJECT,
}

@Serializable
data class KinstoneFusion(
    val id: String,
    /** Thonky's fusion number, 1–100, in roughly the order the story allows them. */
    val number: Int,
    /** The fusion's id in the game's own data (zeldaret/tmc), `0x01`–`0x64`. */
    val gameId: String,
    /** Who or what to fuse with, e.g. "Mayor Hagen" or "Mysterious Statue (left)". */
    val partner: String,
    val partnerKind: PartnerKind = PartnerKind.CHARACTER,
    /**
     * Offered by whichever random fuser the game hands it to, so it has no fixed partner; it's
     * listed under [KinstoneCatalog.randomRegion].
     */
    val random: Boolean = false,
    /** Where the partner is: one of [KinstoneCatalog.regions]. */
    val region: String,
    /** Where to find the partner, in a line; and when they're there, if that changes. */
    val location: String,
    val color: KinstoneColor,
    /**
     * The shape of the half Link supplies, as Thonky names it, e.g. "slant"; null for Gold, whose
     * pieces only ever fit their own statue, cloud or slab.
     */
    val shape: String? = null,
    /** What happens, and where: e.g. "A Chest appears in Minish Woods containing 200 Rupees". */
    val result: String,
    /** The region where [result] appears; null when it's no place (a shop's new stock). */
    val resultRegion: String? = null,
    val rewards: List<FusionReward>,
    /** The story stage it's first possible in, an index into [KinstoneCatalog.stages] (1-based). */
    val stage: Int,
    /** What has to be done first, beyond reaching [stage]; null when nothing. */
    val requires: String? = null,
    /** How it can be missed, or a time when it can't be done; null when it can't be missed. */
    val missable: String? = null,
    val note: String? = null,
) {
    /** One of the nine story fusions that need a Gold Kinstone. */
    val isGold: Boolean get() = color == KinstoneColor.GOLD

    /** The piece Link needs, e.g. "Red jagged teeth" or "Gold". */
    val pieceName: String get() = shape?.let { "${color.label} $it" } ?: color.label
}

@Serializable
data class KinstoneCatalog(
    /** Partner regions in the order the list shows them; the random fusions' pseudo-region first. */
    val regions: List<String>,
    /**
     * When each stage starts, e.g. "After the Ocarina of Wind"; `stages[0]` is stage 1. The stages
     * are Zelda Wiki's, which split the story at the points where new fusions become possible.
     */
    val stages: List<String>,
    val fusions: List<KinstoneFusion>,
) {
    /** The region the random fusions are listed under, ahead of the real places. */
    val randomRegion: String get() = regions.first()

    /** "After the Ocarina of Wind" for [fusion]'s stage. */
    fun stageLabel(fusion: KinstoneFusion): String = stages.getOrElse(fusion.stage - 1) { "Stage ${fusion.stage}" }

    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<KinstoneFusion>): List<Pair<String, List<KinstoneFusion>>> =
        groupByRegion(regions, visible) { it.region }

    /** How many of [fused] are fusions in this catalog. */
    fun fusedCount(fused: Set<String>): Int = fusions.count { it.id in fused }

    fun fusion(id: String): KinstoneFusion? = fusions.firstOrNull { it.id == id }
}
