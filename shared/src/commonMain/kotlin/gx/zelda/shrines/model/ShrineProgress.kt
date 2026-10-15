package gx.zelda.shrines.model

import gx.zelda.core.Discoveries
import gx.zelda.core.DiscoverySets
import gx.zelda.core.DiscoveryState
import kotlinx.serialization.Serializable

/** Where a save file stands with one shrine. Completing a shrine implies having found it. */
typealias ShrineState = DiscoveryState

/**
 * One save file's shrines, plus what its Spirit Orbs (Lights of Blessing) have been spent on and,
 * in TotK, its Lightroots. Fields a game doesn't use stay at their defaults, which aren't stored.
 */
@Serializable
data class ShrineProgress(
    override val completed: Set<String> = emptySet(),
    /**
     * Shrines found on this save file, completed or not. Saves from before this field existed
     * only have [completed]; those shrines count as found too, so read [state] rather than this.
     */
    override val found: Set<String> = emptySet(),
    /** Heart Containers bought at Goddess Statues, as entered by the user. */
    val heartsBought: Int = 0,
    /** Stamina Vessels bought at Goddess Statues, as entered by the user. */
    val staminaBought: Int = 0,
    /** Lightroots found and lit (completed), tracked apart from the shrines above them. */
    val lightroots: Discoveries = Discoveries(),
    /**
     * Heart Containers from bosses, as entered by the user, for a game with no tracker that
     * records its bosses (TotK). BotW counts the Divine Beasts in Main Quest Progress instead.
     */
    val bossHearts: Int = 0,
    /** What [OrbUpgrades.freeUpgrade] was taken as; null until it's been claimed. */
    val freeUpgrade: UpgradeKind? = null,
) : DiscoverySets {
    fun withState(shrineId: String, state: ShrineState): ShrineProgress =
        setsWith(shrineId, state).let { (completed, found) -> copy(completed = completed, found = found) }

    fun withLightrootState(lightrootId: String, state: DiscoveryState): ShrineProgress =
        copy(lightroots = lightroots.withState(lightrootId, state))
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class ShrinePrefs(
    val showDlc: Boolean = true,
    /** Spoiler-safe mode: an undiscovered shrine's row shows only its region until revealed. */
    val hideUndiscovered: Boolean = false,
)

/**
 * The Spirit Orb arithmetic for one save file. Orbs come from completed shrines with an
 * [Shrine.orb]; how they were spent is what the user entered, since the app can't see the save.
 * Heart Containers also come from the Divine Beasts freed on the same file, which the Main Quest
 * tracker records.
 */
data class OrbPlan(
    val upgrades: OrbUpgrades,
    /** Orbs earned so far. */
    val earned: Int,
    /** Orbs the game holds in total: 120 in the base game, 124 with the Champions' Ballad. */
    val total: Int,
    val heartsBought: Int,
    val staminaBought: Int,
    /**
     * Boss Heart Containers on this save file: Divine Beasts freed in BotW, bosses beaten
     * (entered by hand) in TotK.
     */
    val beastHearts: Int = 0,
    /** What [OrbUpgrades.freeUpgrade] was taken as, if it has been. */
    val freeUpgrade: UpgradeKind? = null,
) {
    val bought: Int get() = heartsBought + staminaBought

    private val freeHeart: Int get() = if (freeUpgrade == UpgradeKind.HEART) 1 else 0
    private val freeVessel: Int get() = if (freeUpgrade == UpgradeKind.STAMINA) 1 else 0

    /** Orbs not yet traded in. Negative when more was entered as bought than the orbs allow. */
    val inHand: Int get() = earned - bought * upgrades.orbsPerUpgrade

    val isConsistent: Boolean get() = inHand >= 0

    /** Upgrades the orbs in hand pay for right now. */
    val readyToBuy: Int get() = (inHand / upgrades.orbsPerUpgrade).coerceAtLeast(0)

    /** Orbs still to earn before the orbs in hand pay for one more upgrade than now. */
    val toNext: Int get() = if (isConsistent) upgrades.orbsPerUpgrade - inHand % upgrades.orbsPerUpgrade else 0

    /** Orbs left in shrines not yet completed. */
    val unearned: Int get() = total - earned

    /** Upgrades every orb in the game buys. */
    val totalUpgrades: Int get() = total / upgrades.orbsPerUpgrade

    /**
     * Upgrades still to buy, counting orbs not yet earned. The statues never run out first: even
     * with every beast freed they sell 23 + 10, more than the 30 (31) upgrades the orbs buy. In
     * TotK, with every boss heart and the free upgrade, they sell 40 against the Lights' 38.
     */
    val upgradesLeft: Int get() = (totalUpgrades - bought).coerceAtLeast(0)

    /** Hearts on this save file: the starting ones, the bosses', the free one if taken as a heart, and those bought. */
    val hearts: Int get() = upgrades.startingHearts + beastHearts + freeHeart + heartsBought

    /** Heart Containers the Goddess Statues sell at most, given the boss (and free) hearts so far. */
    val maxHeartsBought: Int get() = (upgrades.maxHeartsBought - beastHearts - freeHeart).coerceAtLeast(0)

    /** Heart Containers that weren't bought: the bosses' and the free one. */
    val heartsNotBought: Int get() = beastHearts + freeHeart

    /** Stamina Vessels on this save file, bought or free. */
    val staminaVessels: Int get() = staminaBought + freeVessel

    /** Stamina Vessels the Goddess Statues sell at most, given the free one. */
    val maxStaminaBought: Int get() = upgrades.maxStaminaVessels - freeVessel

    /** More hearts entered than the game allows, e.g. a beast freed after hearts were maxed. */
    val heartsOverMax: Boolean get() = heartsBought > maxHeartsBought

    val heartsLeft: Int get() = (maxHeartsBought - heartsBought).coerceAtLeast(0)

    val staminaLeft: Int get() = (maxStaminaBought - staminaBought).coerceAtLeast(0)

    /**
     * Upgrades to buy to max both hearts and stamina, with every boss heart and the free upgrade
     * collected. In BotW that's 23 + 10 = 33 against 30 bought with every orb (31 with the DLC);
     * in TotK it's 31 + 10 - 1 = 40 against the 38 that 152 Lights buy. Neither game can max both.
     */
    val upgradesToMaxBoth: Int
        get() = upgrades.maxHeartsBought - upgrades.bossHearts + upgrades.maxStaminaVessels -
            (if (upgrades.freeUpgrade != null) 1 else 0)

    companion object {
        /**
         * [includeDlc] counts the DLC shrines' orbs; off when the DLC is hidden. [beastsFreed] is
         * how many Divine Beasts the same save file has freed; null for a game where the boss
         * hearts are entered by hand ([ShrineProgress.bossHearts]).
         */
        fun of(catalog: ShrineCatalog, progress: ShrineProgress, includeDlc: Boolean, beastsFreed: Int? = 0): OrbPlan {
            val orbShrines = catalog.shrines.filter { it.orb && (includeDlc || !it.dlc) }
            return OrbPlan(
                upgrades = catalog.upgrades,
                earned = orbShrines.count { it.id in progress.completed },
                total = orbShrines.size,
                heartsBought = progress.heartsBought,
                staminaBought = progress.staminaBought,
                beastHearts = (beastsFreed ?: progress.bossHearts).coerceIn(0, catalog.upgrades.bossHearts),
                freeUpgrade = progress.freeUpgrade.takeIf { catalog.upgrades.freeUpgrade != null },
            )
        }
    }
}
