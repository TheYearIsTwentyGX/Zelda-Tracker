package gx.zelda.thrilldigger

import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.GameTotal
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.RupeeTotal
import gx.zelda.thrilldigger.model.TotalInference
import gx.zelda.thrilldigger.ui.inferenceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The round's running total, its range with skipped holes, and working them out from the game's total. */
class RupeeTotalTest {
    private fun board(d: Difficulty, vararg rows: String) = DiggerBoard(d, rows.joinToString("").map(Hole::of))

    @Test
    fun theTotalCountsEachColourAndTakesOffRupoors() {
        val b = board(Difficulty.INTERMEDIATE, "GBRSYP", "X?????", "??????", "??????", "??????")
        val total = RupeeTotal.of(b)
        assertEquals(RupeeTotal(1 + 5 + 20 + 100 + 300 - 10, 0), total)
        assertTrue(total.resolved)
        assertEquals("416", total.text)
        assertEquals(RupeeTotal(0, 0), RupeeTotal.of(DiggerBoard.empty(Difficulty.EXPERT)))
    }

    @Test
    fun skippedHolesMakeARange() {
        val total = RupeeTotal.of(board(Difficulty.BEGINNER, "GD???", "?????", "??D??", "?????"))
        assertEquals(RupeeTotal(1, 2), total)
        assertFalse(total.resolved)
        assertEquals(3, total.min)
        assertEquals(601, total.max)
        assertEquals("3–601", total.text)
    }

    @Test
    fun oneSkippedHoleIsWorkedOut() {
        val b = board(Difficulty.INTERMEDIATE, "G?????", "??????", "??D???", "??????", "??????")
        // Each colour's value is distinct, so the difference names it.
        for (colour in Hole.RUPEES) {
            val result = GameTotal.infer(b, 1 + colour.rupees)
            val fill = assertIs<TotalInference.Fill>(result, "${colour.label}: $result").fill
            assertEquals(mapOf(14 to colour), fill)
        }
        assertEquals("That hole was a Blue Rupee: fill it in?", inferenceText(b, GameTotal.infer(b, 6)))
        // Off by a value no Rupee has.
        assertEquals(TotalInference.NoMatch(2), GameTotal.infer(b, 3))
        assertEquals(TotalInference.NoMatch(-1), GameTotal.infer(b, 0))
        assertFailsWith<IllegalArgumentException> { GameTotal.infer(DiggerBoard.empty(Difficulty.BEGINNER), 10) }
    }

    @Test
    fun aColourTheBoardRulesOutIsntOffered() {
        // A corner has three neighbours: a skipped corner can't have been Silver or Gold.
        val b = board(Difficulty.INTERMEDIATE, "D?????", "??????", "??????", "??????", "??????")
        assertEquals(TotalInference.NoMatch(100), GameTotal.infer(b, 100))
        assertEquals(TotalInference.Fill(mapOf(0 to Hole.RED)), GameTotal.infer(b, 20))
    }

    @Test
    fun theBoardSaysWhichSkippedHoleWasWhich() {
        // A Green and a Blue between two skipped corners; the Bomb beside the first means it's the Blue.
        val b = board(Difficulty.BEGINNER, "DX??D", "?????", "?????", "?????")
        assertEquals(TotalInference.Fill(mapOf(0 to Hole.BLUE, 4 to Hole.GREEN)), GameTotal.infer(b, 6))
        assertEquals("Those holes were A1 Blue, E1 Green: fill them in?", inferenceText(b, GameTotal.infer(b, 6)))
        // Two Blues: both, whichever way round.
        assertEquals(TotalInference.Fill(mapOf(0 to Hole.BLUE, 4 to Hole.BLUE)), GameTotal.infer(b, 10))
    }

    @Test
    fun severalSkippedHolesCanBeAmbiguous() {
        // A Green and a Blue, but nothing says which hole is which.
        val two = board(Difficulty.BEGINNER, "D???D", "?????", "?????", "?????")
        val pair = assertIs<TotalInference.Ambiguous>(GameTotal.infer(two, 6))
        assertEquals(listOf(listOf(Hole.GREEN, Hole.BLUE)), pair.combos)
        assertTrue("which hole is which" in inferenceText(two, pair))
        // Six holes worth 105: five Greens and a Silver, or a Blue and five Reds.
        assertEquals(listOf(List(5) { Hole.GREEN } + Hole.SILVER, listOf(Hole.BLUE) + List(5) { Hole.RED }), GameTotal.combos(6, 105))
        val six = board(Difficulty.EXPERT, "D??D???D", "????????", "????D???", "????????", "D??????D")
        val sets = assertIs<TotalInference.Ambiguous>(GameTotal.infer(six, 105))
        assertEquals(2, sets.combos.size)
        assertFalse(sets.more)
        assertTrue(inferenceText(six, sets).startsWith("It's ambiguous: Green + Green + Green + Green + Green + Silver, or Blue + Red"))
    }

    @Test
    fun combosAddUp() {
        for (k in 1..4) for (sum in 0..1300) {
            val combos = GameTotal.combos(k, sum)
            combos.forEach { c ->
                assertEquals(k, c.size)
                assertEquals(sum, c.sumOf { it.rupees })
                assertEquals(c.sortedBy { Hole.RUPEES.indexOf(it) }, c)
            }
            assertEquals(combos.size, combos.toSet().size)
        }
        assertEquals(listOf(listOf(Hole.BLUE)), GameTotal.combos(1, 5))
        assertTrue(GameTotal.combos(1, 6).isEmpty())
    }
}
