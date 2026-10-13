package gx.zelda.botw.fairies.model

import kotlinx.serialization.Serializable

@Serializable
data class GreatFairy(
    val id: String,
    val name: String,
    val region: String,
    /** Where the fountain is, in one line. */
    val location: String,
    /** How to get there from the nearest tower, settlement or stable. */
    val directions: String,
    /** Named landmarks close to the fountain. */
    val nearby: List<String> = emptyList(),
    /** Side quests or tips tied to this fountain. */
    val notes: String? = null,
)

@Serializable
data class FairyCatalog(
    /**
     * Rupees to open a fountain, by how many are already open: the first one opened costs
     * `unlockCosts[0]` whichever fairy it is.
     */
    val unlockCosts: List<Int>,
    val fairies: List<GreatFairy>,
) {
    /** What the next fountain costs with [unlocked] already open; null once all are. */
    fun nextCost(unlocked: Int): Int? = unlockCosts.getOrNull(unlocked)

    /** Rupees still needed to open every remaining fountain. */
    fun remainingCost(unlocked: Int): Int = unlockCosts.drop(unlocked).sum()

    /** How many of [ids] are fairies in this catalog. */
    fun unlockedCount(ids: Set<String>): Int = fairies.count { it.id in ids }
}
