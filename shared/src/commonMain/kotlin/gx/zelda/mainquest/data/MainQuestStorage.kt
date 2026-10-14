package gx.zelda.mainquest.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.mainquest.model.MainQuestProgress
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persistence for a game's main quest tracker: towers, Divine Beasts and memories per save file.
 * The active save file is the one the game's other trackers use too.
 */
class MainQuestStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_main_quest@${file.id}", MainQuestProgress.serializer(), MainQuestProgress())
    }
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data

    fun progress(saveFileId: String): Flow<MainQuestProgress> = store(saveFileId).data

    /** Ids of the Divine Beasts freed on [saveFileId]; the shrine tracker counts their Heart Containers. */
    fun divineBeastsFreed(saveFileId: String): Flow<Set<String>> =
        progress(saveFileId).map { it.divineBeasts }.distinctUntilChanged()

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setDone(saveFileId: String, section: MainQuestSection, id: String, done: Boolean) =
        store(saveFileId).update { it.with(section, id, done) }

    private fun store(saveFileId: String): JsonStore<MainQuestProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
