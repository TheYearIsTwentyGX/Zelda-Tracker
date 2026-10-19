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

    /**
     * [completed] and [found] with every one of [ids] completed, or with them all cleared, as a
     * (completed, found) pair. A clear leaves each one found, as unticking a single row does.
     */
    fun setsWithAll(ids: Collection<String>, completed: Boolean): Pair<Set<String>, Set<String>> =
        if (completed) (this.completed + ids) to (found + ids) else (this.completed - ids) to (found + ids.filter { it in this.completed })
}

/**
 * What a bulk change to some [DiscoverySets] ids replaced: which of them were completed and which
 * found, so an undo puts back exactly that and leaves every other id as it is now.
 */
data class DiscoverySnapshot(val completed: SetSnapshot<String>, val found: SetSnapshot<String>) {
    /** [sets] with the snapshot's ids put back, as a (completed, found) pair. */
    fun restore(sets: DiscoverySets): Pair<Set<String>, Set<String>> = completed.restore(sets.completed) to found.restore(sets.found)

    companion object {
        fun of(sets: DiscoverySets, ids: Iterable<String>): DiscoverySnapshot =
            DiscoverySnapshot(SetSnapshot.of(sets.completed, ids), SetSnapshot.of(sets.found, ids))

        /** A snapshot of no ids: restoring it changes nothing. */
        val NONE = DiscoverySnapshot(SetSnapshot(emptySet(), emptySet()), SetSnapshot(emptySet(), emptySet()))
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

    fun withAll(ids: Collection<String>, completed: Boolean): Discoveries =
        setsWithAll(ids, completed).let { (completed, found) -> Discoveries(completed, found) }

    fun restored(snapshot: DiscoverySnapshot): Discoveries =
        snapshot.restore(this).let { (completed, found) -> Discoveries(completed, found) }
}
