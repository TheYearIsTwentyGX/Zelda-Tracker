package gx.zelda.potions.model

import gx.zelda.armor.model.MaterialCost
import gx.zelda.armor.model.PieceProgress
import gx.zelda.upgrades.model.ItemCopy
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradeItem
import gx.zelda.upgrades.model.UpgradeProgress

/**
 * One step of a potion's recipe: buying the base potion from Luv (step 0), or one of Bertie's
 * infusions (step n makes the potion's level n).
 */
data class RecipeStep(
    val potion: UpgradeItem,
    val index: Int,
    /** Who sells or infuses it: "Luv's Potion Shop", "Bertie's Infusion Shop". */
    val by: String,
    /** The price, or null for a base potion that isn't sold. */
    val rupees: Int?,
    val materials: List<MaterialCost>,
    val made: Boolean,
    val note: String? = null,
) {
    val name: String get() = potion.levels[index].name
    val effect: String? get() = potion.levels[index].effect
    val isBase: Boolean get() = index == 0
}

/** What a recipe still needs: the materials, merged, and the Rupees. */
data class RecipeNeeds(val materials: List<MaterialCost>, val rupees: Int) {
    val materialCount: Int get() = materials.sumOf { it.qty }
    val isEmpty: Boolean get() = materials.isEmpty() && rupees == 0
}

/**
 * The Potion Infusions recipe book: Skyward Sword's potions, read from the Item Upgrades data's
 * potions section, with the same progress (see `UpgradeStorage`). A potion's progress is its copy
 * in [UpgradeProgress]: *owned* is the base potion bought, its level the infusions made. So a
 * recipe's steps tick like a checklist's tiers, in order: making one makes every step before it.
 */
object PotionBook {
    /** The section of `upgrades.json` the potions are in. */
    const val SECTION = "potions"

    fun potions(catalog: UpgradeCatalog): List<UpgradeItem> = catalog.items.filter { it.section == SECTION }

    /** Steps made of [potion]'s recipe: 0 before it's bought, then 1 + the infusions done. */
    fun made(potion: UpgradeItem, progress: UpgradeProgress): Int {
        val copy = ItemCopy(potion, 1)
        return if (progress.owned(copy)) progress.level(copy) + 1 else 0
    }

    /** The base potion and each infusion. */
    fun steps(potion: UpgradeItem): Int = potion.maxLevel + 1

    fun isDone(potion: UpgradeItem, progress: UpgradeProgress): Boolean = made(potion, progress) >= steps(potion)

    fun recipe(potion: UpgradeItem, progress: UpgradeProgress): List<RecipeStep> {
        val made = made(potion, progress)
        val base = potion.acquire.minByOrNull { it.cost?.rupees ?: Int.MAX_VALUE }
        return listOf(
            RecipeStep(potion, 0, by = base?.title ?: "Not sold", rupees = potion.price, materials = emptyList(), made = made > 0),
        ) + potion.upgrades.mapIndexed { i, step ->
            RecipeStep(potion, i + 1, by = step.by, rupees = step.rupees, materials = step.materials, made = made > i + 1, note = step.note)
        }
    }

    /** What's left to make all of [potion]'s recipe, the base potion's price included. */
    fun needs(potion: UpgradeItem, progress: UpgradeProgress): RecipeNeeds {
        val left = recipe(potion, progress).filter { !it.made }
        val materials = left.flatMap { it.materials }.groupBy { it.material }
            .map { (id, costs) -> MaterialCost(id, costs.sumOf { it.qty }) }
        return RecipeNeeds(materials, left.sumOf { it.rupees ?: 0 })
    }

    /**
     * The potion's state with step [index] made, or not, as a checklist's tiers tick: making a step
     * makes every one before it; unmaking one unmakes every one after.
     */
    fun withStep(potion: UpgradeItem, current: PieceProgress, index: Int, made: Boolean): PieceProgress {
        val now = if (current.owned) current.level.coerceIn(0, potion.maxLevel) + 1 else 0
        val steps = if (made) maxOf(now, index + 1) else minOf(now, index)
        return if (steps == 0) PieceProgress() else PieceProgress(owned = true, level = steps - 1)
    }

    /** Every step of [potion] made, or none. */
    fun allMade(potion: UpgradeItem, made: Boolean): PieceProgress =
        if (made) PieceProgress(owned = true, level = potion.maxLevel) else PieceProgress()
}
