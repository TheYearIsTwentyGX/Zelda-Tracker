package gx.zelda.shrines.model

import kotlinx.serialization.Serializable

/** One save file's shrines, plus what its Spirit Orbs have been spent on. */
@Serializable
data class ShrineProgress(
    val completed: Set<String> = emptySet(),
    /** Heart Containers bought at Goddess Statues, as entered by the user. */
    val heartsBought: Int = 0,
    /** Stamina Vessels bought at Goddess Statues, as entered by the user. */
    val staminaBought: Int = 0,
)

/** Viewing preferences, shared by every save file. */
@Serializable
data class ShrinePrefs(
    val showDlc: Boolean = true,
)

/**
 * The Spirit Orb arithmetic for one save file. Orbs come from completed base-game shrines;
 * how they were spent is what the user entered, since the app can't see the save.
 */
data class OrbPlan(
    val upgrades: OrbUpgrades,
    /** Orbs earned so far. */
    val earned: Int,
    /** Orbs the game holds in total: one per base-game shrine. */
    val total: Int,
    val heartsBought: Int,
    val staminaBought: Int,
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

    /** Upgrades still to buy, counting orbs not yet earned. */
    val upgradesLeft: Int get() = (totalUpgrades - bought).coerceAtLeast(0)

    val heartsLeft: Int get() = (upgrades.maxHeartsBought - heartsBought).coerceAtLeast(0)

    val staminaLeft: Int get() = (upgrades.maxStaminaVessels - staminaBought).coerceAtLeast(0)

    /**
     * Upgrades needed to max both hearts and stamina, with every boss heart collected. In BotW
     * that's 23 + 10 = 33 against 30 bought with every orb, so both can't be maxed at once.
     */
    val upgradesToMaxBoth: Int get() = upgrades.maxHeartsBought - upgrades.bossHearts + upgrades.maxStaminaVessels

    companion object {
        fun of(catalog: ShrineCatalog, progress: ShrineProgress) = OrbPlan(
            upgrades = catalog.upgrades,
            earned = catalog.orbsEarned(progress.completed),
            total = catalog.base.size,
            heartsBought = progress.heartsBought,
            staminaBought = progress.staminaBought,
        )
    }
}
