package gx.zelda.sidequests.model

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
    val completed: Set<String> = emptySet(),
    /** Quests started, completed or not; read [state] rather than this. */
    val started: Set<String> = emptySet(),
) {
    fun state(questId: String): QuestState = DiscoveryState.of(questId, completed, started)

    fun isStarted(questId: String): Boolean = questId in started || questId in completed

    fun withState(questId: String, state: QuestState): SideQuestProgress = when (state) {
        DiscoveryState.UNDISCOVERED -> copy(completed = completed - questId, started = started - questId)
        DiscoveryState.FOUND -> copy(completed = completed - questId, started = started + questId)
        DiscoveryState.COMPLETED -> copy(completed = completed + questId, started = started + questId)
    }
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class SideQuestPrefs(
    /** Spoiler-safe mode: a quest not started shows only its region until revealed. */
    val hideUndiscovered: Boolean = false,
)
