package gx.zelda.thrilldigger

import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerAnalysis
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.ThrillDiggerSolver
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * The solver against boards worked by hand, against brute-force enumeration of every hazard set
 * (weighted the way the game deals), and against boards dealt by the game's own rules.
 */
class ThrillDiggerSolverTest {
    private fun board(d: Difficulty, vararg rows: String): DiggerBoard {
        require(rows.size == d.height && rows.all { it.length == d.width })
        return DiggerBoard(d, rows.joinToString("").map(Hole::of))
    }

    private fun solved(board: DiggerBoard) = assertIs<DiggerAnalysis.Solved>(ThrillDiggerSolver.solve(board))

    private fun assertClose(expected: Double, actual: Double, message: String? = null, tolerance: Double = 1e-9) =
        assertTrue(abs(expected - actual) <= tolerance, "${message ?: ""} expected $expected, got $actual")

    @Test
    fun theRulesAreTheGames() {
        assertEquals(listOf(4 to 0, 4 to 4, 8 to 8), Difficulty.entries.map { it.bombs to it.rupoors })
        assertEquals(listOf(20, 30, 40), Difficulty.entries.map { it.cells })
        assertEquals(listOf(1, 5, 20, 100, 300), Hole.RUPEES.map { it.rupees })
        assertEquals(listOf("0", "1–2", "3–4", "5–6", "7–8"), Hole.RUPEES.map { it.hazardRange })
        assertEquals((0..8).map(Hole::rupeeFor), listOf(Hole.GREEN, Hole.BLUE, Hole.BLUE, Hole.RED, Hole.RED, Hole.SILVER, Hole.SILVER, Hole.GOLD, Hole.GOLD))
        assertEquals(-10, Hole.RUPOOR.rupees)
    }

    @Test
    fun anEmptyBeginnerBoardSharesItsFourBombs() {
        val result = solved(DiggerBoard.empty(Difficulty.BEGINNER))
        assertEquals(20, result.odds.size)
        assertClose(4.0, result.odds.values.sumOf { it.bomb }, "bombs expected on the board")
        assertTrue(result.odds.values.all { it.rupoor == 0.0 }, "Beginner has no Rupoors")
        result.odds.values.forEach { assertClose(1.0, it.bomb + it.rupoor + it.safe) }
        result.odds.values.forEach { o -> assertClose(1.0, o.colours.sum(), "colours of ${o.index}") }
        // Corners have three neighbours, so can never be Silver or Gold.
        assertEquals(0.0, result.odds.getValue(0).colours[3])
        assertEquals(0.0, result.odds.getValue(0).colours[4])
    }

    /** A Green Rupee in the corner clears its three neighbours, so one of them is dug next. */
    @Test
    fun aGreenRupeeClearsItsNeighbours() {
        val b = board(Difficulty.BEGINNER, "G????", "?????", "?????", "?????")
        val result = solved(b)
        for (i in listOf(1, 5, 6)) {
            assertEquals(0.0, result.odds.getValue(i).bomb, "hole $i")
            assertEquals(1.0, result.odds.getValue(i).safe, "hole $i")
        }
        // The other 16 share the 4 Bombs.
        assertClose(4.0, result.odds.values.sumOf { it.bomb })
        assertTrue(result.recommended in listOf(1, 5, 6))
    }

    /**
     * Worked by hand: a Gold Rupee in the middle of the top row has only five neighbours, so it
     * can't be Gold; a Red one there has 3 or 4 of the five hazards around it.
     */
    @Test
    fun aRedRupeeOnTheEdgeNeedsThreeOfItsFive() {
        val b = board(Difficulty.BEGINNER, "??R??", "?????", "?????", "?????")
        val result = solved(b)
        val around = listOf(1, 3, 6, 7, 8)
        val expectedAround = around.sumOf { result.odds.getValue(it).bomb }
        assertTrue(expectedAround in 3.0..4.0, "3–4 Bombs around it, got $expectedAround")
        assertClose(4.0, result.odds.values.sumOf { it.bomb })
        assertTrue(around.none { it == result.recommended }, "the next dig is away from the Red Rupee")
    }

    /** Every Rupee found: the rest are the hazards, certain, and there's nothing left to dig. */
    @Test
    fun aFinishedBoardHasNoRecommendation() {
        repeat(20) { seed ->
            for (d in Difficulty.entries) {
                val truth = deal(d, Random(seed))
                val shown = DiggerBoard(d, truth.holes.map { if (it.isHazard) Hole.UNKNOWN else it })
                val result = solved(shown)
                assertNull(result.recommended)
                assertTrue(result.allRupeesFound)
                truth.holes.indices.filter { truth.holes[it].isHazard }.forEach { i ->
                    assertClose(1.0, result.odds.getValue(i).hazard, "${d.label} seed $seed hole $i")
                }
            }
        }
    }

    /** Fully revealed boards dealt by the game's rules are always possible. */
    @Test
    fun dealtBoardsArePossible() {
        repeat(50) { seed ->
            for (d in Difficulty.entries) assertTrue(ThrillDiggerSolver.isPossible(deal(d, Random(seed))), "${d.label} seed $seed")
        }
    }

    @Test
    fun matchesBruteForceOnEveryBeginnerStart() {
        // The empty board and every single first dig.
        val empty = DiggerBoard.empty(Difficulty.BEGINNER)
        assertMatchesBruteForce(empty)
        for (i in 0 until 20) for (colour in Hole.RUPEES) {
            val b = empty.with(i, colour)
            if (ThrillDiggerSolver.isPossible(b)) assertMatchesBruteForce(b)
        }
    }

    @Test
    fun matchesBruteForceOnDealtBoardsPartlyDug() {
        val random = Random(7)
        for ((d, dug) in listOf(Difficulty.BEGINNER to 6, Difficulty.INTERMEDIATE to 12, Difficulty.EXPERT to 20, Difficulty.EXPERT to 24)) {
            repeat(if (d == Difficulty.EXPERT) 4 else 8) {
                val truth = deal(d, random)
                val shown = reveal(truth, dug, random)
                assertMatchesBruteForce(shown)
            }
        }
    }

    /** Skipped holes are safe with no count to check: the brute force treats them that way too. */
    @Test
    fun matchesBruteForceWithSkippedHoles() {
        val random = Random(17)
        for ((d, dug) in listOf(Difficulty.BEGINNER to 6, Difficulty.INTERMEDIATE to 10, Difficulty.EXPERT to 20)) {
            repeat(6) {
                val shown = reveal(deal(d, random), dug, random)
                val rupees = shown.holes.indices.filter { shown.holes[it].isRupee }.shuffled(random)
                val skipped = DiggerBoard(d, shown.holes.mapIndexed { i, h -> if (i in rupees.take(1 + rupees.size / 3)) Hole.SKIPPED else h })
                assertMatchesBruteForce(skipped)
                val result = solved(skipped)
                skipped.holes.indices.filter { skipped.holes[it] == Hole.SKIPPED }.forEach { i ->
                    assertNull(result.odds[i], "no odds on skipped hole $i")
                    assertTrue(result.recommended != i)
                }
            }
        }
    }

    @Test
    fun aSkippedHoleIsSafeButNoClue() {
        // Skipped in the corner: it holds no Bomb, so the other 19 share the four, and nothing is cleared.
        val corner = board(Difficulty.BEGINNER, "D????", "?????", "?????", "?????")
        assertMatchesBruteForce(corner)
        val result = solved(corner)
        assertEquals(19, result.odds.size)
        assertClose(4.0, result.odds.values.sumOf { it.bomb })
        assertTrue(listOf(1, 5, 6).all { result.odds.getValue(it).bomb > 0.1 })
        // A Gold corner is impossible, a skipped one isn't, even ringed by Bombs.
        assertTrue(ThrillDiggerSolver.isPossible(board(Difficulty.BEGINNER, "DX???", "XX???", "?????", "?????")))
        // A Bomb or a Rupoor can't be in a skipped hole: with one, finished, the rest are hazards.
        assertEquals(Hole.SKIPPED, Hole.of('D'))
        assertTrue(Hole.SKIPPED.isSafe && !Hole.SKIPPED.isRupee && !Hole.SKIPPED.isHazard)
        val full = board(Difficulty.BEGINNER, "DDDDD", "DDDDD", "DDDDD", "D????")
        assertTrue(full.isFinished)
        solved(full).odds.values.forEach { assertClose(1.0, it.bomb) }
    }

    /**
     * Three hazards in an L: an even deal would make the fourth corner as likely as anywhere near,
     * but the game re-deals a 2×2 block, so it's almost never a hazard there.
     */
    @Test
    fun theReDealMakesA2x2BlockRare() {
        val expert = solved(board(Difficulty.EXPERT, "XP??????", "X???????", "????????", "????????", "????????"))
        assertTrue(expert.odds.getValue(9).hazard < 0.01, "the block's fourth hole: ${expert.odds.getValue(9).hazard}")
        assertTrue(expert.odds.getValue(30).hazard > 0.3, "a hole far away: ${expert.odds.getValue(30).hazard}")
        val b = board(Difficulty.INTERMEDIATE, "XP????", "X?????", "??????", "??????", "??????")
        val intermediate = solved(b)
        assertTrue(intermediate.odds.getValue(7).hazard < 1e-6)
        assertTrue(intermediate.odds.getValue(28).hazard > 0.1)
        assertMatchesBruteForce(b)
    }

    @Test
    fun aboutFourInTenEvenExpertDealsHaveABlock() {
        val share = ThrillDiggerSolver.blockShare(Difficulty.EXPERT)
        // Monte Carlo over even deals as a cross-check of the counted share.
        val random = Random(1)
        val trials = 40_000
        val withBlock = (1..trials).count { hasBlock(Difficulty.EXPERT, (0 until 40).shuffled(random).take(16).toSet()) }
        assertClose(withBlock.toDouble() / trials, share, "Expert block share", tolerance = 0.01)
        assertTrue(share in 0.3..0.5, "Expert share $share")
        // Beginner: four hazards in twenty holes, enumerated outright.
        val sets = combinations((0 until 20).toList(), 4)
        assertClose(sets.count { hasBlock(Difficulty.BEGINNER, it) }.toDouble() / sets.size, ThrillDiggerSolver.blockShare(Difficulty.BEGINNER))
    }

    @Test
    fun impossibleBoardsNameTheirConflict() {
        fun conflict(b: DiggerBoard) = assertIs<DiggerAnalysis.Impossible>(ThrillDiggerSolver.solve(b)).conflict

        // A corner has three neighbours: never Gold.
        assertEquals(setOf(0), conflict(board(Difficulty.BEGINNER, "Y????", "?????", "?????", "????G")))
        // A Green Rupee next to a Bomb.
        assertEquals(setOf(0, 6), conflict(board(Difficulty.BEGINNER, "G????", "?X???", "?????", "????G")))
        // A Red corner needs all three neighbours to be hazards, but one is a Rupee.
        assertEquals(setOf(0, 1), conflict(board(Difficulty.BEGINNER, "RB???", "?????", "?????", "G????")))
        // Five Bombs on a board of four.
        assertEquals(setOf(0, 2, 4, 15, 19), conflict(board(Difficulty.BEGINNER, "X?X?X", "?????", "?????", "X???X")))
        // Beginner has no Rupoors.
        assertEquals(setOf(7), conflict(board(Difficulty.BEGINNER, "?????", "??P??", "?????", "?????")))
        // A Gold Rupee needs 7 of its 8 neighbours to be hazards, but the Green beside it clears
        // four of them.
        assertEquals(setOf(14, 15), conflict(board(Difficulty.INTERMEDIATE, "??????", "??????", "??YG??", "??????", "??????")))
    }

    @Test
    fun conflictsAreMinimal() {
        val random = Random(3)
        repeat(30) {
            val d = Difficulty.entries.random(random)
            var b = DiggerBoard.empty(d)
            while (ThrillDiggerSolver.isPossible(b)) b = b.with(random.nextInt(d.cells), Hole.entries.drop(1).random(random))
            val conflict = ThrillDiggerSolver.conflict(b)
            val only = DiggerBoard(d, b.holes.mapIndexed { i, h -> if (i in conflict) h else Hole.UNKNOWN })
            assertTrue(!ThrillDiggerSolver.isPossible(only), "the conflict alone is impossible")
            conflict.forEach { assertTrue(ThrillDiggerSolver.isPossible(only.with(it, Hole.UNKNOWN)), "without $it it's possible") }
        }
    }

    /** The recommendation prefers no Bomb, then no Rupoor, then information. */
    @Test
    fun theRecommendationBreaksTiesSensibly() {
        // Intermediate: a Green at the top left clears 1, 6 and 7; all are Bomb-free and Rupoor-free,
        // so it picks the one that tells the most.
        val result = solved(board(Difficulty.INTERMEDIATE, "G?????", "??????", "??????", "??????", "??????"))
        val best = result.odds.getValue(result.recommended!!)
        assertEquals(0.0, best.bomb)
        assertEquals(0.0, best.rupoor)
        assertClose(listOf(1, 6, 7).maxOf { result.odds.getValue(it).information }, best.information)
        // Nothing has a lower Bomb chance than the recommended hole, on random boards too.
        val random = Random(11)
        repeat(20) {
            val d = Difficulty.entries.random(random)
            val shown = reveal(deal(d, random), random.nextInt(1, d.cells / 2), random)
            val r = solved(shown)
            val rec = r.recommended ?: return@repeat
            assertTrue(r.odds.values.filter { it.safe > 1e-9 }.all { it.bomb >= r.odds.getValue(rec).bomb - 1e-9 })
        }
    }

    /** Certainly safe holes go nearest the hole dug last: king moves, then rook steps, then information. */
    @Test
    fun certainlySafeHolesStayNearTheLastDig() {
        // Greens in opposite corners clear 1, 6, 7 and 22, 23, 28.
        val b = board(Difficulty.INTERMEDIATE, "G?????", "??????", "??????", "??????", "?????G")
        fun rec(near: Int?) = assertIs<DiggerAnalysis.Solved>(ThrillDiggerSolver.solve(b, near)).recommended!!
        // Next to the last Green, and of its neighbours one in line with it (one step) over the diagonal (two).
        assertTrue(rec(29) in setOf(23, 28), "${rec(29)}")
        assertTrue(rec(0) in setOf(1, 6), "${rec(0)}")
        // The diagonal wins on information alone, without a last dig.
        val plain = solved(b)
        assertEquals(0.0, plain.odds.getValue(plain.recommended!!).hazard)
        assertEquals(ThrillDiggerSolver.solve(b, null), ThrillDiggerSolver.solve(b))
        assertEquals(1, ThrillDiggerSolver.chebyshev(0, 7, 6))
        assertEquals(2, ThrillDiggerSolver.manhattan(0, 7, 6))
        assertEquals(5, ThrillDiggerSolver.chebyshev(0, 29, 6))
        assertEquals(9, ThrillDiggerSolver.manhattan(0, 29, 6))
    }

    /** On random boards: the nearest certainly safe hole when there is one, and no change when there isn't. */
    @Test
    fun theLastDigOnlyMattersAmongCertainlySafeHoles() {
        val random = Random(23)
        var unchanged = 0
        var near = 0
        repeat(60) {
            val d = Difficulty.entries.random(random)
            val shown = reveal(deal(d, random), random.nextInt(1, d.cells / 2), random)
            val last = shown.holes.indices.filter { shown.holes[it] != Hole.UNKNOWN }.random(random)
            val plain = solved(shown)
            val result = assertIs<DiggerAnalysis.Solved>(ThrillDiggerSolver.solve(shown, last))
            assertEquals(plain.odds, result.odds)
            val certain = result.odds.values.filter { it.hazard == 0.0 }.map { it.index }
            if (certain.isEmpty()) {
                unchanged++
                assertEquals(plain.recommended, result.recommended, shown.encode())
            } else {
                near++
                val rec = result.recommended!!
                assertTrue(rec in certain, shown.encode())
                assertEquals(certain.minOf { ThrillDiggerSolver.chebyshev(it, last, d.width) }, ThrillDiggerSolver.chebyshev(rec, last, d.width), shown.encode())
            }
        }
        assertTrue(unchanged > 0 && near > 0, "both kinds of board: $unchanged without, $near with")
    }

    @Test
    fun expertIsFastEnoughForAPhone() {
        val random = Random(5)
        val boards = listOf(DiggerBoard.empty(Difficulty.EXPERT)) +
            List(30) { reveal(deal(Difficulty.EXPERT, random), random.nextInt(1, 20), random) }
        // Warm the JIT up first.
        boards.take(5).forEach { ThrillDiggerSolver.solve(it) }
        var slowest = 0L
        val total = measureTime {
            boards.forEach { b ->
                val t = measureTime { ThrillDiggerSolver.solve(b) }.inWholeMilliseconds
                slowest = maxOf(slowest, t)
            }
        }
        println("Expert: ${boards.size} boards in ${total.inWholeMilliseconds} ms, slowest $slowest ms")
        assertTrue(slowest < 250, "slowest Expert solve took $slowest ms")
    }

    // --- Reference implementations ---

    /** Deals a board the way the game does: Bombs, then Rupoors, re-dealt up to ten times for a 2×2 block. */
    private fun deal(d: Difficulty, random: Random): DiggerBoard {
        var holes: Array<Hole>
        var tries = 0
        while (true) {
            holes = Array(d.cells) { Hole.UNKNOWN }
            var placed = 0
            while (placed < d.bombs) random.nextInt(d.cells).let { if (holes[it] == Hole.UNKNOWN) { holes[it] = Hole.BOMB; placed++ } }
            placed = 0
            while (placed < d.rupoors) random.nextInt(d.cells).let { if (holes[it] == Hole.UNKNOWN) { holes[it] = Hole.RUPOOR; placed++ } }
            val hazards = holes.indices.filter { holes[it].isHazard }.toSet()
            if (!hasBlock(d, hazards) || tries == 10) break
            tries++
        }
        val hazards = holes.indices.filter { holes[it].isHazard }.toSet()
        return DiggerBoard(d, holes.indices.map { i -> if (holes[i].isHazard) holes[i] else Hole.rupeeFor(neighbours(d, i).count { it in hazards }) })
    }

    /** [truth] with [count] random holes dug and the rest unknown. */
    private fun reveal(truth: DiggerBoard, count: Int, random: Random): DiggerBoard {
        val dug = truth.holes.indices.shuffled(random).take(count).toSet()
        return DiggerBoard(truth.difficulty, truth.holes.mapIndexed { i, h -> if (i in dug) h else Hole.UNKNOWN })
    }

    private fun neighbours(d: Difficulty, i: Int): List<Int> {
        val r = i / d.width
        val c = i % d.width
        return (-1..1).flatMap { dr -> (-1..1).map { dc -> r + dr to c + dc } }
            .filter { (rr, cc) -> (rr != r || cc != c) && rr in 0 until d.height && cc in 0 until d.width }
            .map { (rr, cc) -> rr * d.width + cc }
    }

    private fun hasBlock(d: Difficulty, hazards: Set<Int>): Boolean = (0 until d.height - 1).any { r ->
        (0 until d.width - 1).any { c ->
            val i = r * d.width + c
            i in hazards && i + 1 in hazards && i + d.width in hazards && i + d.width + 1 in hazards
        }
    }

    private fun <T> combinations(items: List<T>, k: Int): List<Set<T>> {
        val out = mutableListOf<Set<T>>()
        fun go(start: Int, chosen: List<T>) {
            if (chosen.size == k) { out += chosen.toSet(); return }
            for (i in start..items.size - (k - chosen.size)) go(i + 1, chosen + items[i])
        }
        go(0, emptyList())
        return out
    }

    /**
     * Enumerates every hazard set that fits [b] (a block weighted by [ThrillDiggerSolver.blockWeight])
     * and checks each undug hole's hazard chance and colour chances against the solver's.
     */
    private fun assertMatchesBruteForce(b: DiggerBoard) {
        val d = b.difficulty
        val entered = b.holes.indices.filter { b.holes[it].isHazard }.toSet()
        val undug = b.holes.indices.filter { b.holes[it] == Hole.UNKNOWN }
        val rho = ThrillDiggerSolver.blockWeight(d)
        val rupees = b.holes.indices.filter { b.holes[it].isRupee }.map { it to neighbours(d, it) }
        val neighbourLists = Array(d.cells) { neighbours(d, it) }
        var total = 0.0
        val hazard = DoubleArray(d.cells)
        val colour = Array(d.cells) { DoubleArray(5) }
        val chosen = BooleanArray(d.cells).also { a -> entered.forEach { a[it] = true } }
        val need = d.hazards - entered.size

        fun visit() {
            for ((i, around) in rupees) {
                val n = around.count { chosen[it] }
                if (n < b.holes[i].minHazards || n > b.holes[i].maxHazards) return
            }
            val set = (0 until d.cells).filter { chosen[it] }.toSet()
            val w = if (hasBlock(d, set)) rho else 1.0
            total += w
            for (i in undug) {
                if (chosen[i]) hazard[i] += w else colour[i][Hole.RUPEES.indexOf(Hole.rupeeFor(neighbourLists[i].count { chosen[it] }))] += w
            }
        }
        fun go(start: Int, left: Int) {
            if (left == 0) { visit(); return }
            for (j in start..undug.size - left) {
                chosen[undug[j]] = true
                go(j + 1, left - 1)
                chosen[undug[j]] = false
            }
        }
        go(0, need)

        val result = solved(b)
        for (i in undug) {
            val o = result.odds.getValue(i)
            assertClose(hazard[i] / total, o.hazard, "hole $i hazard on ${b.encode()}")
            val safeWeight = colour[i].sum()
            for (c in 0 until 5) {
                val expected = if (safeWeight == 0.0) 0.0 else colour[i][c] / safeWeight
                assertClose(expected, o.colours[c], "hole $i colour $c on ${b.encode()}")
            }
        }
    }
}
