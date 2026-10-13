package gx.zelda.botw.armor.logic

import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorProgress

/** Upgrading a whole set at once from its header. */
object SetUpgrade {
    /**
     * The pieces a set's star counter acts on, from the pieces shown under its header.
     * One slot can hold several pieces (any Divine Helm is the Ancient set's head), so per slot
     * this takes the owned pieces, or, when none is owned, just the slot's first piece — tapping
     * the set's stars must never collect alternatives the player doesn't have.
     */
    fun targets(pieces: List<ArmorPiece>, progress: ArmorProgress): List<ArmorPiece> =
        pieces.filter { it.isUpgradable }
            .groupBy { it.slot }
            .values
            .flatMap { slot -> slot.filter { progress.of(it.id).owned }.ifEmpty { listOf(slot.first()) } }

    fun averageLevel(targets: List<ArmorPiece>, progress: ArmorProgress): Float =
        if (targets.isEmpty()) 0f else targets.sumOf { ArmorCalculator.level(it, progress) }.toFloat() / targets.size

    /**
     * New level for each target after one tap: every piece at the lowest level goes up one.
     * Once everything is at its max, the next tap takes the whole set back to 0.
     */
    fun nextLevels(targets: List<ArmorPiece>, progress: ArmorProgress): Map<String, Int> {
        val levels = targets.associate { it.id to ArmorCalculator.level(it, progress) }
        val belowMax = targets.filter { levels.getValue(it.id) < it.maxLevel }
        if (belowMax.isEmpty()) return targets.associate { it.id to 0 }
        val lowest = belowMax.minOf { levels.getValue(it.id) }
        return belowMax.filter { levels.getValue(it.id) == lowest }.associate { it.id to lowest + 1 }
    }
}
