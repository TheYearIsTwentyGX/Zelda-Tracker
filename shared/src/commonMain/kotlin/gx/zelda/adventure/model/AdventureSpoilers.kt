package gx.zelda.adventure.model

/**
 * Spoiler-safe mode, which works like the game does before you've been everywhere:
 * - A square the save file hasn't reached is *Unexplored*: drawn dim, with nothing about it shown,
 *   until it's revealed for the session. Reached means [explored].
 * - On a reached square, the search spots, quiz answers and Skulltula locations stay hidden until
 *   the user uses the Compass on it (the item card that shows a square's secrets in the game).
 *   Searches already done are always shown.
 * With the mode off nothing is hidden.
 */
class AdventureSpoilers(
    private val map: AdventureMap,
    private val progress: AdventureProgress,
    val on: Boolean,
    /** Squares revealed for this session. */
    private val revealed: Set<String> = emptySet(),
    /** Squares the Compass has been used on this session. */
    private val compassed: Set<String> = emptySet(),
) {
    val explored: Set<String> by lazy { explored(map, progress) }

    /** True when [square] shows as Unexplored. */
    fun isHidden(square: Square): Boolean = on && square.id !in explored && square.id !in revealed

    /** True when [square]'s undone searches, quiz answers and Skulltula locations are kept back. */
    fun secretsHidden(square: Square): Boolean = on && (isHidden(square) || square.id !in compassed)

    /** The searches [square]'s sheet lists: all, or with secrets hidden only those already done. */
    fun visibleSearches(square: Square): List<Search> =
        if (secretsHidden(square)) square.searches.filter { progress.isDone(it.id) } else square.searches

    companion object {
        /**
         * The squares a save file has reached: the start, every cleared square, and each neighbour
         * of a cleared square across an open side. A side is open when neither square has a
         * blockade there, or when a path search (Hookshot, Raft…) has been done on the cleared
         * square: the data doesn't say which neighbour such a search opens, so all of them count.
         * A map with no start (the Rewards Map, opened square by square by Gold Skulltula
         * illustrations rather than by its neighbours) has every square reached.
         */
        fun explored(map: AdventureMap, progress: AdventureProgress): Set<String> = buildSet {
            if (map.start == null) return map.squares.mapTo(mutableSetOf()) { it.id }
            add(map.start)
            for (square in map.squares) {
                if (!progress.isCleared(square)) continue
                add(square.id)
                val opened = square.searches.any { s -> progress.isDone(s.id) && map.card(s.card)?.effects?.contains(CardEffect.PATH) == true }
                for (side in Side.entries) {
                    val next = map.neighbour(square, side) ?: continue
                    if (opened || map.isOpen(square, side)) add(next.id)
                }
            }
        }
    }
}

/** What a square on the map shows, decided away from the drawing code so it can be tested. */
data class SquareLook(
    val status: SquareStatus,
    val complete: Boolean,
    /** Drawn dim with no details: Unexplored in spoiler-safe mode. */
    val hidden: Boolean,
    /** Doesn't match the filters, so it's drawn dim. */
    val dimmed: Boolean,
    val selected: Boolean,
    val start: Boolean,
    val finalBoss: Boolean,
    /** What's still to do there, most important first; empty when hidden. */
    val markers: List<SquareMarker>,
) {
    companion object {
        fun of(
            map: AdventureMap,
            square: Square,
            progress: AdventureProgress,
            spoilers: AdventureSpoilers,
            matches: Boolean,
            selected: Boolean,
        ): SquareLook {
            val hidden = spoilers.isHidden(square)
            fun left(type: RewardType) = square.rewards.count { it.type == type && !progress.isDone(it.id) }
            val markers = if (hidden) emptyList() else listOfNotNull(
                SquareMarker(MarkerKind.HEART_CONTAINER, left(RewardType.HEART_CONTAINER)),
                SquareMarker(MarkerKind.HEART_PIECE, left(RewardType.HEART_PIECE)),
                SquareMarker(MarkerKind.SKULLTULA, square.skulltulas.count { !progress.isDone(it.id) }),
                // Undone searches are a secret in spoiler-safe mode until the Compass shows them.
                SquareMarker(MarkerKind.SEARCH, if (spoilers.secretsHidden(square)) 0 else square.searches.count { !progress.isDone(it.id) }),
            ).filter { it.count > 0 }
            return SquareLook(
                status = progress.status(square),
                complete = progress.isComplete(square),
                hidden = hidden,
                dimmed = !matches,
                selected = selected,
                start = square.id == map.start,
                finalBoss = square.finalBoss && !hidden,
                markers = markers,
            )
        }
    }
}

enum class MarkerKind { HEART_CONTAINER, HEART_PIECE, SKULLTULA, SEARCH }

data class SquareMarker(val kind: MarkerKind, val count: Int)
