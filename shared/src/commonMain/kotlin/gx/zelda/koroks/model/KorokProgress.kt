package gx.zelda.koroks.model

import gx.zelda.core.DiscoverySets
import gx.zelda.core.DiscoveryState
import kotlinx.serialization.Serializable

/** One save file's Korok Seeds, plus the inventory upgrades the user says they've bought from Hestu. */
@Serializable
data class KorokProgress(
    /** Koroks whose seed has been collected. */
    override val completed: Set<String> = emptySet(),
    /** Koroks found, collected or not; a collected one counts as found whether or not it's here. */
    override val found: Set<String> = emptySet(),
    /** Hestu upgrades bought per [HestuPouch.id], as entered by the user. */
    val hestu: Map<String, Int> = emptyMap(),
) : DiscoverySets {
    fun withState(korokId: String, state: DiscoveryState): KorokProgress =
        setsWith(korokId, state).let { (completed, found) -> copy(completed = completed, found = found) }

    /** Upgrades are stored as entered, never below zero. */
    fun withBought(pouchId: String, upgrades: Int): KorokProgress = copy(hestu = hestu + (pouchId to upgrades.coerceAtLeast(0)))
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class KorokPrefs(
    /** Spoiler-safe mode: an undiscovered Korok's row shows only its region until revealed. */
    val hideUndiscovered: Boolean = false,
)

/**
 * The Hestu arithmetic for one save file: seeds collected (Koroks marked complete) against the
 * upgrades the user says they've bought. The app can't see the save, so purchases are entered by hand.
 */
data class HestuPlan(
    val costs: HestuCosts,
    /** Seeds collected so far. */
    val collected: Int,
    /** Seeds in the game: 900. */
    val total: Int,
    /** Upgrades bought per pouch id, as entered. */
    val bought: Map<String, Int>,
) {
    /** Upgrades bought for [pouch], clamped to what the pouch takes. */
    fun boughtIn(pouch: HestuPouch): Int = (bought[pouch.id] ?: 0).coerceIn(0, pouch.costs.size)

    fun slots(pouch: HestuPouch): Int = pouch.startingSlots + boughtIn(pouch)

    /** What [pouch]'s next upgrade costs; null once it's maxed. */
    fun nextCost(pouch: HestuPouch): Int? = pouch.costs.getOrNull(boughtIn(pouch))

    /** Seeds handed to Hestu for the upgrades entered. */
    val spent: Int get() = costs.pouches.sumOf { it.costs.take(boughtIn(it)).sum() }

    /** Seeds not yet handed over. Negative when more was entered as bought than the seeds allow. */
    val inHand: Int get() = collected - spent

    val isConsistent: Boolean get() = inHand >= 0

    /** Upgrades the seeds in hand pay for right now, buying the cheapest first. */
    val affordable: Int
        get() {
            var left = inHand
            var count = 0
            for (cost in remainingCosts.sorted()) {
                if (cost > left) break
                left -= cost
                count++
            }
            return count
        }

    /** Upgrades not yet bought, across every pouch. */
    val upgradesLeft: Int get() = remainingCosts.size

    /** Seeds still to hand over to max every pouch. */
    val toMax: Int get() = costs.total - spent

    val isMaxed: Boolean get() = upgradesLeft == 0

    /** Seeds still to collect before every remaining upgrade is paid for; 0 once there are enough. */
    val stillToCollect: Int get() = (toMax - inHand).coerceAtLeast(0)

    private val remainingCosts: List<Int> get() = costs.pouches.flatMap { it.costs.drop(boughtIn(it)) }

    companion object {
        fun of(catalog: KorokCatalog, progress: KorokProgress): HestuPlan = HestuPlan(
            costs = catalog.hestu,
            collected = catalog.count(progress.completed),
            total = catalog.koroks.size,
            bought = progress.hestu,
        )
    }
}
