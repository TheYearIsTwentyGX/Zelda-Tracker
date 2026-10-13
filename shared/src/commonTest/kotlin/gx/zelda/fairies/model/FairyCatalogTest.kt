package gx.zelda.fairies.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FairyCatalogTest {
    private fun fairy(id: String) = GreatFairy(id, id, "Region", "Somewhere", "Go there.")

    private val catalog = FairyCatalog(
        unlockCosts = listOf(100, 500, 1000, 10000),
        fairies = listOf(fairy("a"), fairy("b"), fairy("c"), fairy("d")),
    )

    @Test
    fun nextCostGoesByHowManyAreOpen() {
        assertEquals(listOf(100, 500, 1000, 10000), (0..3).map { catalog.nextCost(it) })
        assertNull(catalog.nextCost(4))
    }

    @Test
    fun remainingCostSumsTheRest() {
        assertEquals(11600, catalog.remainingCost(0))
        assertEquals(11000, catalog.remainingCost(2))
        assertEquals(0, catalog.remainingCost(4))
    }

    @Test
    fun unlockedCountIgnoresUnknownIds() {
        assertEquals(2, catalog.unlockedCount(setOf("a", "c", "not_a_fairy")))
    }
}
