package gx.zelda.thrilldigger.model

/**
 * The Rupees a round has earned from the holes entered, [digs] being its Rupees, Rupoors and
 * [Hole.SKIPPED] holes (each some Rupee worth 1 to 300) in the order they were dug.
 *
 * The game's total never goes below 0: a Rupoor dug with less than 10 takes it to 0, not into
 * debt. So the total depends on the order, and is walked dig by dig ([add]): a Red, a Rupoor and
 * a Blue make 15, a Rupoor, a Blue and a Red 25.
 */
data class RupeeTotal(val digs: List<Hole>) {
    val skipped: Int get() = digs.count { it == Hole.SKIPPED }

    /** The entered holes alone, every skipped hole worth nothing. */
    val known: Int get() = walk { 0 }

    /** Every skipped hole Green. Taking the floor dig by dig keeps the total rising with each hole's worth, so this is the least. */
    val min: Int get() = walk { Hole.GREEN.rupees }

    /** Every skipped hole Gold. */
    val max: Int get() = walk { Hole.GOLD.rupees }

    /** No skipped hole, so the total is [known]. */
    val resolved: Boolean get() = skipped == 0

    /** "125", or "126–425" with a skipped hole. */
    val text: String get() = if (resolved) "$known" else "$min–$max"

    /** The total with the k-th skipped hole (in dig order) worth [skippedWorth] (k). */
    fun walk(skippedWorth: (Int) -> Int): Int {
        var total = 0
        var k = 0
        for (hole in digs) total = add(total, if (hole == Hole.SKIPPED) skippedWorth(k++) else hole.rupees)
        return total
    }

    companion object {
        /** [total] after digging something worth [rupees]: never below 0. */
        fun add(total: Int, rupees: Int): Int = maxOf(0, total + rupees)

        /**
         * [board]'s holes that count towards the total, in [order] (the dig order,
         * [DiggerBoard.digOrder]); any that [order] leaves out follow in reading order.
         */
        fun counted(board: DiggerBoard, order: List<Int>): List<Int> {
            fun counts(i: Int) = board.holes[i].let { it.isSafe || it == Hole.RUPOOR }
            val first = order.distinct().filter { it in board.holes.indices && counts(it) }
            return first + board.holes.indices.filter { counts(it) && it !in first }
        }

        /** [board]'s total, its holes dug in [order]; reading order when it isn't known. */
        fun of(board: DiggerBoard, order: List<Int> = emptyList()): RupeeTotal =
            RupeeTotal(counted(board, order).map { board.holes[it] })
    }
}

/** What the round's total in the game says about the [Hole.SKIPPED] holes ([GameTotal.infer]). */
sealed interface TotalInference {
    /** One way the skipped holes fit: [fill] gives each its colour, by index. */
    data class Fill(val fill: Map<Int, Hole>) : TotalInference

    /**
     * More than one way, so the total can't tell: [combos] are the colour sets that fit (sorted,
     * fewest Rupees first), at most [GameTotal.MAX_COMBOS] of them, [more] when there are others.
     * [certain] are the skipped holes that are the same colour every way, which can be filled in.
     */
    data class Ambiguous(val combos: List<List<Hole>>, val more: Boolean, val certain: Map<Int, Hole> = emptyMap()) : TotalInference

    /** No colours for the skipped holes make [gameTotal] and fit the board. */
    data class NoMatch(val gameTotal: Int) : TotalInference
}

/**
 * Works out the [Hole.SKIPPED] holes' colours from the round's total in the game. The holes are
 * walked in dig order, as the game counts them ([RupeeTotal]), trying each colour for each skipped
 * hole and keeping the ways that end on the total. The five values are distinct, so above the
 * floor one skipped hole's colour is certain; a skipped hole dug at a low total and followed by a
 * Rupoor can be ambiguous (a Green or a Blue at 0 both end at 0 after it). The ways that fit are
 * then tried on the board, and only those it allows ([ThrillDiggerSolver]) are kept, since the
 * colours are clues for the holes around them.
 */
object GameTotal {
    const val MAX_COMBOS = 6

    /** Ways tried on the board at most; past that, they aren't narrowed by it. */
    private const val MAX_WAYS = 200

    /**
     * What [gameTotal] says about [board]'s skipped holes, of which there must be at least one, the
     * holes dug in [order] ([DiggerBoard.digOrder]; reading order when it isn't known).
     */
    fun infer(board: DiggerBoard, gameTotal: Int, order: List<Int> = emptyList()): TotalInference {
        val digs = RupeeTotal.counted(board, order)
        val skipped = digs.filter { board.holes[it] == Hole.SKIPPED }
        require(skipped.isNotEmpty()) { "no hole is skipped" }

        val ways = ways(digs.map { board.holes[it] }, gameTotal)
        if (ways.isEmpty()) return TotalInference.NoMatch(gameTotal)
        val checked = ways.size <= MAX_WAYS && ThrillDiggerSolver.isPossible(board)
        val fits = if (checked) ways.filter { ThrillDiggerSolver.isPossible(fill(board, skipped, it)) } else ways
        if (fits.isEmpty()) return TotalInference.NoMatch(gameTotal)
        if (fits.size == 1) return TotalInference.Fill(skipped.zip(fits.single()).toMap())

        val combos = fits.map { w -> w.sortedBy { Hole.RUPEES.indexOf(it) } }.distinct()
            .sortedWith(compareBy<List<Hole>> { c -> c.sumOf { it.rupees } }.thenComparator { a, b -> rank(a).compareTo(rank(b)) })
        val more = combos.size > MAX_COMBOS || ways.size > MAX_WAYS
        val certain = if (ways.size > MAX_WAYS) {
            emptyMap()
        } else {
            skipped.indices.filter { k -> fits.all { it[k] == fits[0][k] } }.associate { k -> skipped[k] to fits[0][k] }
        }
        return TotalInference.Ambiguous(combos.take(MAX_COMBOS), more, certain)
    }

    /** A colour set's place among those of its worth: Greens first, then Blues, and so on. */
    private fun rank(colours: List<Hole>): String = colours.joinToString("") { "${Hole.RUPEES.indexOf(it)}" }

    /**
     * The colours for the skipped holes in [digs] (in dig order) that make [gameTotal], each a list
     * by skipped hole, up to one more than [MAX_WAYS] of them.
     */
    internal fun ways(digs: List<Hole>, gameTotal: Int): List<List<Hole>> {
        // reachable[(step, total)]: whether the digs from step on can end at gameTotal from total.
        val reachable = HashMap<Long, Boolean>()
        fun reaches(step: Int, total: Int): Boolean {
            if (step == digs.size) return total == gameTotal
            val key = step.toLong() shl 32 or total.toLong()
            return reachable.getOrPut(key) {
                val hole = digs[step]
                if (hole == Hole.SKIPPED) {
                    Hole.RUPEES.any { reaches(step + 1, RupeeTotal.add(total, it.rupees)) }
                } else {
                    reaches(step + 1, RupeeTotal.add(total, hole.rupees))
                }
            }
        }
        val out = mutableListOf<List<Hole>>()
        fun go(step: Int, total: Int, chosen: List<Hole>) {
            if (out.size > MAX_WAYS) return
            if (step == digs.size) {
                out += chosen
                return
            }
            val hole = digs[step]
            for (h in if (hole == Hole.SKIPPED) Hole.RUPEES else listOf(hole)) {
                val next = RupeeTotal.add(total, h.rupees)
                if (reaches(step + 1, next)) go(step + 1, next, if (hole == Hole.SKIPPED) chosen + h else chosen)
            }
        }
        if (reaches(0, 0)) go(0, 0, emptyList())
        return out
    }

    private fun fill(board: DiggerBoard, holes: List<Int>, colours: List<Hole>): DiggerBoard =
        board.copy(holes = board.holes.toMutableList().also { h -> holes.zip(colours).forEach { (i, c) -> h[i] = c } })
}
