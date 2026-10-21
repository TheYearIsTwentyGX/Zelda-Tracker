package gx.zelda.thrilldigger.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/** The odds for one hole that hasn't been dug. */
data class HoleOdds(
    val index: Int,
    val bomb: Double,
    val rupoor: Double,
    /** The chance it holds a Rupee: 1 − [bomb] − [rupoor]. */
    val safe: Double,
    /** If it holds a Rupee, the chance of each colour, in [Hole.RUPEES] order; all 0 when it can't. */
    val colours: List<Double>,
    /** What a dig here is worth on average: a Rupee's value, −10 for a Rupoor, nothing for a Bomb. */
    val expectedRupees: Double,
    /** How much a dig here tells you, in bits: the entropy of the seven things it might show. */
    val information: Double,
) {
    val hazard: Double get() = bomb + rupoor
}

sealed interface DiggerAnalysis {
    /**
     * Every undug hole's odds, by index, and the hole to dig next ([ThrillDiggerSolver.recommend]).
     * [recommended] is null once no undug hole can hold a Rupee, which is when the round is won.
     */
    data class Solved(val odds: Map<Int, HoleOdds>, val recommended: Int?) : DiggerAnalysis {
        val allRupeesFound: Boolean get() = recommended == null
    }

    /**
     * No board the game can deal fits what's entered. [conflict] is a smallest set of the entered
     * holes that can't all be true together: clearing any one of them makes the rest possible.
     */
    data class Impossible(val conflict: Set<Int>) : DiggerAnalysis
}

/**
 * The exact odds for a Thrill Digger board, over every board the game could have dealt that fits
 * the holes dug so far.
 *
 * **How the game deals** (its hole minigame actor, as ported by lepelog's solver; docs/ss.md): it
 * drops the Bombs, then the Rupoors, into random empty holes, so every set of hazard holes is
 * equally likely and so is every split of them into Bombs and Rupoors. Then, if any 2×2 square is
 * all hazards, it deals again, up to ten times, and keeps the eleventh deal whatever it is. A board
 * with such a block is therefore not impossible, only rare: with p the share of deals that have
 * one, it's dealt p¹⁰ as often as each board without (which comes out (1 − p¹¹) / (1 − p) as
 * often as an even deal). On Expert p is about 0.4, so this changes the odds a lot.
 *
 * **How it counts.** The block rule ties every hole to its neighbours, so holes away from the dug
 * ones aren't interchangeable and can't be counted by plain combinatorics. Instead the board is
 * swept a column at a time (at most 5 holes tall), keeping, for every pair of neighbouring columns'
 * hazards and every number of hazards so far, how many ways the columns to the left fit
 * ([HazardCounter]). That checks each dug Rupee's colour as soon as its right-hand column is
 * placed, enforces the exact hazard total, and runs forward and backward to give every hole's
 * share of the boards, and the hazards around it. It's done twice, with blocks allowed and
 * without, and mixed by the deal's weights. Expert's worst case is a few million steps.
 */
object ThrillDiggerSolver {
    /** Probabilities closer than this count as equal when choosing the next hole. */
    private const val TIE = 1e-9

    /** [board]'s odds; [near] is the hole dug last, if known, for [recommend]. */
    fun solve(board: DiggerBoard, near: Int? = null): DiggerAnalysis {
        val d = board.difficulty
        val bombsFound = board.holes.count { it == Hole.BOMB }
        val rupoorsFound = board.holes.count { it == Hole.RUPOOR }
        val all = HazardCounter(board, blocksAllowed = true)
        if (bombsFound > d.bombs || rupoorsFound > d.rupoors || all.total == 0.0) {
            return DiggerAnalysis.Impossible(conflict(board))
        }
        val clean = HazardCounter(board, blocksAllowed = false)
        val rho = blockWeight(d)
        // A board's weight: 1 without a block, rho with one, and "all" counts both kinds.
        fun mix(clean: Double, all: Double) = clean + rho * (all - clean)
        val total = mix(clean.total, all.total)
        val hazardsLeft = d.hazards - bombsFound - rupoorsFound
        val cleanHazard = clean.hazardWeights()
        val allHazard = all.hazardWeights()
        val cleanAround = clean.safeNeighbourWeights()
        val allAround = all.safeNeighbourWeights()

        val odds = board.holes.indices.filter { board.holes[it] == Hole.UNKNOWN }.associateWith { i ->
            val hazard = (mix(cleanHazard[i], allHazard[i]) / total).coerceIn(0.0, 1.0)
            // The hazards still hidden are Bombs and Rupoors in any order, all equally likely.
            val bomb = if (hazardsLeft == 0) 0.0 else hazard * (d.bombs - bombsFound) / hazardsLeft
            val rupoor = if (hazardsLeft == 0) 0.0 else hazard * (d.rupoors - rupoorsFound) / hazardsLeft
            val byCount = DoubleArray(9) { n -> mix(cleanAround[i][n], allAround[i][n]) }
            val byColour = DoubleArray(Hole.RUPEES.size) { c -> Hole.RUPEES[c].let { (it.minHazards..it.maxHazards).sumOf { n -> byCount[n] } } }
            holeOdds(i, bomb, rupoor, byColour)
        }
        return DiggerAnalysis.Solved(odds, recommend(odds.values, d.width, near))
    }

    /**
     * One hole's [HoleOdds] from its Bomb and Rupoor chances and, for the boards where it holds a
     * Rupee, each colour's weight in [Hole.RUPEES] order (any scale).
     */
    fun holeOdds(index: Int, bomb: Double, rupoor: Double, colourWeights: DoubleArray): HoleOdds {
        val safeWeight = colourWeights.sum()
        val colours = colourWeights.map { if (safeWeight <= 0.0) 0.0 else it / safeWeight }
        val safe = (1.0 - bomb - rupoor).coerceIn(0.0, 1.0)
        val outcomes = colours.map { it * safe } + rupoor + bomb
        return HoleOdds(
            index = index,
            bomb = bomb,
            rupoor = rupoor,
            safe = safe,
            colours = colours,
            expectedRupees = safe * colours.zip(Hole.RUPEES).sumOf { (p, c) -> p * c.rupees } + rupoor * Hole.RUPOOR.rupees,
            information = outcomes.filter { it > 0.0 }.sumOf { -it * ln(it) } / ln(2.0),
        )
    }

    /**
     * The hole to dig next among [odds] on a board [width] holes across, or null when none can hold
     * a Rupee. Lowest Bomb chance, then lowest Rupoor chance, with near-equal chances counted as
     * ties. Then, among holes that are certainly safe (no hazard at all, exactly: both solvers give
     * an exact 0 when no board puts one there), the one nearest [near], the hole dug last, so a
     * Green Rupee's neighbours are dug next to it rather than across the board: fewest king moves
     * ([chebyshev]), then fewest rook steps ([manhattan]). Then the most information, then the best
     * average haul, then the first in reading order. Holes that might hold a hazard ignore [near].
     */
    fun recommend(odds: Collection<HoleOdds>, width: Int, near: Int? = null): Int? {
        fun q(x: Double) = (x / TIE).roundToLong()
        fun certain(o: HoleOdds) = near != null && o.bomb == 0.0 && o.rupoor == 0.0
        return odds.filter { it.safe > TIE }.minWithOrNull(
            compareBy<HoleOdds>(
                { q(it.bomb) },
                { q(it.rupoor) },
                { if (certain(it)) chebyshev(it.index, near!!, width) else Int.MAX_VALUE },
                { if (certain(it)) manhattan(it.index, near!!, width) else Int.MAX_VALUE },
                { -q(it.information) },
                { -q(it.expectedRupees) },
                { it.index },
            ),
        )?.index
    }

    /** King moves between holes [a] and [b] on a board [width] across. */
    fun chebyshev(a: Int, b: Int, width: Int): Int = maxOf(abs(a / width - b / width), abs(a % width - b % width))

    /** Rook steps between holes [a] and [b] on a board [width] across. */
    fun manhattan(a: Int, b: Int, width: Int): Int = abs(a / width - b / width) + abs(a % width - b % width)

    /** True when some board the game can deal fits [board]. */
    fun isPossible(board: DiggerBoard): Boolean {
        val d = board.difficulty
        return board.holes.count { it == Hole.BOMB } <= d.bombs &&
            board.holes.count { it == Hole.RUPOOR } <= d.rupoors &&
            HazardCounter(board, blocksAllowed = true).total > 0.0
    }

    /**
     * A smallest set of [board]'s entered holes that can't all be true: each entered hole is
     * cleared in turn and stays cleared if the board is still impossible without it, so what's
     * left is impossible but becomes possible without any one of its holes.
     */
    fun conflict(board: DiggerBoard): Set<Int> {
        var current = board
        for (i in board.holes.indices) {
            if (current.holes[i] == Hole.UNKNOWN) continue
            val without = current.with(i, Hole.UNKNOWN)
            if (!isPossible(without)) current = without
        }
        return current.holes.indices.filter { current.holes[it] != Hole.UNKNOWN }.toSet()
    }

    /**
     * How often the game deals a board with an all-hazard 2×2 block, relative to one without: p¹⁰
     * against (1 − p¹¹) / (1 − p), where p is the share of even deals with a block.
     */
    fun blockWeight(difficulty: Difficulty): Double {
        val p = blockShare(difficulty)
        return p.pow(10) * (1 - p) / (1 - p.pow(11))
    }

    /** The share of even deals on [difficulty] with an all-hazard 2×2 block. */
    fun blockShare(difficulty: Difficulty): Double = blockShares.getValue(difficulty).value

    private val blockShares: Map<Difficulty, Lazy<Double>> = Difficulty.entries.associateWith { d ->
        lazy {
            val withoutBlocks = HazardCounter(DiggerBoard.empty(d), blocksAllowed = false).total
            1.0 - withoutBlocks / choose(d.cells, d.hazards)
        }
    }

    internal fun choose(n: Int, k: Int): Double = (1..k).fold(1.0) { acc, i -> acc * (n - k + i) / i }
}

/**
 * Counts the sets of hazard holes on [board] that have exactly the difficulty's number of hazards,
 * put a hazard on every Bomb or Rupoor entered and none on an entered Rupee, and give every entered
 * Rupee its colour. A [Hole.SKIPPED] hole is like a Rupee without its colour: never a hazard, and
 * no count to check. With [blocksAllowed] false, sets with an all-hazard 2×2 block don't count.
 *
 * Columns are swept left to right ([forward]) and right to left ([backward]). A state is the
 * hazard pattern of two neighbouring columns (a bit per row) and the number of hazards so far: a
 * Rupee's colour is checked once the columns either side of it are known, and a 2×2 block once its
 * two columns are. The columns off either edge are empty.
 */
internal class HazardCounter(private val board: DiggerBoard, private val blocksAllowed: Boolean) {
    private val width = board.difficulty.width
    private val height = board.difficulty.height
    private val hazards = board.difficulty.hazards
    private val counts = hazards + 1
    private val patterns = 1 shl height

    /** Each column's possible hazard patterns, given what's been dug in it. */
    private val columns: Array<IntArray> = Array(width) { t ->
        var must = 0
        var never = 0
        for (r in 0 until height) {
            val hole = board.holes[r * width + t]
            if (hole.isHazard) must = must or (1 shl r) else if (hole != Hole.UNKNOWN) never = never or (1 shl r)
        }
        (0 until patterns).filter { it and never == 0 && it and must == must }.toIntArray()
    }

    /** The rows of each column that hold a dug Rupee whose colour is known. */
    private val rupeeRows: Array<IntArray> = Array(width) { t -> (0 until height).filter { board.holes[it * width + t].isRupee }.toIntArray() }

    /** Rows r − 1 to r + 1, as a bit mask. */
    private val window = IntArray(height) { r -> ((0b111 shl r) shr 1) and (patterns - 1) }

    private fun column(t: Int): IntArray = if (t < 0 || t >= width) EMPTY else columns[t]

    private fun state(a: Int, b: Int) = a * patterns + b

    /** Whether column [t]'s Rupees show their colours with [a] left of them, [b] their own column and [c] right. */
    private fun rupeesFit(t: Int, a: Int, b: Int, c: Int): Boolean {
        for (r in rupeeRows[t]) {
            val win = window[r]
            // A dug Rupee's own bit is never set, so its whole column window is its neighbours.
            val n = (a and win).countOneBits() + (b and win).countOneBits() + (c and win).countOneBits()
            val hole = board.holes[r * width + t]
            if (n < hole.minHazards || n > hole.maxHazards) return false
        }
        return true
    }

    /** Whether columns [b] and [c] side by side make no all-hazard 2×2 block (always, when allowed). */
    private fun noBlock(b: Int, c: Int): Boolean = blocksAllowed || (b and c).let { it and (it shr 1) } == 0

    private fun fits(t: Int, a: Int, b: Int, c: Int) = noBlock(b, c) && rupeesFit(t, a, b, c)

    /**
     * forward[t][state(a, b) * counts + k]: the ways to fill columns 0..t with column t − 1 = a,
     * column t = b and k hazards, every Rupee left of column t fitting.
     */
    private val forward: Array<DoubleArray> = Array(width) { DoubleArray(patterns * patterns * counts) }.also { f ->
        for (b in column(0)) f[0][state(0, b) * counts + b.countOneBits()] += 1.0
        for (t in 1 until width) {
            val prev = f[t - 1]
            val cur = f[t]
            for (a in column(t - 2)) for (b in column(t - 1)) {
                val from = state(a, b) * counts
                if ((0 until counts).none { prev[from + it] != 0.0 }) continue
                for (c in column(t)) {
                    if (!fits(t - 1, a, b, c)) continue
                    val pc = c.countOneBits()
                    val to = state(b, c) * counts
                    for (k in 0..hazards - pc) cur[to + k + pc] += prev[from + k]
                }
            }
        }
    }

    /** How many hazard sets fit the board. */
    val total: Double = run {
        var sum = 0.0
        for (a in column(width - 2)) for (b in column(width - 1)) {
            if (rupeesFit(width - 1, a, b, 0)) sum += forward[width - 1][state(a, b) * counts + hazards]
        }
        sum
    }

    /**
     * backward[t][state(a, b) * counts + m]: with column t − 1 = a and column t = b, the ways to fill
     * columns t + 1 onward with m more hazards, every Rupee from column t on fitting.
     */
    private val backward: Array<DoubleArray> by lazy {
        Array(width) { DoubleArray(patterns * patterns * counts) }.also { g ->
            for (a in column(width - 2)) for (b in column(width - 1)) {
                if (rupeesFit(width - 1, a, b, 0)) g[width - 1][state(a, b) * counts] = 1.0
            }
            for (t in width - 2 downTo 0) {
                val next = g[t + 1]
                val cur = g[t]
                for (a in column(t - 1)) for (b in column(t)) {
                    val at = state(a, b) * counts
                    for (c in column(t + 1)) {
                        if (!fits(t, a, b, c)) continue
                        val pc = c.countOneBits()
                        val from = state(b, c) * counts
                        for (m in pc..hazards) cur[at + m] += next[from + m - pc]
                    }
                }
            }
        }
    }

    /** For every hole, how many of the fitting hazard sets put a hazard there. */
    fun hazardWeights(): DoubleArray {
        val out = DoubleArray(width * height)
        for (t in 0 until width) {
            val f = forward[t]
            val g = backward[t]
            for (a in column(t - 1)) for (b in column(t)) {
                if (b == 0) continue
                val at = state(a, b) * counts
                var ways = 0.0
                for (k in 0..hazards) ways += f[at + k] * g[at + hazards - k]
                if (ways == 0.0) continue
                for (r in 0 until height) if (b and (1 shl r) != 0) out[r * width + t] += ways
            }
        }
        return out
    }

    /**
     * For every undug hole, [n] → how many fitting hazard sets leave the hole safe with n hazards
     * around it (its Rupee's colour). Dug holes get all zeros.
     */
    fun safeNeighbourWeights(): Array<DoubleArray> {
        val out = Array(width * height) { DoubleArray(9) }
        for (t in 0 until width) {
            val undug = (0 until height).filter { board.holes[it * width + t] == Hole.UNKNOWN }
            if (undug.isEmpty()) continue
            val f = forward[t]
            for (a in column(t - 1)) for (b in column(t)) {
                val at = state(a, b) * counts
                if ((0 until counts).none { f[at + it] != 0.0 }) continue
                for (c in column(t + 1)) {
                    if (!fits(t, a, b, c)) continue
                    val pc = c.countOneBits()
                    val ways = if (t == width - 1) {
                        f[at + hazards]
                    } else {
                        val g = backward[t + 1]
                        val from = state(b, c) * counts
                        var sum = 0.0
                        for (k in 0..hazards - pc) sum += f[at + k] * g[from + hazards - k - pc]
                        sum
                    }
                    if (ways == 0.0) continue
                    for (r in undug) {
                        if (b and (1 shl r) != 0) continue
                        val win = window[r]
                        val n = (a and win).countOneBits() + (b and win).countOneBits() + (c and win).countOneBits()
                        out[r * width + t][n] += ways
                    }
                }
            }
        }
        return out
    }

    private companion object {
        val EMPTY = intArrayOf(0)
    }
}
