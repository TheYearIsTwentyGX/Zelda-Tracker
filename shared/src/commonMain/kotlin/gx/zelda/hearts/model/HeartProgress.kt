package gx.zelda.hearts.model

import kotlinx.serialization.Serializable

/** One save file's collected Pieces of Heart and Heart Containers, by entry id. */
@Serializable
data class HeartProgress(val collected: Set<String> = emptySet()) {
    fun isCollected(id: String): Boolean = id in collected

    fun with(id: String, collected: Boolean): HeartProgress =
        copy(collected = if (collected) this.collected + id else this.collected - id)

    /** Every one of [ids] collected, or none of them. */
    fun withAll(ids: Collection<String>, collected: Boolean): HeartProgress =
        copy(collected = if (collected) this.collected + ids else this.collected - ids.toSet())
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class HeartPrefs(
    /** Spoiler-safe mode: an uncollected entry's row shows only its region until revealed. */
    val hideUncollected: Boolean = false,
)
