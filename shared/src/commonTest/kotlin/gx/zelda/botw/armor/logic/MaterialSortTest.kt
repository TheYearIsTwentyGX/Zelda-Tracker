package gx.zelda.botw.armor.logic

import gx.zelda.botw.armor.model.Material
import gx.zelda.botw.armor.model.MaterialSort
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialSortTest {
    private fun line(name: String, qty: Int, gameOrder: Int?) =
        MaterialTotal(Material(name.lowercase(), name, gameOrder = gameOrder), qty, emptyList())

    private val totals = MaterialTotals(
        lines = listOf(
            line("Amber", 30, gameOrder = 2),
            line("Acorn", 5, gameOrder = null),
            line("Bokoblin Horn", 30, gameOrder = 0),
            line("Zora Scale", 10, gameOrder = 1),
        ),
        piecesCounted = 0,
        tiersRemaining = 0,
    )

    private fun order(sort: MaterialSort) = totals.sorted(sort).map { it.material.name }

    @Test
    fun mostNeededBreaksTiesByName() =
        assertEquals(listOf("Amber", "Bokoblin Horn", "Zora Scale", "Acorn"), order(MaterialSort.MOST_NEEDED))

    @Test
    fun alphabetical() =
        assertEquals(listOf("Acorn", "Amber", "Bokoblin Horn", "Zora Scale"), order(MaterialSort.ALPHABETICAL))

    @Test
    fun inGameFollowsGameOrderWithUnknownsLast() =
        assertEquals(listOf("Bokoblin Horn", "Zora Scale", "Amber", "Acorn"), order(MaterialSort.IN_GAME))
}
