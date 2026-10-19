package gx.zelda.adventure.model

import gx.zelda.core.countOf

/**
 * What to do next on a map: the first square with something left on it that can be acted on now,
 * easiest colour first and then row by row; failing that, any square with something left. Acted
 * on means challengeable, or sealed with the card that lifts the seal in hand. In spoiler-safe mode
 * only squares the save file has reached are named, and a search is only named once the Compass
 * has shown it.
 */
object AdventureNext {
    fun line(map: AdventureMap, progress: AdventureProgress, spoilers: AdventureSpoilers): String {
        val order = map.regions.map { it.id }
        val left = map.squares.filterNot(progress::isComplete)
        if (left.isEmpty()) return "All done"
        val reached = spoilers.explored
        val inventory = AdventureInventory(map, progress)
        fun now(s: Square) = s.id in reached &&
            (!AdventureReach.sealed(progress, s) || AdventureReach.unsealing(s)?.let(inventory::canDo) == true)
        val square = left.filterNot(spoilers::isHidden)
            .minWithOrNull(compareBy<Square>({ !now(it) }, { order.indexOf(it.region) }, { it.row }, { it.col }))
            ?: return "${countOf(left.size, "square")} still to explore"
        return "${square.label}: ${task(map, square, progress, spoilers)}"
    }

    private fun task(map: AdventureMap, square: Square, progress: AdventureProgress, spoilers: AdventureSpoilers): String {
        val reward = square.rewards.firstOrNull { !progress.isDone(it.id) }
        val seal = AdventureReach.unsealing(square)?.takeIf { AdventureReach.sealed(progress, square) }
        return when {
            seal != null -> "${map.card(seal.card)?.name ?: seal.card} search, then clear it"
            !progress.isCleared(square) -> "clear it (${square.mission})"
            reward != null -> rewardTask(map, reward)
            !progress.isARanked(square) -> "get an A rank"
            square.skulltulas.any { !progress.isDone(it.id) } -> "Gold Skulltula"
            spoilers.secretsHidden(square) -> "a search left"
            else -> square.searches.first { !progress.isDone(it.id) }.let { "${map.card(it.card)?.name ?: it.card} search" }
        }
    }

    /** "Heart Container for Impa (A rank)". */
    fun rewardTask(map: AdventureMap, reward: Reward): String {
        val who = reward.warrior?.takeIf { reward.type != RewardType.WARRIOR }?.let { " for ${map.warriorName(it)}" }.orEmpty()
        val how = when (reward.slot) {
            RewardSlot.A_RANK -> " (A rank)"
            RewardSlot.CLEAR -> ""
            RewardSlot.TREASURE -> reward.keep?.let { " (in $it)" }.orEmpty()
        }
        return "${reward.name}$who$how"
    }
}
