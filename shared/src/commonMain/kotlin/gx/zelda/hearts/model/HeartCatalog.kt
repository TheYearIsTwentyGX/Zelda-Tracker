package gx.zelda.hearts.model

import gx.zelda.core.groupByRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HeartKind(val label: String) {
    /** A quarter of a heart; four make a heart. */
    @SerialName("piece")
    PIECE("Piece of Heart"),

    /** A whole heart, usually from a boss. */
    @SerialName("container")
    CONTAINER("Heart Container"),
}

/** One Piece of Heart or Heart Container. Sources are in docs/mc-heart-pieces.md. */
@Serializable
data class HeartEntry(
    val id: String,
    val kind: HeartKind,
    /** A short label for the row, e.g. "Behind the waterfall". */
    val name: String,
    val region: String,
    /** The exact spot: a cave, a house, a minigame or a dungeon. */
    val area: String,
    /** How to get it, precise enough to find it. */
    val howTo: String,
    /** Only reachable after a Kinstone fusion; [fusionNote] says which, if known. */
    val kinstone: Boolean = false,
    val fusionNote: String? = null,
    /** Link has to be Minish-sized to reach it. */
    val minish: Boolean = false,
    /** Items it needs, as the game names them (e.g. "Mole Mitts"). */
    val items: List<String> = emptyList(),
    val note: String? = null,
) {
    /** What the row's tags and the search read: Kinstone, Minish and each item. */
    val requirements: List<String>
        get() = buildList {
            if (kinstone) add("Kinstone")
            if (minish) add("Minish")
            addAll(items)
        }
}

@Serializable
data class HeartCatalog(
    /** Hearts Link starts the game with. */
    val startingHearts: Int,
    /** Regions in display order. */
    val regions: List<String>,
    val entries: List<HeartEntry>,
) {
    val pieces: List<HeartEntry> get() = entries.filter { it.kind == HeartKind.PIECE }
    val containers: List<HeartEntry> get() = entries.filter { it.kind == HeartKind.CONTAINER }

    /** Hearts with everything collected. */
    val maxHearts: Int get() = startingHearts + containers.size + pieces.size / PIECES_PER_HEART

    /** Where a save file stands with its hearts. */
    fun total(progress: HeartProgress): HeartTotal = HeartTotal(
        startingHearts = startingHearts,
        pieces = pieces.count { it.id in progress.collected },
        containers = containers.count { it.id in progress.collected },
        piecesTotal = pieces.size,
        containersTotal = containers.size,
    )

    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<HeartEntry>): List<Pair<String, List<HeartEntry>>> = groupByRegion(regions, visible) { it.region }

    companion object {
        const val PIECES_PER_HEART = 4
    }
}

/** A save file's hearts: whole hearts so far, and the quarters toward the next. */
data class HeartTotal(
    val startingHearts: Int,
    val pieces: Int,
    val containers: Int,
    val piecesTotal: Int,
    val containersTotal: Int,
) {
    val hearts: Int get() = startingHearts + containers + pieces / HeartCatalog.PIECES_PER_HEART

    /** Pieces toward the next heart, 0–3. */
    val quarters: Int get() = pieces % HeartCatalog.PIECES_PER_HEART

    val maxHearts: Int get() = startingHearts + containersTotal + piecesTotal / HeartCatalog.PIECES_PER_HEART

    val collected: Int get() = pieces + containers

    val total: Int get() = piecesTotal + containersTotal

    /** "12 hearts", or "12 hearts + 2/4" with pieces toward the next. */
    val heartsLabel: String
        get() = "$hearts ${if (hearts == 1) "heart" else "hearts"}" + if (quarters > 0) " + $quarters/4" else ""
}
