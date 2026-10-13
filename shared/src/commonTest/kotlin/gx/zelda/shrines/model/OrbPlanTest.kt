package gx.zelda.shrines.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrbPlanTest {
    private val upgrades = OrbUpgrades(
        orbsPerUpgrade = 4,
        startingHearts = 3,
        maxHearts = 30,
        startingStaminaWheels = 1,
        maxStaminaVessels = 10,
        bossHearts = 4,
    )

    private fun shrine(id: String, dlc: Boolean = false) =
        Shrine(id, id, "Region", "Trial", TrialType.PUZZLE, dlc = dlc, location = "Somewhere")

    private fun plan(earned: Int, hearts: Int = 0, stamina: Int = 0) = OrbPlan(upgrades, earned, 120, hearts, stamina)

    @Test
    fun onlyBaseGameShrinesGiveOrbs() {
        val catalog = ShrineCatalog(listOf("Region"), upgrades, listOf(shrine("a"), shrine("b"), shrine("c", dlc = true)))
        val plan = OrbPlan.of(catalog, ShrineProgress(completed = setOf("a", "c", "not_a_shrine")))
        assertEquals(1, plan.earned)
        assertEquals(2, plan.total)
        assertEquals(2, catalog.completedCount(setOf("a", "c", "not_a_shrine")))
    }

    @Test
    fun orbsInHandAreWhatPurchasesLeave() {
        val p = plan(earned = 23, hearts = 3, stamina = 2)
        assertEquals(5, p.bought)
        assertEquals(3, p.inHand)
        assertEquals(0, p.readyToBuy)
        assertEquals(1, p.toNext)
        assertTrue(p.isConsistent)
    }

    @Test
    fun readyToBuyCountsWholeUpgrades() {
        val p = plan(earned = 13)
        assertEquals(3, p.readyToBuy)
        assertEquals(3, p.toNext, "13 in hand pay for 3; 16 pay for 4")
        assertEquals(0, plan(earned = 0).readyToBuy)
        assertEquals(4, plan(earned = 0).toNext)
    }

    @Test
    fun buyingMoreThanTheOrbsAllowIsFlagged() {
        val p = plan(earned = 7, hearts = 2)
        assertEquals(-1, p.inHand)
        assertFalse(p.isConsistent)
        assertEquals(0, p.readyToBuy)
        assertEquals(0, p.toNext)
    }

    @Test
    fun whatIsLeftInTheGame() {
        val p = plan(earned = 87, hearts = 15, stamina = 6)
        assertEquals(33, p.unearned)
        assertEquals(30, p.totalUpgrades)
        assertEquals(9, p.upgradesLeft)
        assertEquals(12, p.heartsLeft)
        assertEquals(4, p.staminaLeft)
    }

    @Test
    fun maxingBothTakesMoreThanEveryOrbBuys() {
        val p = plan(earned = 120)
        assertEquals(27, upgrades.maxHeartsBought)
        assertEquals(33, p.upgradesToMaxBoth, "23 Heart Containers after the Divine Beasts' 4, plus 10 Stamina Vessels")
        assertTrue(p.upgradesToMaxBoth > p.totalUpgrades)
    }
}
