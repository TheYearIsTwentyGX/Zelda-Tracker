package gx.zelda.adventure.model

import kotlinx.serialization.Serializable

/** How far a square's battle has gone; A-ranked includes cleared, so the two can't disagree. */
enum class SquareStatus(val level: Int, val label: String) {
    NONE(0, "Not cleared"),
    CLEARED(1, "Cleared"),
    A_RANK(2, "A rank");

    companion object {
        fun of(level: Int?): SquareStatus = entries.firstOrNull { it.level == level } ?: NONE
    }
}

/**
 * One save file's progress on one map. Reward, Skulltula and search ticks are separate from the
 * battle's status: most rewards need the pictured warrior, so clearing or A-ranking a square
 * doesn't mean its rewards were earned.
 */
@Serializable
data class AdventureProgress(
    /** Square id → [SquareStatus.level]; absent for not cleared. */
    val status: Map<String, Int> = emptyMap(),
    /** Reward, Skulltula and search ids that are done. */
    val done: Set<String> = emptySet(),
    /** Item cards the user has set by hand, overriding [AdventureInventory]'s guess. */
    val cards: Map<String, Boolean> = emptyMap(),
) {
    fun status(square: Square): SquareStatus = SquareStatus.of(status[square.id])

    fun isCleared(square: Square): Boolean = status(square) != SquareStatus.NONE

    fun isARanked(square: Square): Boolean = status(square) == SquareStatus.A_RANK

    fun isDone(id: String): Boolean = id in done

    fun withStatus(square: Square, value: SquareStatus): AdventureProgress =
        copy(status = if (value == SquareStatus.NONE) status - square.id else status + (square.id to value.level))

    fun withDone(id: String, value: Boolean): AdventureProgress = copy(done = if (value) done + id else done - id)

    /** [cardId] set by hand to [owned]; null goes back to the automatic guess. */
    fun withCard(cardId: String, owned: Boolean?): AdventureProgress =
        copy(cards = if (owned == null) cards - cardId else cards + (cardId to owned))

    /** Every one of [squares] complete (A-ranked, with everything ticked), or all cleared back to nothing. */
    fun withAll(squares: List<Square>, complete: Boolean): AdventureProgress {
        val ids = squares.flatMap { it.checkIds }
        return copy(
            status = if (complete) status + squares.associate { it.id to SquareStatus.A_RANK.level } else status - squares.map { it.id }.toSet(),
            done = if (complete) done + ids else done - ids.toSet(),
        )
    }

    /** True when [square]'s battle is A-ranked and each of its rewards, Skulltulas and searches is done. */
    fun isComplete(square: Square): Boolean = isARanked(square) && square.checkIds.all { it in done }

    /** True when anything on [square] is done. */
    fun isStarted(square: Square): Boolean = isCleared(square) || square.checkIds.any { it in done }

    /** Things done on [square], out of [Square.checkCount]: the two battle steps, then each tick. */
    fun doneCount(square: Square): Int = status(square).level + square.checkIds.count { it in done }
}

/** What [AdventureProgress.withAll] and the counts tick for a square: its rewards, Skulltulas and searches. */
val Square.checkIds: List<String> get() = rewards.map { it.id } + skulltulas.map { it.id } + searches.map { it.id }

/** Everything there is to do on the square: clear it, A-rank it, and each tick. */
val Square.checkCount: Int get() = 2 + checkIds.size

/**
 * Which item cards a save file has: a card counts as found once a square that gives it is cleared
 * (DE's Item Card Shop sells any card found before), unless the user set it by hand.
 */
class AdventureInventory(private val map: AdventureMap, private val progress: AdventureProgress) {
    fun isAutomatic(cardId: String): Boolean = cardId !in progress.cards

    fun has(cardId: String): Boolean = progress.cards[cardId]
        ?: map.card(cardId)?.anyOf?.takeIf { it.isNotEmpty() }?.any(::has)
        ?: map.sourcesOf(cardId).any(progress::isCleared)

    /** True when [search] isn't done yet and its card is in hand. */
    fun canDo(search: Search): Boolean = !progress.isDone(search.id) && has(search.card)
}

/** Totals of one kind over a set of squares, e.g. Heart Containers 50 of 111. */
data class Tally(val done: Int, val total: Int) {
    val left: Int get() = total - done
}

/** A map's numbers on one save file, for the summary and the overview. */
class AdventureTotals(map: AdventureMap, private val progress: AdventureProgress, squares: List<Square> = map.squares) {
    val cleared = Tally(squares.count(progress::isCleared), squares.size)
    val aRanked = Tally(squares.count(progress::isARanked), squares.size)
    val complete = Tally(squares.count(progress::isComplete), squares.size)

    /** Everything to do, the bar's numbers: two battle steps per square plus each tick. */
    val all = Tally(squares.sumOf(progress::doneCount), squares.sumOf { it.checkCount })

    private val rewards = squares.flatMap { it.rewards }

    fun rewards(type: RewardType): Tally = rewards.filter { it.type == type }.let { r -> Tally(r.count { progress.isDone(it.id) }, r.size) }

    val skulltulas: Tally = squares.flatMap { it.skulltulas }.let { g -> Tally(g.count { progress.isDone(it.id) }, g.size) }
    val searches: Tally = squares.flatMap { it.searches }.let { s -> Tally(s.count { progress.isDone(it.id) }, s.size) }
}

/** Viewing choices for every HW:DE map, the same on each save file. */
@Serializable
data class AdventurePrefs(
    /** Spoiler-safe mode: see [AdventureSpoilers]. */
    val spoilerSafe: Boolean = false,
)

/** One map's own viewing choices, kept when the tracker closes. */
@Serializable
data class AdventureMapPrefs(
    val tab: AdventureTab = AdventureTab.MAP,
    val filters: Set<SquareFilter> = emptySet(),
    /** Only squares with something left for this warrior; null for anyone. */
    val warrior: String? = null,
)

@Serializable
enum class AdventureTab(val label: String) { MAP("Map"), LIST("List") }

/** The filter chips. Squares must match every one that's on. */
@Serializable
enum class SquareFilter(val label: String) {
    NOT_CLEARED("Not cleared"),
    NOT_A_RANKED("Not A-ranked"),
    HEART_CONTAINERS("Heart Containers left"),
    HEART_PIECES("Pieces of Heart left"),
    SKULLTULAS("Skulltulas left"),
    SEARCHES("Searches left"),
    SEARCHABLE("Searchable now"),
    CHALLENGEABLE("Challengeable now"),
}
