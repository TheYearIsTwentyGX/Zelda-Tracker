package gx.zelda.thrilldigger.model

import kotlinx.serialization.Serializable

/**
 * Tubert's three boards. Sizes and counts are the game's own (the hole minigame actor's
 * `placeBombs`/`placeRupoors`, as ported by lepelog's solver; docs/ss.md): [width] holes across,
 * [height] rows, and exactly [bombs] Bombs and [rupoors] Rupoors hidden in them. [cost] is
 * what a round costs to play.
 */
@Serializable
enum class Difficulty(val label: String, val width: Int, val height: Int, val bombs: Int, val rupoors: Int, val cost: Int) {
    BEGINNER("Beginner", 5, 4, 4, 0, 30),
    INTERMEDIATE("Intermediate", 6, 5, 4, 4, 50),
    EXPERT("Expert", 8, 5, 8, 8, 70),
    ;

    val cells: Int get() = width * height

    /** Bombs and Rupoors together: what a Rupee's colour counts. */
    val hazards: Int get() = bombs + rupoors
}

/**
 * What one hole holds, as far as the player knows. A dug Rupee's colour says how many of its eight
 * neighbours hold a hazard (a Bomb or a Rupoor), from [minHazards] to [maxHazards]. [SKIPPED] is a
 * hole dug without its colour being seen: a Rupee of some colour, so no hazard, but no clue either.
 */
enum class Hole(val code: Char, val label: String, val rupees: Int, val minHazards: Int, val maxHazards: Int) {
    UNKNOWN('?', "Unknown", 0, 0, 8),
    GREEN('G', "Green", 1, 0, 0),
    BLUE('B', "Blue", 5, 1, 2),
    RED('R', "Red", 20, 3, 4),
    SILVER('S', "Silver", 100, 5, 6),
    GOLD('Y', "Gold", 300, 7, 8),
    RUPOOR('P', "Rupoor", -10, 0, 8),
    BOMB('X', "Bomb", 0, 0, 8),
    SKIPPED('D', "Skipped", 0, 0, 8),
    ;

    val isRupee: Boolean get() = this in RUPEES

    /** Dug and not a hazard: a Rupee, its colour seen or [SKIPPED]. */
    val isSafe: Boolean get() = isRupee || this == SKIPPED
    val isHazard: Boolean get() = this == RUPOOR || this == BOMB

    /** "1–2" for a Rupee's hazard count, or null for anything else. */
    val hazardRange: String?
        get() = if (!isRupee) null else if (minHazards == maxHazards) "$minHazards" else "$minHazards–$maxHazards"

    companion object {
        /** The five Rupee colours, fewest hazards around first. */
        val RUPEES: List<Hole> = listOf(GREEN, BLUE, RED, SILVER, GOLD)

        /** The Rupee a hole shows with [hazards] hazards around it. */
        fun rupeeFor(hazards: Int): Hole = RUPEES.first { hazards <= it.maxHazards }

        fun of(code: Char): Hole = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** One board as entered: [holes] in reading order, row by row, `row * width + column`. */
data class DiggerBoard(val difficulty: Difficulty, val holes: List<Hole>) {
    init {
        require(holes.size == difficulty.cells) { "${difficulty.label} has ${difficulty.cells} holes, not ${holes.size}" }
    }

    fun row(index: Int): Int = index / difficulty.width
    fun column(index: Int): Int = index % difficulty.width

    fun with(index: Int, hole: Hole): DiggerBoard = copy(holes = holes.toMutableList().also { it[index] = hole })

    /** One character per hole ([Hole.code]), for saving. */
    fun encode(): String = holes.joinToString("") { it.code.toString() }

    val isEmpty: Boolean get() = holes.all { it == Hole.UNKNOWN }

    /** Holes still hiding a Rupee: the undug ones less the hazards not found yet. */
    val rupeesLeft: Int get() = holes.count { it == Hole.UNKNOWN } - (difficulty.hazards - holes.count { it.isHazard })

    /**
     * No Rupee left to dig, so every undug hole holds a hazard (a Bomb or a Rupoor, which isn't
     * known): the round is over and the board can be checked against the RNG ([RngSolver.dealing]).
     * Below zero, more hazards are needed than there are undug holes, an impossible board that the
     * check finds no state for.
     */
    val isFinished: Boolean get() = rupeesLeft <= 0

    /**
     * The dug holes in the order they were dug, worked out from [history], this board's earlier
     * versions oldest first (the undo history). A hole counts as dug when it last went from undug to
     * dug, so correcting its colour or filling in a skipped hole keeps its place, while clearing it
     * and digging it again moves it to the end. Holes dug before the history begins (a loaded board,
     * or one older than the history keeps) come first, and holes dug in the same change go in reading
     * order. The last one is the hole dug most recently.
     */
    fun digOrder(history: List<DiggerBoard>): List<Int> {
        val past = history.filter { it.difficulty == difficulty }
        fun dugAt(i: Int): Int {
            var k = past.size
            while (k > 0 && past[k - 1].holes[i] != Hole.UNKNOWN) k--
            return k
        }
        return holes.indices.filter { holes[it] != Hole.UNKNOWN }.sortedWith(compareBy({ dugAt(it) }, { it }))
    }

    companion object {
        fun empty(difficulty: Difficulty) = DiggerBoard(difficulty, List(difficulty.cells) { Hole.UNKNOWN })

        /** Reads [encode]'s text back; anything unreadable or the wrong length gives an empty board. */
        fun decode(difficulty: Difficulty, text: String?): DiggerBoard =
            if (text == null || text.length != difficulty.cells) empty(difficulty) else DiggerBoard(difficulty, text.map(Hole::of))
    }
}
