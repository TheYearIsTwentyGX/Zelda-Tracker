package gx.zelda.koroks.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HestuPlanTest {
    private val weapon = HestuPouch("weapon", "Weapon", 8, listOf(1, 2, 3, 5, 8, 12, 17, 25, 35, 45, 55))
    private val bow = HestuPouch("bow", "Bow", 5, listOf(1, 2, 3, 5, 8, 12, 17, 25))
    private val shield = HestuPouch("shield", "Shield", 4, listOf(1, 2, 3, 4, 5, 10, 10, 10, 10, 10, 15, 15, 15, 15, 15, 20))
    private val costs = HestuCosts(listOf(weapon, bow, shield))

    private fun plan(collected: Int, vararg bought: Pair<String, Int>) = HestuPlan(costs, collected, 900, bought.toMap())

    @Test
    fun maxingEveryPouchTakes441Seeds() {
        assertEquals(208, weapon.costs.sum())
        assertEquals(73, bow.costs.sum())
        assertEquals(160, shield.costs.sum())
        assertEquals(441, costs.total)
        assertEquals(listOf(19, 13, 20), costs.pouches.map { it.maxSlots })
    }

    @Test
    fun spentIsTheFirstUpgradesOfEachPouch() {
        val p = plan(30, "weapon" to 3, "shield" to 6)
        assertEquals(6 + 25, p.spent)
        assertEquals(-1, p.inHand)
        assertFalse(p.isConsistent)
        assertEquals(11, p.slots(weapon))
        assertEquals(5, p.nextCost(weapon))
        assertEquals(1, p.nextCost(bow))
        assertEquals(10, p.nextCost(shield))
    }

    @Test
    fun affordableBuysTheCheapestUpgradesFirst() {
        // Cheapest first across the pouches: 1 + 1 + 1 + 2 + 2 + 2 = 9 seeds; the next (a 3) doesn't fit in 10.
        assertEquals(6, plan(10).affordable)
        assertEquals(0, plan(0).affordable)
        assertEquals(costs.pouches.sumOf { it.costs.size }, plan(900).affordable)
    }

    @Test
    fun boughtIsClampedToWhatAPouchTakes() {
        val p = plan(441, "weapon" to 50, "bow" to -3)
        assertEquals(11, p.boughtIn(weapon))
        assertEquals(0, p.boughtIn(bow))
        assertNull(p.nextCost(weapon))
        assertEquals(208, p.spent)
    }

    @Test
    fun maxedPlanHasNothingLeft() {
        val p = plan(500, "weapon" to 11, "bow" to 8, "shield" to 16)
        assertTrue(p.isMaxed)
        assertEquals(0, p.upgradesLeft)
        assertEquals(0, p.toMax)
        assertEquals(59, p.inHand)
        assertEquals(0, p.affordable)
    }

    @Test
    fun stillToCollectCountsSeedsInHand() {
        assertEquals(441, plan(0).stillToCollect)
        val p = plan(100, "weapon" to 4)
        assertEquals(441 - 11, p.toMax)
        assertEquals(441 - 100, p.stillToCollect)
        assertEquals(0, plan(441).stillToCollect)
    }
}
