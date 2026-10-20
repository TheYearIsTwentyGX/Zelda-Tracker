package gx.zelda.thrilldigger.model

/**
 * Every board the game deals on [difficulty] from a state on one of [loops]: [boards] holds one
 * [PackedBoard] per state, loop by loop, each loop's states in RNG order from its anchor.
 */
class RngBoards(val difficulty: Difficulty, val loops: List<RngLoop>, val boards: LongArray) {
    /** Where each loop's states start in [boards], and the end after the last. */
    val loopStart: IntArray = IntArray(loops.size + 1).also { s -> loops.forEachIndexed { i, l -> s[i + 1] = s[i] + l.length } }

    init {
        require(boards.size == loopStart.last()) { "${boards.size} boards for ${loopStart.last()} states" }
    }

    /**
     * The empty board's result for every loop ([loops].size) and each loop alone, once worked out:
     * every round starts there, and it's the slowest board to count. Two threads racing only work
     * it out twice.
     */
    internal val emptyResults = arrayOfNulls<RngResult>(loops.size + 1)

    fun loopOf(index: Int): Int = (0 until loops.size).first { index < loopStart[it + 1] }

    /** The RNG state that deals [boards]`[index]`. */
    fun stateAt(index: Int): Long {
        val loop = loopOf(index)
        var s = loops[loop].anchor
        repeat(index - loopStart[loop]) { s = SsRng.next(s) }
        return s
    }

    companion object {
        /**
         * Deals every state's board. [onProgress] hears the share done now and then, and
         * [checkCancelled] is called as often, to stop a generation nobody needs any more by throwing.
         */
        fun generate(
            difficulty: Difficulty,
            loops: List<RngLoop> = RngLoops.all,
            onProgress: (Float) -> Unit = {},
            checkCancelled: () -> Unit = {},
        ): RngBoards {
            val total = loops.sumOf { it.length }
            val boards = LongArray(total)
            var n = 0
            for (loop in loops) {
                var state = loop.anchor
                repeat(loop.length) {
                    boards[n++] = ThrillDiggerDeal.deal(difficulty, state)
                    state = SsRng.next(state)
                    if (n and 0xFFFF == 0) {
                        checkCancelled()
                        onProgress(n.toFloat() / total)
                    }
                }
            }
            onProgress(1f)
            return RngBoards(difficulty, loops, boards)
        }
    }
}

/** What fits a board among the RNG's boards. */
data class RngResult(
    /** The odds over the fitting states, each weighted by its loop's basin. */
    val analysis: DiggerAnalysis.Solved,
    /** How many states deal a board that fits. */
    val states: Int,
    /** The loops those states are on, by index into [RngBoards.loops]. */
    val loops: Set<Int>,
)

/**
 * The odds for a board when the game's RNG is taken into account (lepelog's idea; docs/ss.md): of
 * the few million boards the RNG can deal, keep those that fit what's been dug, and count. A state
 * on a loop is weighted by that loop's basin share over its length, so the loops are as likely as a
 * random start makes them and the states on each are equally likely.
 */
object RngSolver {
    /**
     * The odds over the states that fit [board], only on loop [onlyLoop] when it's given, or null
     * when none fits: the RNG hasn't settled into a loop yet, or something was entered wrong.
     */
    fun solve(board: DiggerBoard, table: RngBoards, onlyLoop: Int? = null): RngResult? {
        val d = board.difficulty
        require(d == table.difficulty)
        if (board.isEmpty) {
            val slot = onlyLoop ?: table.loops.size
            return table.emptyResults[slot] ?: count(board, table, onlyLoop).also { table.emptyResults[slot] = it }
        }
        return count(board, table, onlyLoop)
    }

    private fun count(board: DiggerBoard, table: RngBoards, onlyLoop: Int?): RngResult? {
        val d = board.difficulty
        val filter = Filter(board)
        val around = Neighbours.of(d)
        val undug = board.holes.indices.filter { board.holes[it] == Hole.UNKNOWN }.toIntArray()
        val bombW = DoubleArray(d.cells)
        val rupoorW = DoubleArray(d.cells)
        val colourW = Array(d.cells) { DoubleArray(Hole.RUPEES.size) }
        var total = 0.0
        var states = 0
        val loops = mutableSetOf<Int>()
        for (loop in table.loops.indices) {
            if (onlyLoop != null && loop != onlyLoop) continue
            val w = table.loops[loop].share / table.loops[loop].length
            for (k in table.loopStart[loop] until table.loopStart[loop + 1]) {
                val packed = table.boards[k]
                if (!filter.fits(packed)) continue
                states++
                loops += loop
                total += w
                val hazards = PackedBoard.hazards(packed)
                val bombs = PackedBoard.bombs(packed)
                for (i in undug) {
                    val bit = 1L shl i
                    when {
                        bombs and bit != 0L -> bombW[i] += w
                        hazards and bit != 0L -> rupoorW[i] += w
                        else -> colourW[i][COLOUR[(hazards and around[i]).countOneBits()]] += w
                    }
                }
            }
        }
        if (states == 0) return null
        val odds = undug.associateWith { i -> ThrillDiggerSolver.holeOdds(i, bombW[i] / total, rupoorW[i] / total, colourW[i]) }
        return RngResult(DiggerAnalysis.Solved(odds, ThrillDiggerSolver.recommend(odds.values)), states, loops)
    }

    /** The indices in [table] of the states that deal exactly [board], every hole of which is filled in. */
    fun dealing(board: DiggerBoard, table: RngBoards): List<Int> {
        require(board.holes.none { it == Hole.UNKNOWN }) { "the board isn't finished" }
        val filter = Filter(board)
        return table.boards.indices.filter { filter.fits(table.boards[it]) }
    }

    /** Hazard count → index of its Rupee colour in [Hole.RUPEES]. */
    private val COLOUR = IntArray(9) { Hole.RUPEES.indexOf(Hole.rupeeFor(it)) }

    /** Whether a packed board agrees with every hole entered on [board]. */
    private class Filter(board: DiggerBoard) {
        private var hazards = 0L
        private var safe = 0L
        private var bombs = 0L
        private var rupoors = 0L
        private val rupeeMasks: LongArray
        private val rupeeMin: IntArray
        private val rupeeMax: IntArray

        init {
            val around = Neighbours.of(board.difficulty)
            val rupees = board.holes.indices.filter { board.holes[it].isRupee }
            board.holes.forEachIndexed { i, hole ->
                val bit = 1L shl i
                when {
                    hole == Hole.BOMB -> { hazards = hazards or bit; bombs = bombs or bit }
                    hole == Hole.RUPOOR -> { hazards = hazards or bit; rupoors = rupoors or bit }
                    hole.isRupee -> safe = safe or bit
                }
            }
            rupeeMasks = LongArray(rupees.size) { around[rupees[it]] }
            rupeeMin = IntArray(rupees.size) { board.holes[rupees[it]].minHazards }
            rupeeMax = IntArray(rupees.size) { board.holes[rupees[it]].maxHazards }
        }

        fun fits(packed: Long): Boolean {
            val h = PackedBoard.hazards(packed)
            if (h and hazards != hazards || h and safe != 0L) return false
            for (k in rupeeMasks.indices) {
                val n = (h and rupeeMasks[k]).countOneBits()
                if (n < rupeeMin[k] || n > rupeeMax[k]) return false
            }
            if (bombs or rupoors != 0L) {
                val b = PackedBoard.bombs(packed)
                if (b and bombs != bombs || b and rupoors != 0L) return false
            }
            return true
        }
    }
}
