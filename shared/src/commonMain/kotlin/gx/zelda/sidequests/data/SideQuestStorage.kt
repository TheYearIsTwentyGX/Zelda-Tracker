package gx.zelda.sidequests.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.DiscoverySnapshot
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.sidequests.model.QuestState
import gx.zelda.sidequests.model.SideQuestPrefs
import gx.zelda.sidequests.model.SideQuestProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's side quest tracker: quests started and completed per save file, plus
 * viewing preferences shared across files. The active save file is the one the game's other
 * trackers use too.
 */
class SideQuestStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_side_quests@${file.id}", SideQuestProgress.serializer(), SideQuestProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_side_quests_prefs", SideQuestPrefs.serializer(), SideQuestPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<SideQuestPrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<SideQuestProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setState(saveFileId: String, questId: String, state: QuestState) =
        store(saveFileId).update { it.withState(questId, state) }

    /**
     * Moves every one of [questIds] to [state] in one write, so a "mark all" lands whole or not at
     * all. Returns which of them were completed and which started before, for its undo.
     */
    suspend fun setAllStates(
        saveFileId: String,
        questIds: Collection<String>,
        state: QuestState,
    ): DiscoverySnapshot {
        val before = store(saveFileId).swap { it.withStates(questIds, state) }
        return DiscoverySnapshot.of(before, questIds)
    }

    /** Puts back what [setAllStates] replaced, in one write; quests it didn't touch stay as they are. */
    suspend fun restore(saveFileId: String, snapshot: DiscoverySnapshot) =
        store(saveFileId).update { it.restored(snapshot) }

    /** Unticking a completed quest leaves it in progress. */
    suspend fun setCompleted(saveFileId: String, questId: String, completed: Boolean) =
        setState(saveFileId, questId, if (completed) QuestState.COMPLETED else QuestState.FOUND)

    suspend fun updatePrefs(transform: (SideQuestPrefs) -> SideQuestPrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<SideQuestProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
