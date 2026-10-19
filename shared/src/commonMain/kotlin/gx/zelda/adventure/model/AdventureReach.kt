package gx.zelda.adventure.model

/** How the line between two neighbouring squares is drawn, as the game's map draws it. */
enum class EdgeStyle {
    /** A wavy wall: clearing one square doesn't open the other. */
    WALL,

    /** A straight line: clearing either square opens the other. */
    OPEN,

    /** A wall a path search (Hookshot, Raft…) done on one of the squares has opened: drawn straight. */
    OPENED,
}

/** The edge on [side] of [square], toward [neighbour]. */
data class MapEdge(val square: Square, val side: Side, val neighbour: Square, val style: EdgeStyle)

/**
 * Where a save file can go next on a map. In the game, clearing a square opens its neighbours,
 * except across a wavy wall; some neighbours also need the clear to be a B or an A rank (the arrows
 * around a square), but no source records those, so any clear counts here.
 */
object AdventureReach {
    /**
     * The edge on [side] of [square], or null where there's no square on the other side (the map's
     * border or a blank square). A wall recorded on either square counts for both: the data has no
     * one-way walls (tools/hwde/build_maps.py draws each on both squares). A done path search on
     * either square opens its walls; the data doesn't say which one it opens, so it opens them all.
     */
    fun edge(map: AdventureMap, progress: AdventureProgress, square: Square, side: Side): EdgeStyle? {
        val other = map.neighbour(square, side) ?: return null
        return when {
            map.isOpen(square, side) -> EdgeStyle.OPEN
            pathDone(map, progress, square) || pathDone(map, progress, other) -> EdgeStyle.OPENED
            else -> EdgeStyle.WALL
        }
    }

    /** Every edge between two squares, once each: each square's east and south ones. */
    fun edges(map: AdventureMap, progress: AdventureProgress): List<MapEdge> = map.squares.flatMap { square ->
        listOf(Side.EAST, Side.SOUTH).mapNotNull { side ->
            edge(map, progress, square, side)?.let { MapEdge(square, side, map.neighbour(square, side)!!, it) }
        }
    }

    /**
     * The squares a save file can challenge now:
     * - the start;
     * - every square already cleared;
     * - and each neighbour of a cleared square across an edge that isn't a wall.
     *
     * A map with no start (the Rewards Map, whose squares open as Gold Skulltula illustrations are
     * completed) has every square open.
     */
    fun challengeable(map: AdventureMap, progress: AdventureProgress): Set<String> {
        if (map.start == null) return map.squares.mapTo(mutableSetOf()) { it.id }
        return buildSet {
            add(map.start)
            for (square in map.squares) {
                if (!progress.isCleared(square)) continue
                add(square.id)
                for (side in Side.entries) {
                    val style = edge(map, progress, square, side) ?: continue
                    if (style != EdgeStyle.WALL) add(map.neighbour(square, side)!!.id)
                }
            }
        }
    }

    private fun pathDone(map: AdventureMap, progress: AdventureProgress, square: Square): Boolean =
        square.searches.any { s -> progress.isDone(s.id) && map.card(s.card)?.effects?.contains(CardEffect.PATH) == true }
}
