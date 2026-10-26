package gx.zelda.upgrades.logic

import gx.zelda.armor.model.MaterialSort
import gx.zelda.upgrades.model.ItemCopy
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradeItem
import gx.zelda.upgrades.model.UpgradeMaterial
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import gx.zelda.upgrades.model.UpgradeStep

enum class UpgradeTotalsMode(val label: String) {
    COLLECTED("Collected items"),
    ALL("All items (including not yet obtained)"),
}

/** One upgrade still to do: [step] takes [copy] to [toLevel]. */
data class RemainingStep(val copy: ItemCopy, val toLevel: Int, val step: UpgradeStep) {
    /** What the item becomes: "Quick Beetle". */
    val name: String get() = copy.item.levelName(toLevel)
}

/** [qty] of a material that the upgrade to [toLevel] of [copy] takes. */
data class MaterialUse(val copy: ItemCopy, val toLevel: Int, val qty: Int) {
    val name: String get() = copy.item.levelName(toLevel)
}

data class MaterialTotal(val material: UpgradeMaterial, val qty: Int, val uses: List<MaterialUse>)

data class UpgradeTotals(
    val lines: List<MaterialTotal>,
    val rupees: Int,
    val itemsCounted: Int,
    val stepsRemaining: Int,
) {
    val materialCount: Int get() = lines.sumOf { it.qty }

    fun sorted(sort: MaterialSort): List<MaterialTotal> = when (sort) {
        MaterialSort.MOST_NEEDED -> lines.sortedWith(compareByDescending<MaterialTotal> { it.qty }.thenBy { it.material.name })
        MaterialSort.ALPHABETICAL -> lines.sortedBy { it.material.name }
        MaterialSort.IN_GAME -> lines.sortedWith(
            compareBy<MaterialTotal, Int?>(nullsLast()) { it.material.gameOrder }.thenBy { it.material.name },
        )
    }
}

/**
 * The totals rule: **every item fully upgraded, and one of each pouch item.** You can carry several
 * Seed Satchels, Quivers and Bomb Bags and track each copy, but only one of each counts:
 * - your most upgraded owned copy (the first, on a tie), since that's the one you'd finish;
 * - with none owned, copy 1, from the start, and only for [UpgradeTotalsMode.ALL].
 *
 * Other copies' upgrades are still listed on their own pages; they just don't add to the totals or
 * to the overview's upgrade count. Sections the filter chips turn off (the potions) count nowhere.
 */
object UpgradeCalculator {
    fun visibleItems(catalog: UpgradeCatalog, prefs: UpgradePrefs): List<UpgradeItem> =
        catalog.items.filter { prefs.shows(it.section) }

    /**
     * Upgrades still to do on [copy]: everything above its level when owned, the whole path
     * from the base item when not.
     */
    fun remaining(copy: ItemCopy, progress: UpgradeProgress): List<RemainingStep> {
        val from = progress.level(copy)
        return (from until copy.item.maxLevel).map { RemainingStep(copy, it + 1, copy.item.upgrades[it]) }
    }

    /** The copy of [item] the totals count in [mode], or null when none does. */
    fun countedCopy(item: UpgradeItem, progress: UpgradeProgress, mode: UpgradeTotalsMode): ItemCopy? {
        val copies = progress.copiesOf(item)
        val owned = copies.filter { progress.owned(it) }
        return when {
            owned.isNotEmpty() -> owned.maxBy { progress.level(it) }
            mode == UpgradeTotalsMode.ALL -> copies.first()
            else -> null
        }
    }

    /** Whether [copy] is one the totals count (in either mode), for the "not in totals" tag. */
    fun isCounted(copy: ItemCopy, progress: UpgradeProgress): Boolean =
        countedCopy(copy.item, progress, UpgradeTotalsMode.ALL) == copy

    fun totals(catalog: UpgradeCatalog, progress: UpgradeProgress, prefs: UpgradePrefs, mode: UpgradeTotalsMode): UpgradeTotals =
        totals(catalog, visibleItems(catalog, prefs), progress, mode)

    /** The totals over [items] only, whatever the chips say: the Potion Infusions book's, over the potions. */
    fun totals(catalog: UpgradeCatalog, items: List<UpgradeItem>, progress: UpgradeProgress, mode: UpgradeTotalsMode): UpgradeTotals {
        val uses = mutableMapOf<String, MutableList<MaterialUse>>()
        var rupees = 0
        var itemsCounted = 0
        var steps = 0
        for (item in items) {
            val copy = countedCopy(item, progress, mode) ?: continue
            val remaining = remaining(copy, progress)
            if (remaining.isEmpty()) continue
            itemsCounted++
            steps += remaining.size
            for (r in remaining) {
                rupees += r.step.rupees
                for (cost in r.step.materials) {
                    uses.getOrPut(cost.material) { mutableListOf() } += MaterialUse(copy, r.toLevel, cost.qty)
                }
            }
        }
        val lines = uses.map { (id, list) -> MaterialTotal(catalog.material(id), list.sumOf { it.qty }, list) }
            .sortedWith(compareByDescending<MaterialTotal> { it.qty }.thenBy { it.material.name })
        return UpgradeTotals(lines, rupees, itemsCounted, steps)
    }

    /** Upgrades done and possible, one copy of each item, over the items the chips leave in. */
    fun upgradeCount(catalog: UpgradeCatalog, progress: UpgradeProgress, prefs: UpgradePrefs): Pair<Int, Int> {
        val items = visibleItems(catalog, prefs)
        val done = items.sumOf { item -> countedCopy(item, progress, UpgradeTotalsMode.ALL)?.let(progress::level) ?: 0 }
        return done to items.sumOf { it.maxLevel }
    }
}

/** 1630 → "1,630". */
internal fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
