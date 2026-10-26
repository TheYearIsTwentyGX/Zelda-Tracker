package gx.zelda.potions

import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.Games
import gx.zelda.potions.model.PotionBook
import gx.zelda.potions.model.PotionTotals
import gx.zelda.potions.ui.rupeeBreakdown
import gx.zelda.upgrades.data.UpgradeRepository
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotals
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The recipe book's Totals on Skyward Sword's data: both scopes, Luv's prices, and the cross-check with Item Upgrades. */
class PotionTotalsTest {
    private suspend fun catalog(): UpgradeCatalog = UpgradeRepository.load(Games.SS)

    private fun made(vararg potions: Pair<String, PieceProgress>) = UpgradeProgress(items = potions.toMap())

    private fun UpgradeTotals.qty(): Map<String, Int> = lines.associate { it.material.id to it.qty }

    private fun totals(c: UpgradeCatalog, p: UpgradeProgress, mode: UpgradeTotalsMode): PotionTotals = PotionBook.totals(c, p, mode)

    @Test
    fun allPotionsWithNothingBoughtIsEveryStep() = runTest {
        val all = totals(catalog(), UpgradeProgress(), UpgradeTotalsMode.ALL)
        assertEquals(50, all.materialCount)
        assertEquals(180, all.infusions.rupees, "Bertie's seven infusions")
        assertEquals(320, all.baseRupees, "Luv's 20 + 50 + 20 + 30 + 200")
        assertEquals(500, all.rupees)
        assertEquals(12, all.stepsRemaining)
        assertEquals(5, all.potionsCounted)
        assertEquals("180 at Bertie's for 7 infusions · 320 at Luv's for 5 potions", rupeeBreakdown(all))
        assertTrue(all.infusions.lines.all { it.material.kind.name == "BUG" })
        assertEquals(9, all.infusions.qty()["blessed_butterfly"])
        assertEquals(
            listOf("Heart Potion+" to 3, "Heart Potion++" to 3, "Guardian Potion+" to 3),
            all.infusions.lines.single { it.material.id == "blessed_butterfly" }.uses.map { it.name to it.qty },
        )
    }

    @Test
    fun boughtCountsOnlyThePotionsYouHaveFromWhatsMade() = runTest {
        val c = catalog()
        val none = totals(c, UpgradeProgress(), UpgradeTotalsMode.COLLECTED)
        assertEquals(emptyList(), none.infusions.lines)
        assertEquals(0, none.rupees)
        assertEquals(0, none.potionsCounted)
        assertNull(rupeeBreakdown(none))

        // The Heart Potion bought, the Guardian Potion fully infused.
        val p = made("heart_potion" to PieceProgress(owned = true), "guardian_potion" to PieceProgress(owned = true, level = 1))
        val bought = totals(c, p, UpgradeTotalsMode.COLLECTED)
        assertEquals(13, bought.materialCount, "the Heart Potion's two infusions")
        assertEquals(50, bought.rupees, "nothing to buy from Luv")
        assertEquals(emptyList(), bought.basesToBuy)
        assertEquals(2 to 1, bought.stepsRemaining to bought.potionsCounted)

        // All adds the potions not bought: their base price and every infusion.
        val all = totals(c, p, UpgradeTotalsMode.ALL)
        assertEquals(listOf("stamina_potion", "air_potion", "revitalizing_potion"), all.basesToBuy.map { it.id })
        assertEquals(100, all.baseRupees)
        assertEquals(50 + 20 + 20 + 20 + 30, all.infusions.rupees)
        assertEquals(4, all.potionsCounted, "not the Guardian Potion, which is done")

        // A level on a potion not bought (Item Upgrades keeps one) counts from the start.
        val unbought = totals(c, made("air_potion" to PieceProgress(owned = false, level = 1)), UpgradeTotalsMode.ALL)
        assertEquals(50, unbought.materialCount)
        assertEquals(500, unbought.rupees)
    }

    /** The bugs and Bertie's Rupees are exactly what the potions add to Item Upgrades' totals, in each scope. */
    @Test
    fun theInfusionsAreItemUpgradesWithPotionsMinusWithout() = runTest {
        val c = catalog()
        val states = listOf(
            UpgradeProgress(),
            made("heart_potion" to PieceProgress(true, 1), "air_potion" to PieceProgress(true, 0), "beetle" to PieceProgress(true, 1)),
            UpgradeProgress(items = PotionBook.potions(c).associate { it.id to PotionBook.allMade(it, true) }),
        )
        for (p in states) for (mode in UpgradeTotalsMode.entries) {
            val with = UpgradeCalculator.totals(c, p, UpgradePrefs(), mode)
            val without = UpgradeCalculator.totals(c, p, UpgradePrefs(hiddenSections = setOf(PotionBook.SECTION)), mode)
            val potions = totals(c, p, mode).infusions
            val diff = with.qty().mapValues { (id, q) -> q - (without.qty()[id] ?: 0) }.filterValues { it > 0 }
            assertEquals(diff, potions.qty(), "$mode, $p")
            assertEquals(with.rupees - without.rupees, potions.rupees, "$mode, $p")
            assertEquals(with.stepsRemaining - without.stepsRemaining, potions.stepsRemaining, "$mode, $p")
        }
        // With nothing collected: 195 − 145 bugs and 1,510 − 1,330 Rupees.
        val with = UpgradeCalculator.totals(c, UpgradeProgress(), UpgradePrefs(), UpgradeTotalsMode.ALL)
        val without = UpgradeCalculator.totals(c, UpgradeProgress(), UpgradePrefs(hiddenSections = setOf("potions")), UpgradeTotalsMode.ALL)
        assertEquals(195 to 1_510, with.materialCount to with.rupees)
        assertEquals(145 to 1_330, without.materialCount to without.rupees)
        val potions = totals(c, UpgradeProgress(), UpgradeTotalsMode.ALL)
        assertEquals(50 to 180, potions.materialCount to potions.infusions.rupees)
    }
}
