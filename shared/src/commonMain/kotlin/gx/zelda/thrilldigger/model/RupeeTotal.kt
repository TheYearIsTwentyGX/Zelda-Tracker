package gx.zelda.thrilldigger.model

/**
 * The Rupees a round has earned from the holes entered: [known] from the Rupees and Rupoors whose
 * colours are entered, and [skipped] [Hole.SKIPPED] holes, each a Rupee worth 1 to 300.
 */
data class RupeeTotal(val known: Int, val skipped: Int) {
    /** Every skipped hole Green. */
    val min: Int get() = known + skipped * Hole.GREEN.rupees

    /** Every skipped hole Gold. */
    val max: Int get() = known + skipped * Hole.GOLD.rupees

    /** No skipped hole, so the total is [known]. */
    val resolved: Boolean get() = skipped == 0

    /** "125", or "126–425" with a skipped hole. */
    val text: String get() = if (resolved) "$known" else "$min–$max"

    companion object {
        fun of(board: DiggerBoard): RupeeTotal = RupeeTotal(
            known = board.holes.sumOf { if (it.isRupee || it == Hole.RUPOOR) it.rupees else 0 },
            skipped = board.holes.count { it == Hole.SKIPPED },
        )
    }
}

/** What the round's total in the game says about the [Hole.SKIPPED] holes ([GameTotal.infer]). */
sealed interface TotalInference {
    /** One way the skipped holes fit: [fill] gives each its colour, by index. */
    data class Fill(val fill: Map<Int, Hole>) : TotalInference

    /**
     * More than one way: [combos] are the colour sets that add up (sorted, fewest Rupees first), at
     * most [GameTotal.MAX_COMBOS] of them, [more] when there are others.
     */
    data class Ambiguous(val combos: List<List<Hole>>, val more: Boolean) : TotalInference

    /** No colours for the skipped holes add up to [difference] and fit the board. */
    data class NoMatch(val difference: Int) : TotalInference
}

/**
 * Works out the [Hole.SKIPPED] holes' colours from the round's total in the game. The skipped
 * holes are worth the total less the known holes' [RupeeTotal.known]; the five values are
 * distinct, so one skipped hole's colour is certain. With several, every set of colours that adds
 * up is tried in every order on the board, and only orders the board allows ([ThrillDiggerSolver])
 * are kept, since the colours are clues for the holes around them.
 */
object GameTotal {
    const val MAX_COMBOS = 6

    /** Orders of colours tried on the board at most; past that, colour sets aren't narrowed by it. */
    private const val MAX_ORDERS = 200

    /** What [gameTotal] says about [board]'s skipped holes, of which there must be at least one. */
    fun infer(board: DiggerBoard, gameTotal: Int): TotalInference {
        val skipped = board.holes.indices.filter { board.holes[it] == Hole.SKIPPED }
        require(skipped.isNotEmpty()) { "no hole is skipped" }
        val difference = gameTotal - RupeeTotal.of(board).known
        val combos = combos(skipped.size, difference)
        if (combos.isEmpty()) return TotalInference.NoMatch(difference)

        val orders = combos.associateWith { orders(it) }
        val checked = ThrillDiggerSolver.isPossible(board) && orders.values.sumOf { it.size } <= MAX_ORDERS
        val fits: Map<List<Hole>, List<List<Hole>>> = if (!checked) {
            orders
        } else {
            orders.mapValues { (_, os) -> os.filter { o -> ThrillDiggerSolver.isPossible(fill(board, skipped, o)) } }.filterValues { it.isNotEmpty() }
        }
        val all = fits.values.flatten()
        return when {
            all.isEmpty() -> TotalInference.NoMatch(difference)
            all.size == 1 -> TotalInference.Fill(skipped.zip(all.single()).toMap())
            else -> TotalInference.Ambiguous(fits.keys.take(MAX_COMBOS), fits.size > MAX_COMBOS)
        }
    }

    /** The sets of [count] Rupee colours worth [sum] together, each sorted in [Hole.RUPEES] order. */
    internal fun combos(count: Int, sum: Int): List<List<Hole>> {
        val out = mutableListOf<List<Hole>>()
        fun go(from: Int, left: Int, rest: Int, chosen: List<Hole>) {
            if (left == 0) {
                if (rest == 0) out += chosen
                return
            }
            for (c in from until Hole.RUPEES.size) {
                val v = Hole.RUPEES[c].rupees
                // The rest are at least this colour, so too much now is too much for good.
                if (v * left > rest) break
                if (Hole.GOLD.rupees * (left - 1) + v < rest) continue
                go(c, left - 1, rest - v, chosen + Hole.RUPEES[c])
            }
        }
        if (count > 0) go(0, count, sum, emptyList())
        return out
    }

    /** Every distinct order of [colours], up to one more than [MAX_ORDERS]. */
    private fun orders(colours: List<Hole>): List<List<Hole>> {
        val out = mutableListOf<List<Hole>>()
        val left = colours.groupingBy { it }.eachCount().toMutableMap()
        fun go(chosen: List<Hole>) {
            if (out.size > MAX_ORDERS) return
            if (chosen.size == colours.size) {
                out += chosen
                return
            }
            for (h in left.keys.toList()) {
                val n = left.getValue(h)
                if (n == 0) continue
                left[h] = n - 1
                go(chosen + h)
                left[h] = n
            }
        }
        go(emptyList())
        return out
    }

    private fun fill(board: DiggerBoard, holes: List<Int>, colours: List<Hole>): DiggerBoard =
        board.copy(holes = board.holes.toMutableList().also { h -> holes.zip(colours).forEach { (i, c) -> h[i] = c } })
}
