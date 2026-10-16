package gx.zelda.figurines.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigurineOddsTest {
    @Test
    fun firstDrawIsCertain() {
        assertEquals(100, FigurineOdds(available = 48, owned = 0).shownChance())
        assertEquals(1, FigurineOdds(available = 48, owned = 0).shellsForCertainty)
    }

    /** Zelda Wiki, Figurine Gallery: 25 % with one shell is 34 % with ten. */
    @Test
    fun eachExtraShellAddsOnePercent() {
        val odds = FigurineOdds(available = 100, owned = 75)
        assertEquals(25, odds.shownChance(1))
        assertEquals(34, odds.shownChance(10))
        assertEquals(100, odds.shownChance(76))
        assertEquals(100, odds.shownChance(500), "capped at 100")
        assertEquals(76, odds.shellsForCertainty)
    }

    /** Zelda Wiki's formula, P = ⌊99 + S − 100C/U⌋ %, wherever it gives at least 1 %. */
    @Test
    fun matchesZeldaWikisFormula() {
        for (u in 1..136) for (c in 0 until u) for (s in 1..100) {
            val wiki = ((99 + s) * u - 100 * c).floorDiv(u)
            if (wiki < s) continue // the game shows 1 % for one shell where the formula gives 0
            assertEquals(minOf(100, wiki), FigurineOdds(u, c).shownChance(s), "U=$u C=$c S=$s")
        }
    }

    /** The last of 136 shows 1 %, but the game rolls at least 6 % with 110 or more owned. */
    @Test
    fun lastFigurineUsesTheHiddenMinimum() {
        val odds = FigurineOdds(available = 136, owned = 135)
        assertEquals(1, odds.shownChance())
        assertEquals(6, odds.actualChance())
        assertEquals(100, odds.shellsForCertainty)
        assertEquals(100.0 / 6, odds.expectedShellsForNext)
    }

    @Test
    fun hiddenMinimumSteps() {
        assertEquals(listOf(15, 15, 12, 12, 9, 9, 6), listOf(0, 49, 50, 79, 80, 109, 110).map(FigurineOdds::minimumChance))
        assertEquals(15, FigurineOdds(available = 48, owned = 45).actualChance(), "shown 6 %, rolled at 15 %")
        assertEquals(29, FigurineOdds(available = 85, owned = 60).actualChance(), "above the minimum, as shown")
    }

    @Test
    fun nothingLeftMeansNoOdds() {
        val odds = FigurineOdds(available = 51, owned = 51)
        assertTrue(odds.complete)
        assertEquals(0, odds.shownChance())
        assertEquals(0, odds.actualChance())
        assertEquals(0.0, odds.expectedShellsForNext)
        assertEquals(0.0, odds.expectedShellsForRest)
    }

    @Test
    fun expectedShellsForTheRestAddsEachStep() {
        assertEquals(1.0, FigurineOdds(available = 1, owned = 0).expectedShellsForRest)
        // 100 % then 50 %: one shell, then two on average.
        assertEquals(3.0, FigurineOdds(available = 2, owned = 0).expectedShellsForRest)
    }
}
