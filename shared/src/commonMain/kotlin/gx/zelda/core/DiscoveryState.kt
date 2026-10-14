package gx.zelda.core

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
