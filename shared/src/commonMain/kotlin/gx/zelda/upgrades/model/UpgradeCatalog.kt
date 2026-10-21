package gx.zelda.upgrades.model

import gx.zelda.armor.model.Acquisition
import gx.zelda.armor.model.MaterialCost
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a material is in the game's Collection screen. */
@Serializable
enum class MaterialKind(val label: String) {
    TREASURE("Treasure"),
    BUG("Bug"),
}

/** A treasure or bug an upgrade takes, with where to farm it. */
@Serializable
data class UpgradeMaterial(
    val id: String,
    val name: String,
    val kind: MaterialKind,
    /** Where to find or farm it, in a sentence or two. */
    val farm: String,
    /** The areas it's found in, for a glance at the list. */
    val regions: List<String> = emptyList(),
    /** Position on the game's Collection screen (bugs first, then treasures). */
    val gameOrder: Int? = null,
)

/**
 * One list section: the items, the shields, the pouch items or the potions. A section with an
 * [optional] label gets a filter chip of that name, so it can be left out of the list and the totals.
 */
@Serializable
data class UpgradeSection(
    val id: String,
    val title: String,
    val note: String? = null,
    val optional: String? = null,
)

/** An item at one level: the base item is level 0, each upgrade one more. */
@Serializable
data class UpgradeLevel(
    /** Stable, and names its icon (`drawable/ss_up_<id>.png`). */
    val id: String,
    val name: String,
    val effect: String? = null,
)

/**
 * One upgrade: what takes an item from one level to the next. [free] marks the one that costs
 * nothing (the Hook Beetle, fitted by a robot in Lanayru Desert); every other step has a price.
 */
@Serializable
data class UpgradeStep(
    val rupees: Int,
    val materials: List<MaterialCost> = emptyList(),
    /** Who does it: "Gondo's Scrap Shop", "Bertie's Infusion Shop". */
    val by: String,
    val free: Boolean = false,
    val note: String? = null,
)

@Serializable
data class UpgradeItem(
    val id: String,
    /** What the item is called whatever its level ("Seed Satchel"); the base item's name when absent. */
    @SerialName("name") val baseName: String? = null,
    val section: String,
    /** The item at each level; `levels[0]` is the base item, so `levels.size == upgrades.size + 1`. */
    val levels: List<UpgradeLevel>,
    /** `upgrades[i]` takes the item from level i to i + 1. Empty for one that can't be upgraded. */
    val upgrades: List<UpgradeStep> = emptyList(),
    /** Ways to get the base item. */
    val acquire: List<Acquisition> = emptyList(),
    /** Can be lost for good if not got in time. */
    val missable: Boolean = false,
    /**
     * A pouch item you can carry several of (Seed Satchels, Quivers, Bomb Bags). Each copy is
     * tracked on its own, but the totals count one of each: see `UpgradeCalculator`.
     */
    val multiple: Boolean = false,
    val notes: String? = null,
) {
    val name: String get() = baseName ?: levels.first().name
    val maxLevel: Int get() = upgrades.size
    val isUpgradable: Boolean get() = upgrades.isNotEmpty()

    /** Rupees the item is sold for, the cheapest way, if it can be bought. */
    val price: Int? get() = acquire.mapNotNull { it.cost?.rupees }.minOrNull()
    val purchasable: Boolean get() = price != null

    fun levelName(level: Int): String = levels[level.coerceIn(0, maxLevel)].name
}

@Serializable
data class UpgradeCatalog(
    val sections: List<UpgradeSection>,
    val materials: List<UpgradeMaterial>,
    val items: List<UpgradeItem>,
) {
    private val materialsById by lazy { materials.associateBy { it.id } }
    private val itemsById by lazy { items.associateBy { it.id } }
    private val sectionsById by lazy { sections.associateBy { it.id } }

    fun material(id: String): UpgradeMaterial = materialsById[id] ?: UpgradeMaterial(id, id, MaterialKind.TREASURE, farm = "")
    fun item(id: String): UpgradeItem? = itemsById[id]
    fun section(id: String): UpgradeSection? = sectionsById[id]

    /** The sections with a filter chip. */
    val optionalSections: List<UpgradeSection> by lazy { sections.filter { it.optional != null } }
}
