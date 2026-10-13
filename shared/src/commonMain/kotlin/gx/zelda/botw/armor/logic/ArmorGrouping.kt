package gx.zelda.botw.armor.logic

import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorGroupBy
import gx.zelda.botw.armor.model.ArmorOrder
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorSet

/** A run of pieces shown under one header. [title] is null for pieces shown without a header. */
data class ArmorGroup(
    val key: String,
    val title: String?,
    val set: ArmorSet?,
    val pieces: List<ArmorPiece>,
)

object ArmorGrouping {
    /**
     * Lays out [pieces] (already filtered) for the armor list.
     *
     * In-game order uses the game's two armor sorts: grouped by set, it follows the game's set
     * order; ungrouped, it follows the game's body-part order (head, body, legs).
     */
    fun group(catalog: ArmorCatalog, pieces: List<ArmorPiece>, order: ArmorOrder, groupBy: ArmorGroupBy): List<ArmorGroup> =
        when (groupBy) {
            ArmorGroupBy.SET -> when (order) {
                ArmorOrder.ALPHABETICAL -> setsAlphabetical(catalog, pieces)
                ArmorOrder.IN_GAME -> setsInGame(catalog, pieces)
            }
            ArmorGroupBy.NONE -> {
                val sorted = when (order) {
                    ArmorOrder.ALPHABETICAL -> pieces.sortedBy { it.name }
                    ArmorOrder.IN_GAME -> pieces.sortedWith(
                        compareBy<ArmorPiece> { it.slot.ordinal }
                            .thenBy(nullsLast()) { it.typeOrder }
                            .thenBy { it.name },
                    )
                }
                listOfNotNull(sorted.takeIf { it.isNotEmpty() }?.let { ArmorGroup("_all", null, null, it) })
            }
        }

    /** Sets A–Z, pieces in catalog order within each, standalone pieces last. */
    private fun setsAlphabetical(catalog: ArmorCatalog, pieces: List<ArmorPiece>): List<ArmorGroup> {
        val bySet = pieces.filter { it.setId != null }.groupBy { it.setId!! }
        val sets = catalog.sets.sortedBy { it.name }.mapNotNull { set ->
            bySet[set.id]?.let { ArmorGroup(set.id, set.name, set, it) }
        }
        val singles = pieces.filter { it.setId == null }.sortedBy { it.name }
        return sets + listOfNotNull(singles.takeIf { it.isNotEmpty() }?.let { ArmorGroup("_singles", "Other armor", null, it) })
    }

    /**
     * The game's set order. Each run of consecutive pieces from the same set becomes a group,
     * so standalone pieces keep whatever position the game gives them.
     */
    private fun setsInGame(catalog: ArmorCatalog, pieces: List<ArmorPiece>): List<ArmorGroup> {
        val ordered = pieces.sortedWith(compareBy<ArmorPiece, Int?>(nullsLast()) { it.setOrder }.thenBy { it.name })
        val groups = mutableListOf<ArmorGroup>()
        var run = mutableListOf<ArmorPiece>()
        fun flush() {
            if (run.isEmpty()) return
            val set = catalog.set(run.first().setId)
            groups += ArmorGroup("run_${groups.size}_${set?.id ?: "single"}", set?.name, set, run)
            run = mutableListOf()
        }
        for (piece in ordered) {
            if (run.isNotEmpty() && run.first().setId != piece.setId) flush()
            run += piece
        }
        flush()
        return groups
    }
}
