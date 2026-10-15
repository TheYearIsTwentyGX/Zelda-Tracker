package gx.zelda.core

import kotlinx.serialization.Serializable

/**
 * Where a save file stands with one thing a tracker lists (a shrine, a Korok). Completing it
 * implies having found it.
 */
enum class DiscoveryState(val label: String) {
    UNDISCOVERED("Undiscovered"),
    FOUND("Found"),
    COMPLETED("Completed"),
    ;

    companion object {
        /** The state of [id] given the sets a progress record keeps. */
        fun of(id: String, completed: Set<String>, found: Set<String>): DiscoveryState = when (id) {
            in completed -> COMPLETED
            in found -> FOUND
            else -> UNDISCOVERED
        }
    }
}

/**
 * The two sets a save file keeps for one kind of discoverable thing: which are completed and which
 * are found. Older saves may have an id in [completed] only; it still counts as found.
 */
interface DiscoverySets {
    val completed: Set<String>
    val found: Set<String>

    fun state(id: String): DiscoveryState = DiscoveryState.of(id, completed, found)

    fun isFound(id: String): Boolean = id in found || id in completed

    /** [completed] and [found] with [id] moved to [state], as a (completed, found) pair. */
    fun setsWith(id: String, state: DiscoveryState): Pair<Set<String>, Set<String>> = when (state) {
        DiscoveryState.UNDISCOVERED -> (completed - id) to (found - id)
        DiscoveryState.FOUND -> (completed - id) to (found + id)
        DiscoveryState.COMPLETED -> (completed + id) to (found + id)
    }
}

/** A [DiscoverySets] on its own, for progress records that track more than one kind of thing. */
@Serializable
data class Discoveries(
    override val completed: Set<String> = emptySet(),
    override val found: Set<String> = emptySet(),
) : DiscoverySets {
    fun withState(id: String, state: DiscoveryState): Discoveries =
        setsWith(id, state).let { (completed, found) -> Discoveries(completed, found) }
}
