package gx.zelda.figurines.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A kind of figurine, by the colour of the egg it comes out of (Zelda Wiki, Figurine Gallery). */
@Serializable
data class FigurineCategory(
    val id: String,
    val name: String,
    /** The egg's colour: Blue, Red, Green or Gold. */
    val egg: String,
)

/**
 * A point in the story where Carlov adds figurines to his machine. The game checks these as it
 * opens the draw (zeldaret/tmc, `figurineDevice.c`); see docs/mc-figurines.md.
 */
@Serializable
data class FigurineCheckpoint(
    val id: String,
    val name: String,
    /** What the player does to reach it, e.g. "Clear the Cave of Flames (Fire Element)". */
    val detail: String,
    /** Carlov's "final series", which he only makes after the credits. */
    val finalSeries: Boolean = false,
)

@Serializable
data class Figurine(
    /** The gallery's own number, 1–136. */
    val number: Int,
    val name: String,
    /** A [FigurineCategory] id. */
    val category: String,
    /** The [FigurineCheckpoint] id from which Carlov can dispense it (once [requirement] is met, if any). */
    val checkpoint: String,
    /** The gallery's text for it, North American version. */
    val description: String,
    /** Who or what the figurine shows, e.g. "Pita and Wheaton". */
    val pictured: String = "",
    /** A side task that must be done too, e.g. a Kinstone fusion; null if the story alone unlocks it. */
    val requirement: String? = null,
) {
    /** "#007", as the gallery numbers it. */
    val label: String get() = "#" + number.toString().padStart(3, '0')
}

@Serializable
data class FigurineCatalog(
    val categories: List<FigurineCategory>,
    /** In story order. */
    val checkpoints: List<FigurineCheckpoint>,
    val figurines: List<Figurine>,
) {
    @Transient private val indexById = checkpoints.withIndex().associate { (i, c) -> c.id to i }

    fun category(id: String): FigurineCategory? = categories.firstOrNull { it.id == id }

    /** [id]'s position in the story; an unknown id sorts last. */
    fun checkpointIndex(id: String?): Int = indexById[id] ?: checkpoints.size

    /** The checkpoint a save file has reached; one that hasn't set it is at the first. */
    fun reached(progress: FigurineProgress): FigurineCheckpoint =
        checkpoints.firstOrNull { it.id == progress.checkpoint } ?: checkpoints.first()

    /** The figurines Carlov makes before the credits (130); the final series adds the rest. */
    val beforeFinalSeries: List<Figurine>
        get() = figurines.filter { f -> checkpoints.none { it.id == f.checkpoint && it.finalSeries } }

    /**
     * Whether Carlov's machine can give [figurine] to this save file: its checkpoint is reached and
     * its side task, if any, is done. An owned figurine always counts, whatever the settings say.
     */
    fun isAvailable(figurine: Figurine, progress: FigurineProgress): Boolean =
        figurine.number in progress.owned ||
            (checkpointIndex(figurine.checkpoint) <= checkpointIndex(reached(progress).id) &&
                (figurine.requirement == null || figurine.number in progress.requirementsMet))

    /** [visible] split by checkpoint, in story order; checkpoints with none are left out. */
    fun byCheckpoint(visible: List<Figurine>): List<Pair<FigurineCheckpoint, List<Figurine>>> {
        val grouped = visible.groupBy { it.checkpoint }
        return checkpoints.mapNotNull { c -> grouped[c.id]?.let { c to it } }
    }
}
