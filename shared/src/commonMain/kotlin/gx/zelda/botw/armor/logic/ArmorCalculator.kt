package gx.zelda.botw.armor.logic

import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorProgress
import gx.zelda.botw.armor.model.ArmorSet
import gx.zelda.botw.armor.model.Material
import gx.zelda.botw.armor.model.MaterialCost
import gx.zelda.botw.armor.model.Slot

/** One upgrade step: the materials needed to take a piece to [toLevel]. */
data class UpgradeTier(val toLevel: Int, val costs: List<MaterialCost>)

/**
 * One slot of a set. Usually a single piece, but some slots accept several — any of the four
 * amiibo Divine Helms counts as the head piece of the Ancient set.
 */
data class SetSlotStatus(val slot: Slot, val options: List<ArmorPiece>, val owned: Boolean, val satisfied: Boolean)

data class SetBonusStatus(
    val set: ArmorSet,
    val pieces: List<ArmorPiece>,
    val slots: List<SetSlotStatus>,
) {
    val hasBonus: Boolean get() = set.bonus != null
    val isActive: Boolean get() = hasBonus && slots.all { it.satisfied }
}

enum class TotalsMode(val label: String) {
    COLLECTED("Collected Armor"),
    ALL("All Armor (Including Uncollected)"),
}

data class MaterialUse(val piece: ArmorPiece, val toLevel: Int, val qty: Int)

data class MaterialTotal(val material: Material, val qty: Int, val uses: List<MaterialUse>)

data class MaterialTotals(
    val lines: List<MaterialTotal>,
    val piecesCounted: Int,
    val tiersRemaining: Int,
)

object ArmorCalculator {
    /** In BotW a set bonus switches on once every piece of the set is upgraded to ★2. */
    const val SET_BONUS_LEVEL = 2

    /** The level that counts for stats: 0 for unowned pieces, clamped to what the piece supports. */
    fun level(piece: ArmorPiece, progress: ArmorProgress): Int {
        val p = progress.of(piece.id)
        return if (p.owned) p.level.coerceIn(0, piece.maxLevel) else 0
    }

    fun defenseAt(piece: ArmorPiece, level: Int): Int =
        piece.defense.getOrElse(level.coerceIn(0, piece.maxLevel)) { piece.defense.last() }

    /**
     * Upgrades still to do. For an owned piece that is everything above its current level;
     * for an unowned piece it is the full path from ★0.
     */
    fun remainingUpgrades(piece: ArmorPiece, progress: ArmorProgress): List<UpgradeTier> {
        val from = level(piece, progress)
        return (from until piece.maxLevel).map { UpgradeTier(toLevel = it + 1, costs = piece.upgrades[it]) }
    }

    /**
     * A bonus is active when every slot of the set has an owned piece that is at
     * [SET_BONUS_LEVEL] or higher. Pieces that can't be upgraded only need to be owned.
     */
    fun setBonusStatus(catalog: ArmorCatalog, set: ArmorSet, progress: ArmorProgress): SetBonusStatus {
        val pieces = catalog.piecesIn(set.id)
        val slots = pieces.groupBy { it.slot }.entries.sortedBy { it.key.ordinal }.map { (slot, options) ->
            val owned = options.filter { progress.of(it.id).owned }
            SetSlotStatus(
                slot = slot,
                options = options,
                owned = owned.isNotEmpty(),
                satisfied = owned.any { !it.isUpgradable || level(it, progress) >= SET_BONUS_LEVEL },
            )
        }
        return SetBonusStatus(set, pieces, slots)
    }

    /**
     * Sums the materials for every remaining upgrade.
     * [TotalsMode.COLLECTED] counts owned pieces only; [TotalsMode.ALL] also adds the full
     * upgrade path of every piece not yet collected. The DLC/amiibo filters apply in both.
     */
    fun totals(catalog: ArmorCatalog, progress: ArmorProgress, mode: TotalsMode): MaterialTotals {
        val pieces = catalog.pieces.filter { piece ->
            progress.isVisible(piece.source) &&
                (mode == TotalsMode.ALL || progress.of(piece.id).owned)
        }

        val uses = mutableMapOf<String, MutableList<MaterialUse>>()
        var piecesCounted = 0
        var tiersRemaining = 0
        for (piece in pieces) {
            val tiers = remainingUpgrades(piece, progress)
            if (tiers.isEmpty()) continue
            piecesCounted++
            tiersRemaining += tiers.size
            for (tier in tiers) {
                for (cost in tier.costs) {
                    uses.getOrPut(cost.material) { mutableListOf() } += MaterialUse(piece, tier.toLevel, cost.qty)
                }
            }
        }

        val lines = uses.map { (id, list) -> MaterialTotal(catalog.material(id), list.sumOf { it.qty }, list) }
            .sortedWith(compareByDescending<MaterialTotal> { it.qty }.thenBy { it.material.name })
        return MaterialTotals(lines, piecesCounted, tiersRemaining)
    }
}
