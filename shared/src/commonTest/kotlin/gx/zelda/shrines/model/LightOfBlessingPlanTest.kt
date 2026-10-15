package gx.zelda.shrines.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The planner arithmetic with TotK's numbers: Lights of Blessing, six boss hearts and A Call from the Depths. */
class LightOfBlessingPlanTest {
    private val upgrades = OrbUpgrades(
        orbsPerUpgrade = 4,
        startingHearts = 3,
        maxHearts = 40,
        startingStaminaWheels = 1,
        maxStaminaVessels = 10,
        bossHearts = 6,
        currency = BlessingCurrency.LIGHT_OF_BLESSING,
        freeUpgrade = "A Call from the Depths",
    )

    private val catalog = ShrineCatalog(
        listOf("Lookout Landing"),
        upgrades,
        (1..152).map { Shrine("s$it", "S$it", "Lookout Landing", "Trial", TrialType.PUZZLE, location = "Somewhere") },
    )

    private fun plan(progress: ShrineProgress) = OrbPlan.of(catalog, progress, includeDlc = true, beastsFreed = null)

    private fun completed(n: Int) = (1..n).map { "s$it" }.toSet()

    @Test
    fun everyShrineGivesOneLightAndFourBuyAnUpgrade() {
        val p = plan(ShrineProgress(completed = completed(4)))
        assertEquals(4, p.earned)
        assertEquals(152, p.total)
        assertEquals(1, p.readyToBuy, "the Great Sky Island's four shrines buy one upgrade")
        assertEquals(38, p.totalUpgrades)

        val bought = plan(ShrineProgress(completed = completed(23), heartsBought = 3, staminaBought = 2))
        assertEquals(3, bought.inHand)
        assertEquals(1, bought.toNext)
        assertEquals(33, bought.upgradesLeft)
        assertEquals(129, bought.unearned)
    }

    @Test
    fun bossHeartsAreEnteredByHandAndCappedAtSix() {
        assertEquals(4, plan(ShrineProgress(bossHearts = 4)).beastHearts)
        assertEquals(6, plan(ShrineProgress(bossHearts = 9)).beastHearts)
        val p = plan(ShrineProgress(bossHearts = 6, heartsBought = 31))
        assertEquals(40, p.hearts)
        assertEquals(31, p.maxHeartsBought)
        assertFalse(p.heartsOverMax)
    }

    @Test
    fun theFreeUpgradeCountsAsAHeartOrAVessel() {
        val heart = plan(ShrineProgress(bossHearts = 6, heartsBought = 30, freeUpgrade = UpgradeKind.HEART))
        assertEquals(40, heart.hearts)
        assertEquals(30, heart.maxHeartsBought, "one fewer for the statues to sell")
        assertEquals(7, heart.heartsNotBought)
        assertEquals(10, heart.maxStaminaBought)

        val vessel = plan(ShrineProgress(staminaBought = 9, freeUpgrade = UpgradeKind.STAMINA))
        assertEquals(10, vessel.staminaVessels)
        assertEquals(9, vessel.maxStaminaBought)
        assertEquals(0, vessel.staminaLeft)
        assertEquals(3, vessel.hearts)

        val overMax = plan(ShrineProgress(bossHearts = 6, heartsBought = 31, freeUpgrade = UpgradeKind.HEART))
        assertTrue(overMax.heartsOverMax, "41 hearts")
    }

    @Test
    fun aGameWithoutAFreeUpgradeIgnoresAStoredChoice() {
        val botw = ShrineCatalog(listOf("R"), upgrades.copy(freeUpgrade = null), catalog.shrines)
        assertNull(OrbPlan.of(botw, ShrineProgress(freeUpgrade = UpgradeKind.HEART), includeDlc = true).freeUpgrade)
    }

    /** 38 + 6 + 1 = 45 upgrades against the 47 that 40 hearts and 3 wheels take. */
    @Test
    fun heartsAndStaminaCantBothBeMaxed() {
        val p = plan(ShrineProgress())
        assertEquals(40, p.upgradesToMaxBoth, "31 statue hearts + 10 vessels - the free one")
        assertTrue(p.upgradesToMaxBoth - p.totalUpgrades == 2, "two short")

        // 40 hearts and 8 vessels uses everything.
        val hearts = plan(ShrineProgress(completed = completed(152), bossHearts = 6, freeUpgrade = UpgradeKind.HEART, heartsBought = 30, staminaBought = 8))
        assertEquals(40, hearts.hearts)
        assertEquals(0, hearts.inHand)
        assertEquals(0, hearts.upgradesLeft)
        assertTrue(hearts.isConsistent)
        // 38 hearts and 3 wheels does too.
        val stamina = plan(ShrineProgress(completed = completed(152), bossHearts = 6, freeUpgrade = UpgradeKind.STAMINA, heartsBought = 29, staminaBought = 9))
        assertEquals(38, stamina.hearts)
        assertEquals(10, stamina.staminaVessels)
        assertEquals(0, stamina.inHand)
    }
}
