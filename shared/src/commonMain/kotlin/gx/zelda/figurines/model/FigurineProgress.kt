package gx.zelda.figurines.model

import kotlinx.serialization.Serializable

/** One save file's figurine collection and where it stands with Carlov. */
@Serializable
data class FigurineProgress(
    /** Figurine numbers won. */
    val owned: Set<Int> = emptySet(),
    /** Figurines whose side task (a Kinstone fusion, the Goron Cave…) is done. */
    val requirementsMet: Set<Int> = emptySet(),
    /** The furthest [FigurineCheckpoint] id reached; null means the first. */
    val checkpoint: String? = null,
    /** Mysterious Shells held. */
    val shells: Int = 0,
) {
    fun isOwned(number: Int): Boolean = number in owned

    fun withOwned(number: Int, owned: Boolean): FigurineProgress =
        copy(owned = if (owned) this.owned + number else this.owned - number)

    /** Every one of [numbers] owned, or none of them; the checkpoint, side tasks and shells stay. */
    fun withAllOwned(numbers: Collection<Int>, owned: Boolean): FigurineProgress =
        copy(owned = if (owned) this.owned + numbers else this.owned - numbers.toSet())

    fun withRequirementMet(number: Int, met: Boolean): FigurineProgress =
        copy(requirementsMet = if (met) requirementsMet + number else requirementsMet - number)

    companion object {
        /** The most Mysterious Shells Link can carry. */
        const val MAX_SHELLS = 999
    }
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class FigurinePrefs(
    /**
     * Spoiler-safe mode: a figurine not owned and not yet available shows only its number until
     * revealed, and checkpoints past the save file's read "Later in the story".
     */
    val hideUnavailable: Boolean = false,
)
