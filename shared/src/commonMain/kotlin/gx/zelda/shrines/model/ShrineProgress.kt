package gx.zelda.shrines.model

import gx.zelda.core.DiscoveryState
import kotlinx.serialization.Serializable

/** Where a save file stands with one shrine. Completing a shrine implies having found it. */
typealias ShrineState = DiscoveryState

/** One save file's shrines, plus what its Spirit Orbs have been spent on. */
@Serializable
data class ShrineProgress(
    val completed: Set<String> = emptySet(),
    /**
     * Shrines found on this save file, completed or not. Saves from before this field existed
     * only have [completed]; those shrines count as found too, so read [state] rather than this.
     */
    val found: Set<String> = emptySet(),
    /** Heart Containers bought at Goddess Statues, as entered by the user. */
    val heartsBought: Int = 0,
    /** Stamina Vessels bought at Goddess Statues, as entered by the user. */
    val staminaBought: Int = 0,
) {
    fun state(shrineId: String): ShrineState = DiscoveryState.of(shrineId, completed, found)

    fun isFound(shrineId: String): Boolean = shrineId in found || shrineId in completed

    fun withState(shrineId: String, state: ShrineState): ShrineProgress = when (state) {
        ShrineState.UNDISCOVERED -> copy(completed = completed - shrineId, found = found - shrineId)
        ShrineState.FOUND -> copy(completed = completed - shrineId, found = found + shrineId)
        ShrineState.COMPLETED -> copy(completed = completed + shrineId, found = found + shrineId)
    }
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
    /** Divine Beasts freed on this save file; each gave one Heart Container. */
    val beastHearts: Int = 0,
) {
    val bought: Int get() = heartsBought + staminaBought

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
     * with every beast freed they sell 23 + 10, more than the 30 (31) upgrades the orbs buy.
     */
    val upgradesLeft: Int get() = (totalUpgrades - bought).coerceAtLeast(0)

    /** Hearts on this save file: the starting ones, the Divine Beasts' and those bought. */
    val hearts: Int get() = upgrades.startingHearts + beastHearts + heartsBought

    /** Heart Containers the Goddess Statues sell at most, given the Divine Beasts freed so far. */
    val maxHeartsBought: Int get() = (upgrades.maxHeartsBought - beastHearts).coerceAtLeast(0)

    /** More hearts entered than the game allows, e.g. a beast freed after hearts were maxed. */
    val heartsOverMax: Boolean get() = heartsBought > maxHeartsBought

    val heartsLeft: Int get() = (maxHeartsBought - heartsBought).coerceAtLeast(0)

    val staminaLeft: Int get() = (upgrades.maxStaminaVessels - staminaBought).coerceAtLeast(0)

    /**
     * Upgrades needed to max both hearts and stamina, with every boss heart collected. In BotW
     * that's 23 + 10 = 33 against 30 bought with every orb (31 with the DLC), so both can't be
     * maxed at once.
     */
    val upgradesToMaxBoth: Int get() = upgrades.maxHeartsBought - upgrades.bossHearts + upgrades.maxStaminaVessels

    companion object {
        /**
         * [includeDlc] counts the DLC shrines' orbs; off when the DLC is hidden. [beastsFreed] is
         * how many Divine Beasts the same save file has freed.
         */
        fun of(catalog: ShrineCatalog, progress: ShrineProgress, includeDlc: Boolean, beastsFreed: Int = 0): OrbPlan {
            val orbShrines = catalog.shrines.filter { it.orb && (includeDlc || !it.dlc) }
            return OrbPlan(
                upgrades = catalog.upgrades,
                earned = orbShrines.count { it.id in progress.completed },
                total = orbShrines.size,
                heartsBought = progress.heartsBought,
                staminaBought = progress.staminaBought,
                beastHearts = beastsFreed.coerceIn(0, catalog.upgrades.bossHearts),
            )
        }
    }
}
