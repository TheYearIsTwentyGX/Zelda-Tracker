package gx.zelda.botw.armor.logic

import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorSet
import gx.zelda.botw.armor.model.ArmorSort
import gx.zelda.botw.armor.model.Slot

/** A run of pieces shown under one header. [title] is null for pieces that belong to no set. */
data class ArmorGroup(
    val key: String,
    val title: String?,
    val set: ArmorSet?,
    val pieces: List<ArmorPiece>,
)

object ArmorGrouping {
    /** Groups [pieces] (already filtered) for display in the armor list. */
    fun group(catalog: ArmorCatalog, pieces: List<ArmorPiece>, sort: ArmorSort): List<ArmorGroup> = when (sort) {
        ArmorSort.ALPHABETICAL -> alphabetical(catalog, pieces)
        ArmorSort.BODY_PART -> byBodyPart(pieces)
        ArmorSort.SET -> bySet(catalog, pieces)
    }

    /** Sets A–Z, pieces in catalog order within each, standalone pieces last. */
    private fun alphabetical(catalog: ArmorCatalog, pieces: List<ArmorPiece>): List<ArmorGroup> {
        val bySet = pieces.filter { it.setId != null }.groupBy { it.setId!! }
        val sets = catalog.sets.sortedBy { it.name }.mapNotNull { set ->
            bySet[set.id]?.let { ArmorGroup(set.id, set.name, set, it) }
        }
        val singles = pieces.filter { it.setId == null }.sortedBy { it.name }
        return sets + listOfNotNull(singles.takeIf { it.isNotEmpty() }?.let { ArmorGroup("_singles", "Other armor", null, it) })
    }

    /** In-game "by body part" order: one group per slot, pieces by the game's type sort key. */
    private fun byBodyPart(pieces: List<ArmorPiece>): List<ArmorGroup> =
        Slot.entries.mapNotNull { slot ->
            pieces.filter { it.slot == slot }
                .sortedWith(compareBy<ArmorPiece, Int?>(nullsLast()) { it.typeOrder }.thenBy { it.name })
                .takeIf { it.isNotEmpty() }
                ?.let { ArmorGroup("slot_${slot.name}", slot.label, null, it) }
        }

    /**
     * In-game "by set" order. Each run of consecutive pieces from the same set becomes a group,
     * so standalone pieces keep whatever position the game gives them.
     */
    private fun bySet(catalog: ArmorCatalog, pieces: List<ArmorPiece>): List<ArmorGroup> {
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
