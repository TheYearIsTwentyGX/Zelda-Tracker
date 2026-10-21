package gx.zelda.upgrades

import gx.zelda.armor.model.MaterialCost
import gx.zelda.upgrades.model.MaterialKind
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradeItem
import gx.zelda.upgrades.model.UpgradeLevel
import gx.zelda.upgrades.model.UpgradeMaterial
import gx.zelda.upgrades.model.UpgradeSection
import gx.zelda.upgrades.model.UpgradeStep

/**
 * A small upgrade list: a Beetle with a free first upgrade, a shield that can't be upgraded, a
 * pouch item you can carry several of, and a potion in a section the chips can hide.
 */
object UpgradeFixture {
    private fun levels(vararg names: String) = names.map { UpgradeLevel(it.lowercase().replace(' ', '_'), it) }

    private fun step(rupees: Int, vararg materials: Pair<String, Int>) =
        UpgradeStep(rupees, materials.map { (m, q) -> MaterialCost(m, q) }, by = "Gondo's Scrap Shop")

    val beetle = UpgradeItem(
        id = "beetle", section = "items",
        levels = levels("Beetle", "Hook Beetle", "Quick Beetle"),
        upgrades = listOf(UpgradeStep(0, by = "A robot", free = true), step(50, "amber" to 2, "flower" to 1)),
    )
    val hylian = UpgradeItem(id = "hylian_shield", section = "shields", levels = levels("Hylian Shield"))
    val satchel = UpgradeItem(
        id = "seed_satchel", baseName = "Seed Satchel", section = "pouch", multiple = true,
        levels = levels("Small Seed Satchel", "Medium Seed Satchel", "Large Seed Satchel"),
        upgrades = listOf(step(50, "amber" to 4), step(100, "amber" to 5, "skull" to 1)),
    )
    val potion = UpgradeItem(
        id = "heart_potion", section = "potions",
        levels = levels("Heart Potion", "Heart Potion+"),
        upgrades = listOf(step(20, "butterfly" to 3).copy(by = "Bertie's Infusion Shop")),
    )

    val catalog = UpgradeCatalog(
        sections = listOf(
            UpgradeSection("items", "Items"),
            UpgradeSection("shields", "Shields"),
            UpgradeSection("pouch", "Pouch Items"),
            UpgradeSection("potions", "Potions", optional = "Potions"),
        ),
        materials = listOf(
            UpgradeMaterial("butterfly", "Blessed Butterfly", MaterialKind.BUG, "Everywhere", gameOrder = 1),
            UpgradeMaterial("amber", "Amber Relic", MaterialKind.TREASURE, "Everywhere", gameOrder = 2),
            UpgradeMaterial("flower", "Ancient Flower", MaterialKind.TREASURE, "Timeshift Stones", gameOrder = 3),
            UpgradeMaterial("skull", "Golden Skull", MaterialKind.TREASURE, "Bokoblins", gameOrder = 4),
        ),
        items = listOf(beetle, hylian, satchel, potion),
    )
}
