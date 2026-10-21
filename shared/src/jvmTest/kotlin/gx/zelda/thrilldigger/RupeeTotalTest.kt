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

/**
 * The round's running total (never below 0, so in dig order), its range with skipped holes, the dig
 * order from the undo history, and working the skipped holes out from the game's total.
 */
class RupeeTotalTest {
    private fun board(d: Difficulty, vararg rows: String) = DiggerBoard(d, rows.joinToString("").map(Hole::of))

    @Test
    fun theTotalCountsEachColourAndTakesOffRupoors() {
        val b = board(Difficulty.INTERMEDIATE, "GBRSYP", "X?????", "??????", "??????", "??????")
        val total = RupeeTotal.of(b)
        assertEquals(1 + 5 + 20 + 100 + 300 - 10, total.known)
        assertTrue(total.resolved)
        assertEquals("416", total.text)
        assertEquals("0", RupeeTotal.of(DiggerBoard.empty(Difficulty.EXPERT)).text)
    }

    @Test
    fun theTotalNeverGoesIntoDebtSoTheOrderMatters() {
        val b = board(Difficulty.INTERMEDIATE, "RPB???", "??????", "??????", "??????", "??????")
        // Red, Rupoor, Blue: 20 − 10 + 5.
        assertEquals("15", RupeeTotal.of(b, listOf(0, 1, 2)).text)
        // Rupoor, Blue, Red: the Rupoor finds nothing to take.
        assertEquals("25", RupeeTotal.of(b, listOf(1, 2, 0)).text)
        // Blue, Rupoor, Red: 5 − 10 stops at 0.
        assertEquals("20", RupeeTotal.of(b, listOf(2, 1, 0)).text)
        // With no order known, reading order; holes the order leaves out follow in reading order.
        assertEquals("15", RupeeTotal.of(b).text)
        assertEquals("25", RupeeTotal.of(b, listOf(1)).text)
    }

    @Test
    fun aRupoorAtZeroTakesNothing() {
        val b = board(Difficulty.INTERMEDIATE, "PPG???", "??????", "??????", "??????", "??????")
        assertEquals("1", RupeeTotal.of(b).text)
        assertEquals("0", RupeeTotal.of(b, listOf(2, 0, 1)).text)
        assertEquals("0", RupeeTotal.of(board(Difficulty.INTERMEDIATE, "P?????", "??????", "??????", "??????", "??????")).text)
        assertEquals(0, RupeeTotal.add(3, -10))
        assertEquals(10, RupeeTotal.add(20, -10))
    }

    @Test
    fun skippedHolesMakeARange() {
        val total = RupeeTotal.of(board(Difficulty.BEGINNER, "GD???", "?????", "??D??", "?????"))
        assertEquals(1, total.known)
        assertEquals(2, total.skipped)
        assertFalse(total.resolved)
        assertEquals(3, total.min)
        assertEquals(601, total.max)
        assertEquals("3–601", total.text)
    }

    @Test
    fun skippedHolesUnderTheFloor() {
        val one = board(Difficulty.INTERMEDIATE, "DP????", "??????", "??????", "??????", "??????")
        // Skipped, Rupoor: a Green or a Blue is eaten whole, a Gold loses 10.
        assertEquals("0–290", RupeeTotal.of(one, listOf(0, 1)).text)
        // Rupoor, skipped: the Rupoor takes nothing.
        assertEquals("1–300", RupeeTotal.of(one, listOf(1, 0)).text)
        // Two skipped either side of the Rupoor: Greens give 0 + 1, Golds 290 + 300.
        val b = one.with(14, Hole.SKIPPED)
        val two = RupeeTotal.of(b, listOf(0, 1, 14))
        assertEquals(0, two.known)
        assertEquals("1–590", two.text)
    }

    @Test
    fun theDigOrderComesFromTheHistory() {
        val d = Difficulty.INTERMEDIATE
        val history = mutableListOf<DiggerBoard>()
        var b = DiggerBoard.empty(d)
        fun dig(i: Int, h: Hole) {
            history += b
            b = b.with(i, h)
        }
        dig(5, Hole.RED)
        dig(0, Hole.RUPOOR)
        dig(12, Hole.SKIPPED)
        dig(3, Hole.BLUE)
        assertEquals(listOf(5, 0, 12, 3), b.digOrder(history))
        // Correcting a colour, or filling in a skipped hole, keeps its place.
        dig(5, Hole.SILVER)
        dig(12, Hole.GREEN)
        assertEquals(listOf(5, 0, 12, 3), b.digOrder(history))
        // Clearing a hole and digging it again moves it to the end.
        dig(0, Hole.UNKNOWN)
        dig(0, Hole.RUPOOR)
        assertEquals(listOf(5, 12, 3, 0), b.digOrder(history))
        assertEquals("96", RupeeTotal.of(b, b.digOrder(history)).text)
        // Holes dug before the history begins come first, in reading order.
        assertEquals(listOf(3, 5, 12, 0), b.digOrder(history.drop(history.indexOfLast { it.holes[0] == Hole.UNKNOWN })))
        assertEquals(listOf(0, 3, 5, 12), b.digOrder(emptyList()))
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
        assertEquals(TotalInference.NoMatch(3), GameTotal.infer(b, 3))
        assertEquals(TotalInference.NoMatch(0), GameTotal.infer(b, 0))
        assertFailsWith<IllegalArgumentException> { GameTotal.infer(DiggerBoard.empty(Difficulty.BEGINNER), 10) }
    }

    @Test
    fun oneSkippedHoleUnderTheFloor() {
        // The skipped hole, then a Rupoor away from it.
        val b = board(Difficulty.INTERMEDIATE, "P?????", "??????", "??D???", "??????", "??????")
        val order = listOf(14, 0)
        // Above the floor it's certain: a Red less 10.
        assertEquals(TotalInference.Fill(mapOf(14 to Hole.RED)), GameTotal.infer(b, 10, order))
        assertEquals(TotalInference.Fill(mapOf(14 to Hole.SILVER)), GameTotal.infer(b, 90, order))
        // At 0 it was a Green or a Blue, and the total can't tell which.
        val zero = assertIs<TotalInference.Ambiguous>(GameTotal.infer(b, 0, order))
        assertEquals(listOf(listOf(Hole.GREEN), listOf(Hole.BLUE)), zero.combos)
        assertTrue(zero.certain.isEmpty())
        assertEquals("The total can't tell: that hole was Green or Blue.", inferenceText(b, zero))
        assertEquals(TotalInference.NoMatch(5), GameTotal.infer(b, 5, order))
        // In reading order the Rupoor came first and took nothing, so 5 is a Blue.
        assertEquals(TotalInference.Fill(mapOf(14 to Hole.BLUE)), GameTotal.infer(b, 5))
        // A Rupoor beside the skipped hole means it wasn't Green, so the board settles it.
        val beside = board(Difficulty.INTERMEDIATE, "??????", "??????", "??DP??", "??????", "??????")
        assertEquals(TotalInference.Fill(mapOf(14 to Hole.BLUE)), GameTotal.infer(beside, 0, listOf(14, 15)))
    }

    @Test
    fun severalSkippedHolesUnderTheFloor() {
        // Skipped, Rupoor, skipped: the first is lost to the Rupoor, the second is certain.
        val b = board(Difficulty.INTERMEDIATE, "P?????", "??????", "??D???", "??????", "?????D")
        val order = listOf(14, 0, 29)
        val result = assertIs<TotalInference.Ambiguous>(GameTotal.infer(b, 20, order))
        assertEquals(listOf(listOf(Hole.GREEN, Hole.RED), listOf(Hole.BLUE, Hole.RED)), result.combos)
        assertEquals(mapOf(29 to Hole.RED), result.certain)
        assertEquals("The total can't tell: Green + Red, or Blue + Red. Either way, F5 Red: fill it in?", inferenceText(b, result))
        // 1 is a Green after the Rupoor, and either of the two before it.
        val one = assertIs<TotalInference.Ambiguous>(GameTotal.infer(b, 1, order))
        assertEquals(mapOf(29 to Hole.GREEN), one.certain)
        // 30: a Red that kept 10 through the Rupoor, then another Red (from 0, no Rupee is worth 30).
        assertEquals(TotalInference.Fill(mapOf(14 to Hole.RED, 29 to Hole.RED)), GameTotal.infer(b, 30, order))
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
        assertTrue(pair.certain.isEmpty())
        assertTrue("which hole is which" in inferenceText(two, pair))
        // Six holes worth 105: five Greens and a Silver, or a Blue and five Reds.
        val six = board(Difficulty.EXPERT, "D??D???D", "????????", "????D???", "????????", "D??????D")
        val sets = assertIs<TotalInference.Ambiguous>(GameTotal.infer(six, 105))
        assertEquals(listOf(List(5) { Hole.GREEN } + Hole.SILVER, listOf(Hole.BLUE) + List(5) { Hole.RED }), sets.combos)
        assertFalse(sets.more)
        assertTrue(inferenceText(six, sets).startsWith("The total can't tell: Green + Green + Green + Green + Green + Silver, or Blue + Red"))
    }

    /** [GameTotal.ways] against trying every colour for every skipped hole. */
    @Test
    fun waysMatchBruteForce() {
        val digs = listOf(
            listOf(Hole.SKIPPED),
            listOf(Hole.SKIPPED, Hole.RUPOOR),
            listOf(Hole.RUPOOR, Hole.SKIPPED, Hole.RUPOOR, Hole.SKIPPED),
            listOf(Hole.SKIPPED, Hole.RUPOOR, Hole.RUPOOR, Hole.GREEN, Hole.SKIPPED, Hole.RUPOOR, Hole.SKIPPED),
        )
        for (d in digs) {
            val k = d.count { it == Hole.SKIPPED }
            var all = listOf(emptyList<Hole>())
            repeat(k) { all = all.flatMap { w -> Hole.RUPEES.map { w + it } } }
            for (total in 0..1000) {
                val expected = all.filter { w -> RupeeTotal(d).walk { w[it].rupees } == total }.toSet()
                assertEquals(expected, GameTotal.ways(d, total).toSet(), "$d at $total")
            }
        }
    }
}
