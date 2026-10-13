package gx.zelda.shrines.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's shrine tracker: completed shrines and Spirit Orb purchases per save
 * file, plus viewing preferences shared across files. The active save file is the one the
 * game's other trackers use too.
 */
class ShrineStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_shrines@${file.id}", ShrineProgress.serializer(), ShrineProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_shrines_prefs", ShrinePrefs.serializer(), ShrinePrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<ShrinePrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<ShrineProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setCompleted(saveFileId: String, shrineId: String, completed: Boolean) =
        updateProgress(saveFileId) { it.copy(completed = if (completed) it.completed + shrineId else it.completed - shrineId) }

    /** Purchases are stored as entered, never below zero. */
    suspend fun setBought(saveFileId: String, hearts: Int, stamina: Int) =
        updateProgress(saveFileId) { it.copy(heartsBought = hearts.coerceAtLeast(0), staminaBought = stamina.coerceAtLeast(0)) }

    suspend fun updateProgress(saveFileId: String, transform: (ShrineProgress) -> ShrineProgress) =
        store(saveFileId).update(transform)

    suspend fun updatePrefs(transform: (ShrinePrefs) -> ShrinePrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<ShrineProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
