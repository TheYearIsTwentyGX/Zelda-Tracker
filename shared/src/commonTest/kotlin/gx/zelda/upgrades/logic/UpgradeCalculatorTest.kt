package gx.zelda.upgrades.logic

import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
import gx.zelda.upgrades.UpgradeFixture.beetle
import gx.zelda.upgrades.UpgradeFixture.catalog
import gx.zelda.upgrades.UpgradeFixture.satchel
import gx.zelda.upgrades.data.UpgradeProgressProvider
import gx.zelda.upgrades.model.ItemCopy
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The totals rule: everything fully upgraded, one of each pouch item, the potion chip, and the two scopes. */
class UpgradeCalculatorTest {
    private val prefs = UpgradePrefs()
    private val noPotions = UpgradePrefs(hiddenSections = setOf("potions"))

    private fun totals(progress: UpgradeProgress, mode: UpgradeTotalsMode, prefs: UpgradePrefs = this.prefs) =
        UpgradeCalculator.totals(catalog, progress, prefs, mode)

    private fun UpgradeTotals.qty(): Map<String, Int> = lines.associate { it.material.id to it.qty }

    private fun progress(vararg copies: Pair<String, PieceProgress>, satchels: Int = 1) =
        UpgradeProgress(copies.toMap(), if (satchels > 1) mapOf("seed_satchel" to satchels) else emptyMap())

    @Test
    fun allItemsWithNothingCollectedIsEveryUpgrade() {
        val all = totals(UpgradeProgress(), UpgradeTotalsMode.ALL)
        assertEquals(mapOf("amber" to 11, "flower" to 1, "skull" to 1, "butterfly" to 3), all.qty())
        assertEquals(50 + 150 + 20, all.rupees)
        assertEquals(3, all.itemsCounted, "the Hylian Shield has nothing to upgrade")
        assertEquals(5, all.stepsRemaining, "the free Hook Beetle counts as a step, at no cost")
    }

    @Test
    fun collectedCountsOnlyWhatYouHaveFromItsLevel() {
        assertEquals(emptyList(), totals(UpgradeProgress(), UpgradeTotalsMode.COLLECTED).lines)
        val p = progress("beetle" to PieceProgress(owned = true, level = 1))
        val collected = totals(p, UpgradeTotalsMode.COLLECTED)
        assertEquals(mapOf("amber" to 2, "flower" to 1), collected.qty())
        assertEquals(50, collected.rupees)
        // All adds the uncollected items' whole paths to the collected ones' remaining upgrades.
        assertEquals(mapOf("amber" to 11, "flower" to 1, "skull" to 1, "butterfly" to 3), totals(p, UpgradeTotalsMode.ALL).qty())
        // A level on an uncollected item doesn't count: it's upgraded from scratch.
        val unowned = progress("beetle" to PieceProgress(owned = false, level = 2))
        assertEquals(1, totals(unowned, UpgradeTotalsMode.ALL).qty()["flower"])
    }

    @Test
    fun aFullyUpgradedItemNeedsNothing() {
        val p = progress("beetle" to PieceProgress(true, 2), "seed_satchel" to PieceProgress(true, 2), "heart_potion" to PieceProgress(true, 1))
        val all = totals(p, UpgradeTotalsMode.ALL)
        assertEquals(emptyList(), all.lines)
        assertEquals(0, all.rupees)
        assertEquals(0, all.itemsCounted)
    }

    @Test
    fun onlyOneOfEachPouchItemCounts() {
        // Three satchels, none collected: All counts one, from the start.
        val three = progress(satchels = 3)
        assertEquals(9, totals(three, UpgradeTotalsMode.ALL).qty()["amber"]!! - 2, "4 + 5 for one satchel, plus the Beetle's 2")
        assertEquals(listOf(1), totals(three, UpgradeTotalsMode.ALL).lines.single { it.material.id == "skull" }.uses.map { it.copy.number })

        // The most upgraded one you have is the one that counts; the others add nothing.
        val mixed = progress(
            "seed_satchel" to PieceProgress(true, 0),
            "seed_satchel#2" to PieceProgress(true, 1),
            "seed_satchel#3" to PieceProgress(false),
            satchels = 3,
        )
        val collected = totals(mixed, UpgradeTotalsMode.COLLECTED)
        assertEquals(mapOf("amber" to 5, "skull" to 1), collected.qty())
        assertEquals(listOf(2), collected.lines.flatMap { it.uses }.map { it.copy.number }.distinct())
        assertEquals(ItemCopy(satchel, 2), UpgradeCalculator.countedCopy(satchel, mixed, UpgradeTotalsMode.COLLECTED))
        assertTrue(UpgradeCalculator.isCounted(ItemCopy(satchel, 2), mixed))
        assertFalse(UpgradeCalculator.isCounted(ItemCopy(satchel, 1), mixed))

        // One maxed satchel means the pouch item is done, however many others are behind.
        val maxed = progress("seed_satchel" to PieceProgress(true, 0), "seed_satchel#2" to PieceProgress(true, 2), satchels = 2)
        assertTrue(totals(maxed, UpgradeTotalsMode.ALL).lines.none { l -> l.uses.any { it.copy.item == satchel } })
        // A tie goes to the first.
        val tie = progress("seed_satchel" to PieceProgress(true, 1), "seed_satchel#2" to PieceProgress(true, 1), satchels = 2)
        assertEquals(1, UpgradeCalculator.countedCopy(satchel, tie, UpgradeTotalsMode.ALL)!!.number)
    }

    @Test
    fun nothingCountsInCollectedWhenNoCopyIsOwned() {
        assertNull(UpgradeCalculator.countedCopy(satchel, progress(satchels = 2), UpgradeTotalsMode.COLLECTED))
        assertEquals(ItemCopy(satchel, 1), UpgradeCalculator.countedCopy(satchel, progress(satchels = 2), UpgradeTotalsMode.ALL))
    }

    @Test
    fun thePotionChipLeavesThemOutOfBothScopes() {
        val p = progress("heart_potion" to PieceProgress(true, 0))
        assertEquals(3, totals(p, UpgradeTotalsMode.COLLECTED).qty()["butterfly"])
        assertNull(totals(p, UpgradeTotalsMode.COLLECTED, noPotions).qty()["butterfly"])
        val all = totals(UpgradeProgress(), UpgradeTotalsMode.ALL, noPotions)
        assertNull(all.qty()["butterfly"])
        assertEquals(200, all.rupees)
        assertEquals((2 + 2) to (2 + 0 + 2 + 1), UpgradeCalculator.upgradeCount(catalog, progress("beetle" to PieceProgress(true, 2), "seed_satchel" to PieceProgress(true, 2)), prefs))
        assertEquals(4 to 4, UpgradeCalculator.upgradeCount(catalog, progress("beetle" to PieceProgress(true, 2), "seed_satchel" to PieceProgress(true, 2)), noPotions))
    }

    @Test
    fun tappingAMaterialListsTheUpgradesThatNeedIt() {
        val amber = totals(progress(satchels = 2), UpgradeTotalsMode.ALL).lines.single { it.material.id == "amber" }
        assertEquals(listOf("Quick Beetle" to 2, "Medium Seed Satchel" to 4, "Large Seed Satchel" to 5), amber.uses.map { it.name to it.qty })
        assertEquals(11, amber.qty)
    }

    @Test
    fun remainingStepsStartAtTheLevelYouHave() {
        val p = progress("beetle" to PieceProgress(true, 1))
        assertEquals(listOf("Quick Beetle"), UpgradeCalculator.remaining(ItemCopy(beetle, 1), p).map { it.name })
        assertEquals(listOf("Hook Beetle", "Quick Beetle"), UpgradeCalculator.remaining(ItemCopy(beetle, 1), UpgradeProgress()).map { it.name })
        // A level saved above the item's top is clamped.
        assertEquals(emptyList(), UpgradeCalculator.remaining(ItemCopy(beetle, 1), progress("beetle" to PieceProgress(true, 9))))
    }

    @Test
    fun materialSorts() {
        val all = totals(UpgradeProgress(), UpgradeTotalsMode.ALL)
        assertEquals(listOf("amber", "butterfly", "flower", "skull"), all.sorted(MaterialSort.MOST_NEEDED).map { it.material.id })
        assertEquals(listOf("amber", "flower", "butterfly", "skull"), all.sorted(MaterialSort.ALPHABETICAL).map { it.material.id })
        assertEquals(listOf("butterfly", "amber", "flower", "skull"), all.sorted(MaterialSort.IN_GAME).map { it.material.id })
    }

    @Test
    fun theOverviewRow() {
        val empty = UpgradeProgressProvider.summarize(catalog, UpgradeProgress(), prefs)
        assertEquals(0, empty.done)
        assertEquals(4, empty.total)
        assertEquals("Upgrades 0 / 5 · 16 materials and 220 Rupees to go", empty.secondary)
        assertNull(empty.next, "nothing collected, nothing to suggest")

        val p = progress("beetle" to PieceProgress(true, 1), "seed_satchel#2" to PieceProgress(true, 2), "hylian_shield" to PieceProgress(true), satchels = 2)
        val row = UpgradeProgressProvider.summarize(catalog, p, prefs)
        assertEquals(3, row.done, "the Beetle, a satchel (copy 2 counts) and the Hylian Shield")
        assertEquals("Upgrades 3 / 5 · 6 materials and 70 Rupees to go", row.secondary)
        assertEquals("Quick Beetle (Gondo's Scrap Shop)", row.next)

        val hidden = UpgradeProgressProvider.summarize(catalog, p, noPotions)
        assertEquals(3, hidden.total)
        assertEquals("Upgrades 3 / 4 · 3 materials and 50 Rupees to go", hidden.secondary)

        val done = progress("beetle" to PieceProgress(true, 2), "seed_satchel" to PieceProgress(true, 2), "heart_potion" to PieceProgress(true, 1))
        assertEquals("Upgrades 5 / 5 · nothing left to gather", UpgradeProgressProvider.summarize(catalog, done, prefs).secondary)
    }
}
