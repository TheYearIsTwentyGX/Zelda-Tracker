package gx.zelda.sidequests.model

import gx.zelda.core.DiscoverySets
import gx.zelda.core.DiscoverySnapshot
import gx.zelda.core.DiscoveryState
import kotlinx.serialization.Serializable

/**
 * Where a save file stands with one quest. It reuses the shrine and Korok states: not started is
 * [DiscoveryState.UNDISCOVERED] and in progress is [DiscoveryState.FOUND], so the spoiler-safe
 * mode hides quests not started the way it hides undiscovered shrines.
 */
typealias QuestState = DiscoveryState

/** "Not started", "In progress" or "Completed". */
val QuestState.questLabel: String
    get() = when (this) {
        DiscoveryState.UNDISCOVERED -> "Not started"
        DiscoveryState.FOUND -> "In progress"
        DiscoveryState.COMPLETED -> "Completed"
    }

/** One save file's quests. Completing a quest implies having started it. */
@Serializable
data class SideQuestProgress(
    override val completed: Set<String> = emptySet(),
    /** Quests started, completed or not; read [state] rather than this. */
    val started: Set<String> = emptySet(),
) : DiscoverySets {
    /** [started] under [DiscoverySets]' name; not stored, so saves keep the `started` key. */
    override val found: Set<String> get() = started

    fun isStarted(questId: String): Boolean = isFound(questId)

    fun withState(questId: String, state: QuestState): SideQuestProgress =
        setsWith(questId, state).let { (completed, started) -> copy(completed = completed, started = started) }

    /** Each of [questIds] moved to [state], as [withState] does one. */
    fun withStates(questIds: Collection<String>, state: QuestState): SideQuestProgress =
        questIds.fold(this) { progress, id -> progress.withState(id, state) }

    /** What [snapshot] replaced put back; every other quest stays as it is now. */
    fun restored(snapshot: DiscoverySnapshot): SideQuestProgress =
        snapshot.restore(this).let { (completed, started) -> copy(completed = completed, started = started) }
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class SideQuestPrefs(
    /** Spoiler-safe mode: a quest not started shows only its region until revealed. */
    val hideUndiscovered: Boolean = false,
)
