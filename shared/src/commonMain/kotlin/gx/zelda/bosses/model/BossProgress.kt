package gx.zelda.bosses.model

import gx.zelda.core.DiscoverySets
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
    override val found: Set<String> = emptySet(),
) : DiscoverySets {
    /** [defeated] under [DiscoverySets]' name; not stored, so saves keep the `defeated` key. */
    override val completed: Set<String> get() = defeated

    fun isDefeated(spawnId: String): Boolean = spawnId in defeated

    fun withState(spawnId: String, state: DiscoveryState): BossProgress =
        setsWith(spawnId, state).let { (defeated, found) -> copy(defeated = defeated, found = found) }

    /** Each of [spawnIds] moved to [state], as [withState] does one. */
    fun withStates(spawnIds: Collection<String>, state: DiscoveryState): BossProgress =
        spawnIds.fold(this) { progress, id -> progress.withState(id, state) }
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
