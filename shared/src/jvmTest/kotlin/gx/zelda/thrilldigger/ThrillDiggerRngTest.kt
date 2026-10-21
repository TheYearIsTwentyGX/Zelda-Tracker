package gx.zelda.thrilldigger

import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.PackedBoard
import gx.zelda.thrilldigger.model.RngBoards
import gx.zelda.thrilldigger.model.RngLoop
import gx.zelda.thrilldigger.model.RngLoops
import gx.zelda.thrilldigger.model.RngSolver
import gx.zelda.thrilldigger.model.SsRng
import gx.zelda.thrilldigger.model.ThrillDiggerDeal
import gx.zelda.thrilldigger.model.ThrillDiggerSolver
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** The RNG port, the dealer, the RNG's board table and the odds over it. */
class ThrillDiggerRngTest {
    /** Two made-up "loops" (any run of states will do for the table), small enough to enumerate. */
    private val testLoops = listOf(RngLoop(123_456_789L, 3000, 0.7), RngLoop(987_654_321L, 2000, 0.3))

    private fun assertClose(expected: Double, actual: Double, message: String) =
        assertTrue(abs(expected - actual) <= 1e-9, "$message: expected $expected, got $actual")

    @Test
    fun theRngStepsAsTheGamesDoes() {
        // state × 1664525 + 1013904223, the 64-bit result's high half folded into its low half.
        assertEquals(1013904223L, SsRng.next(0))
        val big = 0xFFFFFFFFL
        val t = big * 1664525L + 1013904223L
        assertEquals(((t ushr 32) + t) and big, SsRng.next(big))
        // The float is the top 23 bits as a mantissa.
        assertEquals(0f, SsRng.float(0))
        assertEquals(0.5f, SsRng.float(0x80000000L))
        assertTrue(SsRng.float(big) < 1f)
    }

    /** Each loop's anchor comes back after exactly its length, and is its smallest state. */
    @Test
    fun theLoopsAreLoops() {
        for (loop in RngLoops.all) {
            var s = SsRng.next(loop.anchor)
            var steps = 1
            var smallest = loop.anchor
            while (s != loop.anchor && steps <= loop.length) {
                smallest = minOf(smallest, s)
                s = SsRng.next(s)
                steps++
            }
            assertEquals(loop.length, steps, "loop at ${loop.anchor}")
            assertEquals(loop.anchor, smallest, "the anchor is the loop's smallest state")
        }
        val shares = RngLoops.all.sumOf { it.share }
        assertTrue(shares in 0.99..1.0001, "basin shares add up to $shares")
        assertEquals(RngLoops.all.sortedByDescending { it.share }, RngLoops.all)
    }

    @Test
    fun boardsPackAndUnpack() {
        val random = Random(1)
        repeat(200) {
            val d = Difficulty.entries.random(random)
            val cells = (0 until d.cells).shuffled(random)
            val bombs = cells.take(d.bombs).fold(0L) { m, i -> m or (1L shl i) }
            val rupoors = cells.drop(d.bombs).take(d.rupoors).fold(0L) { m, i -> m or (1L shl i) }
            val packed = PackedBoard.pack(bombs, rupoors)
            assertEquals(bombs or rupoors, PackedBoard.hazards(packed))
            assertEquals(bombs, PackedBoard.bombs(packed))
            val board = PackedBoard.unpack(d, packed)
            assertEquals(d.bombs, board.holes.count { it == Hole.BOMB })
            assertEquals(d.rupoors, board.holes.count { it == Hole.RUPOOR })
            assertTrue(ThrillDiggerSolver.isPossible(board))
        }
    }

    @Test
    fun theBlockCheckFindsEvery2x2() {
        val random = Random(2)
        repeat(2000) {
            val d = Difficulty.entries.random(random)
            val hazards = (0 until d.cells).shuffled(random).take(d.hazards + random.nextInt(0, 6)).toSet()
            val mask = hazards.fold(0L) { m, i -> m or (1L shl i) }
            val expected = (0 until d.height - 1).any { r ->
                (0 until d.width - 1).any { c -> val i = r * d.width + c; i in hazards && i + 1 in hazards && i + d.width in hazards && i + d.width + 1 in hazards }
            }
            assertEquals(expected, ThrillDiggerDeal.hasBlock(d, mask), "${d.label} $hazards")
        }
    }

    @Test
    fun dealtBoardsFollowTheRules() {
        var state = 42L
        var blocks = 0
        repeat(5000) {
            state = SsRng.next(state)
            for (d in Difficulty.entries) {
                val packed = ThrillDiggerDeal.deal(d, state)
                val board = PackedBoard.unpack(d, packed)
                assertEquals(d.bombs, board.holes.count { it == Hole.BOMB })
                assertEquals(d.rupoors, board.holes.count { it == Hole.RUPOOR })
                if (ThrillDiggerDeal.hasBlock(d, PackedBoard.hazards(packed))) blocks++
                assertTrue(ThrillDiggerSolver.isPossible(board))
            }
        }
        // A block survives only eleven deals in a row with one: about 0.4¹¹ of the time on Expert.
        assertTrue(blocks < 5, "$blocks boards kept a block")
    }

    /** The odds over a small table, against counting its fitting states directly. */
    @Test
    fun rngOddsMatchCountingTheFittingStates() {
        val random = Random(3)
        for (d in listOf(Difficulty.INTERMEDIATE, Difficulty.EXPERT)) {
            val table = RngBoards.generate(d, testLoops)
            repeat(20) {
                val truth = PackedBoard.unpack(d, table.boards[random.nextInt(table.boards.size)])
                val dug = truth.holes.indices.filter { truth.holes[it].isRupee }.shuffled(random).take(random.nextInt(0, 5)).toSet()
                val shown = DiggerBoard(d, truth.holes.mapIndexed { i, h -> if (i in dug) h else Hole.UNKNOWN })
                val result = assertNotNull(RngSolver.solve(shown, table), "the true board fits")

                var total = 0.0
                val bomb = DoubleArray(d.cells)
                val rupoor = DoubleArray(d.cells)
                var states = 0
                for (loop in testLoops.indices) for (k in table.loopStart[loop] until table.loopStart[loop + 1]) {
                    val board = PackedBoard.unpack(d, table.boards[k])
                    if (shown.holes.indices.any { shown.holes[it] != Hole.UNKNOWN && shown.holes[it] != board.holes[it] }) continue
                    val w = testLoops[loop].share / testLoops[loop].length
                    states++
                    total += w
                    board.holes.forEachIndexed { i, h -> if (h == Hole.BOMB) bomb[i] += w else if (h == Hole.RUPOOR) rupoor[i] += w }
                }
                assertEquals(states, result.states)
                for ((i, o) in result.analysis.odds) {
                    assertClose(bomb[i] / total, o.bomb, "${d.label} hole $i bomb")
                    assertClose(rupoor[i] / total, o.rupoor, "${d.label} hole $i rupoor")
                    if (o.safe > 1e-9) assertClose(1.0, o.colours.sum(), "${d.label} hole $i colours add up")
                }
            }
        }
    }

    /** A skipped hole keeps the states with a Rupee of any colour there, and only those. */
    @Test
    fun skippedHolesFitAnyRupee() {
        val random = Random(5)
        for (d in listOf(Difficulty.INTERMEDIATE, Difficulty.EXPERT)) {
            val table = RngBoards.generate(d, testLoops)
            repeat(10) {
                val truth = PackedBoard.unpack(d, table.boards[random.nextInt(table.boards.size)])
                val rupees = truth.holes.indices.filter { truth.holes[it].isRupee }.shuffled(random)
                val seen = rupees.take(3).toSet()
                val skipped = rupees.drop(3).take(3).toSet()
                val shown = DiggerBoard(d, truth.holes.mapIndexed { i, h -> if (i in seen) h else if (i in skipped) Hole.SKIPPED else Hole.UNKNOWN })
                val result = assertNotNull(RngSolver.solve(shown, table), "the true board fits")
                var states = 0
                val hazard = DoubleArray(d.cells)
                var total = 0.0
                for (loop in testLoops.indices) for (k in table.loopStart[loop] until table.loopStart[loop + 1]) {
                    val board = PackedBoard.unpack(d, table.boards[k])
                    if (seen.any { board.holes[it] != shown.holes[it] } || skipped.any { !board.holes[it].isRupee }) continue
                    val w = testLoops[loop].share / testLoops[loop].length
                    states++
                    total += w
                    board.holes.forEachIndexed { i, h -> if (h.isHazard) hazard[i] += w }
                }
                assertEquals(states, result.states)
                assertTrue(skipped.none { it in result.analysis.odds }, "no odds on a skipped hole")
                for ((i, o) in result.analysis.odds) assertClose(hazard[i] / total, o.hazard, "${d.label} hole $i hazard")
            }
            // A skipped hole on a hazard rules out the state that put it there.
            val truth = PackedBoard.unpack(d, table.boards[10])
            val onHazard = DiggerBoard.empty(d).let { b -> truth.holes.indices.filter { truth.holes[it].isHazard }.fold(b) { acc, i -> acc.with(i, Hole.SKIPPED) } }
            assertTrue(RngSolver.solve(onHazard, table).let { it == null || it.states < RngSolver.solve(DiggerBoard.empty(d), table)!!.states })
        }
    }

    @Test
    fun aLoopCanBeSingledOutAndABoardCanFitNothing() {
        val table = RngBoards.generate(Difficulty.EXPERT, testLoops)
        val truth = PackedBoard.unpack(Difficulty.EXPERT, table.boards[10])
        val rupeesOnly = truth.copy(holes = truth.holes.map { if (it.isRupee) it else Hole.UNKNOWN })
        assertTrue(0 in assertNotNull(RngSolver.solve(rupeesOnly, table)).loops, "state 10 is on the first loop")
        RngSolver.solve(rupeesOnly, table, onlyLoop = 1)?.let { assertEquals(setOf(1), it.loops) }
        // The same hazards with a Bomb and a Rupoor swapped: a possible board, but not one this table deals.
        val bomb = truth.holes.indexOf(Hole.BOMB)
        val rupoor = truth.holes.indexOf(Hole.RUPOOR)
        val swapped = truth.with(bomb, Hole.RUPOOR).with(rupoor, Hole.BOMB)
        assertTrue(ThrillDiggerSolver.isPossible(swapped))
        assertNull(RngSolver.solve(swapped, table))
        // The exact board is dealt by its own state.
        assertTrue(10 in RngSolver.dealing(truth, table))
        assertEquals(table.boards[10], ThrillDiggerDeal.deal(Difficulty.EXPERT, table.stateAt(10)))
    }

    /** The whole Expert table: its size, and a solve fast enough for a phone. */
    @Test
    fun theExpertTableIsQuickToSearch() {
        val table: RngBoards
        val listing = measureTime { table = RngBoards.generate(Difficulty.EXPERT) }
        assertEquals(RngLoops.all.sumOf { it.length }, table.boards.size)
        val random = Random(4)
        val truth = PackedBoard.unpack(Difficulty.EXPERT, table.boards[random.nextInt(table.boards.size)])
        val rupees = truth.holes.indices.filter { truth.holes[it].isRupee }.shuffled(random)
        var slowest = 0L
        for (dug in listOf(0, 1, 3, 6)) {
            val shown = DiggerBoard(Difficulty.EXPERT, truth.holes.mapIndexed { i, h -> if (i in rupees.take(dug)) h else Hole.UNKNOWN })
            RngSolver.solve(shown, table)
            val t = measureTime { assertNotNull(RngSolver.solve(shown, table)) }.inWholeMilliseconds
            slowest = maxOf(slowest, t)
        }
        println("Expert RNG table: ${table.boards.size} boards listed in ${listing.inWholeMilliseconds} ms; slowest solve $slowest ms")
        assertTrue(slowest < 1500, "slowest RNG solve $slowest ms")
    }

    /**
     * A real Expert round (the export crash's board): all 24 Rupees and 3 Rupoors entered, the other
     * 13 holes left undug. Every Rupee dug makes it finished, its undug holes hazards of either kind,
     * and exactly one of the RNG's boards deals it, on the biggest loop.
     */
    @Test
    fun aRealRoundWithItsHazardsUndugIsDealtOnce() {
        val board = DiggerBoard.decode(Difficulty.EXPERT, "BRPR??BB??R?RBRPR?RRRBR??BB??R?RBBBRPR??")
        assertEquals(24, board.holes.count { it.isRupee })
        assertEquals(3, board.holes.count { it == Hole.RUPOOR })
        assertEquals(13, board.holes.count { it == Hole.UNKNOWN })
        assertEquals(0, board.rupeesLeft)
        assertTrue(board.isFinished)
        val table = RngBoards.generate(Difficulty.EXPERT)
        val fit = assertNotNull(RngSolver.solve(board, table))
        assertEquals(1, fit.states)
        assertEquals(setOf(0), fit.loops)
        val dealt = RngSolver.dealing(board, table)
        assertEquals(1, dealt.size)
        val truth = PackedBoard.unpack(Difficulty.EXPERT, table.boards[dealt.single()])
        assertEquals(0, table.loopOf(dealt.single()))
        board.holes.forEachIndexed { i, h ->
            when (h) {
                Hole.UNKNOWN -> assertTrue(truth.holes[i].isHazard, "undug hole $i is a hazard")
                else -> assertEquals(h, truth.holes[i], "hole $i")
            }
        }
    }

    /** dealing() takes exactly the boards DiggerBoard.isFinished calls finished, and never throws on them. */
    @Test
    fun dealingAgreesWithIsFinished() {
        for (d in Difficulty.entries) {
            val table = RngBoards.generate(d, testLoops)
            val truth = PackedBoard.unpack(d, table.boards[42])
            assertTrue(truth.isFinished)
            assertTrue(42 in RngSolver.dealing(truth, table), "${d.label}: every hole entered")
            // Every Rupee dug, hazards not: still finished, and still dealt by its state.
            val rupeesOnly = truth.copy(holes = truth.holes.map { if (it.isRupee) it else Hole.UNKNOWN })
            assertTrue(rupeesOnly.isFinished)
            assertTrue(42 in RngSolver.dealing(rupeesOnly, table), "${d.label}: Rupees only")
            // A Rupee short: not finished, and dealing() says so rather than guess.
            val short = rupeesOnly.with(rupeesOnly.holes.indexOfFirst { it.isRupee }, Hole.UNKNOWN)
            assertFalse(short.isFinished)
            assertFailsWith<IllegalArgumentException> { RngSolver.dealing(short, table) }
            // A Rupee entered on a hazard: too few undug holes for the hazards, which nothing deals.
            val hazard = truth.holes.indexOfFirst { it.isHazard }
            val tooFew = rupeesOnly.with(hazard, Hole.GREEN)
            assertTrue(tooFew.isFinished)
            assertTrue(RngSolver.dealing(tooFew, table).isEmpty())
        }
    }
}
