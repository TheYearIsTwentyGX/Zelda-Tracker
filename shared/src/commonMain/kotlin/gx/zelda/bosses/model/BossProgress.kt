package gx.zelda.bosses.model

import gx.zelda.core.DiscoveryState
import kotlinx.serialization.Serializable

/**
 * One save file's overworld bosses. [DiscoveryState.COMPLETED] means defeated at least once:
 * bosses come back after a Blood Moon, but the game (and Kilton's medals) count the first win.
 */
@Serializable
data class BossProgress(
    /** Spawns defeated at least once. */
    val defeated: Set<String> = emptySet(),
    /** Spawns found, defeated or not; a defeated one counts as found whether or not it's here. */
    val found: Set<String> = emptySet(),
) {
    fun state(spawnId: String): DiscoveryState = DiscoveryState.of(spawnId, defeated, found)

    fun isFound(spawnId: String): Boolean = spawnId in found || spawnId in defeated

    fun isDefeated(spawnId: String): Boolean = spawnId in defeated

    fun withState(spawnId: String, state: DiscoveryState): BossProgress = when (state) {
        DiscoveryState.UNDISCOVERED -> copy(defeated = defeated - spawnId, found = found - spawnId)
        DiscoveryState.FOUND -> copy(defeated = defeated - spawnId, found = found + spawnId)
        DiscoveryState.COMPLETED -> copy(defeated = defeated + spawnId, found = found + spawnId)
    }
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class BossPrefs(
    /** Spoiler-safe mode: an unfound boss's row shows only its region until revealed. */
    val hideUndiscovered: Boolean = false,
)

/** "Not found", "Found" or "Defeated": the tracker's words for a boss's [DiscoveryState]. */
val DiscoveryState.bossLabel: String
    get() = when (this) {
        DiscoveryState.UNDISCOVERED -> "Not found"
        DiscoveryState.FOUND -> "Found"
        DiscoveryState.COMPLETED -> "Defeated"
    }
