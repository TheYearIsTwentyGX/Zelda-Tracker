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

    private fun shrine(id: String, dlc: Boolean = false, orb: Boolean = true) =
        Shrine(id, id, "Region", "Trial", TrialType.PUZZLE, dlc = dlc, orb = orb, location = "Somewhere")

    private fun plan(earned: Int, hearts: Int = 0, stamina: Int = 0, beasts: Int = 0) =
        OrbPlan(upgrades, earned, 120, hearts, stamina, beasts)

    private val catalog = ShrineCatalog(
        listOf("Region"),
        upgrades,
        listOf(shrine("a"), shrine("b"), shrine("plateau_dlc", dlc = true), shrine("emblem_dlc", dlc = true, orb = false)),
    )

    @Test
    fun orbsComeFromShrinesThatGiveThem() {
        val progress = ShrineProgress(completed = setOf("a", "plateau_dlc", "emblem_dlc", "not_a_shrine"))
        val plan = OrbPlan.of(catalog, progress, includeDlc = true)
        assertEquals(2, plan.earned, "an emblem shrine gives no orb")
        assertEquals(3, plan.total)
        assertEquals(1, catalog.baseCompleted(progress.completed))
        assertEquals(3, catalog.completedCount(progress.completed))
    }

    @Test
    fun hidingTheDlcDropsItsOrbs() {
        val plan = OrbPlan.of(catalog, ShrineProgress(completed = setOf("a", "plateau_dlc")), includeDlc = false)
        assertEquals(1, plan.earned)
        assertEquals(2, plan.total)
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

    @Test
    fun freedBeastsAddHeartsAndLowerWhatTheStatuesSell() {
        val p = plan(earned = 40, hearts = 5, beasts = 2)
        assertEquals(10, p.hearts, "3 to start + 2 from Divine Beasts + 5 bought")
        assertEquals(25, p.maxHeartsBought)
        assertEquals(20, p.heartsLeft)
        assertFalse(p.heartsOverMax)

        val allBeasts = plan(earned = 120, hearts = 23, beasts = 4)
        assertEquals(30, allBeasts.hearts)
        assertEquals(23, allBeasts.maxHeartsBought)
        assertEquals(0, allBeasts.heartsLeft)
        assertFalse(allBeasts.heartsOverMax)
    }

    @Test
    fun freeingABeastAfterMaxingHeartsIsFlaggedNotChanged() {
        val p = plan(earned = 120, hearts = 27, beasts = 1)
        assertEquals(27, p.heartsBought, "the entered number is kept")
        assertEquals(31, p.hearts)
        assertEquals(26, p.maxHeartsBought)
        assertTrue(p.heartsOverMax)
        assertEquals(0, p.heartsLeft)
    }

    @Test
    fun beastsFreedAreCappedAtTheGamesFour() {
        assertEquals(4, OrbPlan.of(catalog, ShrineProgress(), includeDlc = true, beastsFreed = 6).beastHearts)
        assertEquals(0, OrbPlan.of(catalog, ShrineProgress(), includeDlc = true).beastHearts)
    }
}
