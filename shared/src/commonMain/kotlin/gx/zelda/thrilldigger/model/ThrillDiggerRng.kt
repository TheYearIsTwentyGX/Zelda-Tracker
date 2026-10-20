package gx.zelda.thrilldigger.model

/**
 * Skyward Sword's random number generator, as lepelog's Thrill Digger solver describes it (its
 * README and `rng.rs`; re-implemented here, docs/ss.md). The state is 32 bits, held in a Long.
 * Each step multiplies by 1664525, adds 1013904223, and folds the 64-bit result's high half into
 * its low half. The fold makes the map many-to-one, so from any start the state soon falls into
 * one of a few loops ([RngLoop]) and never leaves it.
 */
object SsRng {
    private const val MASK = 0xFFFFFFFFL

    fun next(state: Long): Long {
        // state < 2³², so the product is under 2⁵³ and fits.
        val t = state * 1664525L + 1013904223L
        return ((t ushr 32) + t) and MASK
    }

    /** The float in [0, 1) the game makes from a state: its top 23 bits as the mantissa. */
    fun float(state: Long): Float = Float.fromBits(((state ushr 9) or 0x3F800000L).toInt()) - 1f
}

/**
 * One loop the RNG settles into: [anchor] is its smallest state and [length] its number of states.
 * [share] is how often a random start ends up in it (its basin), so a player whose RNG has run for
 * a while is in it that often.
 */
data class RngLoop(val anchor: Long, val length: Int, val share: Double)

/**
 * The game's boards, packed into a Long each: bit i (i < 40) is set when hole i holds a hazard, and
 * bit 40 + k is set when the k-th hazard in reading order is a Bomb rather than a Rupoor.
 */
object PackedBoard {
    const val CELLS = 40

    fun hazards(packed: Long): Long = packed and ((1L shl CELLS) - 1)

    fun bombs(packed: Long): Long {
        var h = hazards(packed)
        var out = 0L
        var k = 0
        while (h != 0L) {
            val bit = h and -h
            if ((packed ushr (CELLS + k)) and 1L == 1L) out = out or bit
            h = h xor bit
            k++
        }
        return out
    }

    fun pack(bombs: Long, rupoors: Long): Long {
        var h = bombs or rupoors
        var out = h
        var k = 0
        while (h != 0L) {
            val bit = h and -h
            if (bombs and bit != 0L) out = out or (1L shl (CELLS + k))
            h = h xor bit
            k++
        }
        return out
    }

    /** The board as [DiggerBoard] holes, each Rupee the colour its neighbours give it. */
    fun unpack(difficulty: Difficulty, packed: Long): DiggerBoard {
        val hazards = hazards(packed)
        val bombs = bombs(packed)
        val around = Neighbours.of(difficulty)
        return DiggerBoard(difficulty, List(difficulty.cells) { i ->
            when {
                bombs ushr i and 1L == 1L -> Hole.BOMB
                hazards ushr i and 1L == 1L -> Hole.RUPOOR
                else -> Hole.rupeeFor((hazards and around[i]).countOneBits())
            }
        })
    }
}

/** Each hole's neighbours as a bit mask, per difficulty. */
internal object Neighbours {
    private val masks: Map<Difficulty, LongArray> = Difficulty.entries.associateWith { d ->
        LongArray(d.cells) { i ->
            val r = i / d.width
            val c = i % d.width
            var m = 0L
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr
                val cc = c + dc
                if ((dr != 0 || dc != 0) && rr in 0 until d.height && cc in 0 until d.width) m = m or (1L shl (rr * d.width + cc))
            }
            m
        }
    }

    fun of(d: Difficulty): LongArray = masks.getValue(d)
}

/**
 * How the game deals a board from an RNG state (its hole minigame actor's `placeBombs`,
 * `placeRupoors` and re-deal, docs/ss.md). It drops each Bomb, then each Rupoor, into hole
 * ⌊float × holes⌋, drawing again when that hole is taken. If any 2×2 square is all hazards, it
 * deals again from where the RNG is, up to ten times, keeping the eleventh deal.
 */
object ThrillDiggerDeal {
    fun deal(d: Difficulty, start: Long): Long {
        var state = start
        val cells = d.cells.toFloat()
        var bombs = 0L
        var rupoors = 0L
        for (attempt in 0..10) {
            bombs = 0L
            rupoors = 0L
            var placed = 0
            while (placed < d.bombs) {
                state = SsRng.next(state)
                val bit = 1L shl (SsRng.float(state) * cells).toInt()
                if ((bombs or rupoors) and bit == 0L) {
                    bombs = bombs or bit
                    placed++
                }
            }
            placed = 0
            while (placed < d.rupoors) {
                state = SsRng.next(state)
                val bit = 1L shl (SsRng.float(state) * cells).toInt()
                if ((bombs or rupoors) and bit == 0L) {
                    rupoors = rupoors or bit
                    placed++
                }
            }
            if (!hasBlock(d, bombs or rupoors)) break
        }
        return PackedBoard.pack(bombs, rupoors)
    }

    fun hasBlock(d: Difficulty, hazards: Long): Boolean {
        // A hazard whose right, lower and lower-right neighbours are hazards too, away from the edges.
        val w = d.width
        var corners = hazards and (hazards ushr 1) and (hazards ushr w) and (hazards ushr (w + 1))
        // Drop corners in the last column (their "right" wrapped to the next row) and the last row.
        for (r in 0 until d.height) corners = corners and (1L shl (r * w + w - 1)).inv()
        corners = corners and ((1L shl ((d.height - 1) * w)) - 1)
        return corners != 0L
    }
}
