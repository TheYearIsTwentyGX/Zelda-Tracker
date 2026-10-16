package gx.zelda.kinstones.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.kinstones.model.KinstonePrefs
import gx.zelda.kinstones.model.KinstoneProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's Kinstone fusion tracker: fusions done per save file, plus viewing
 * preferences shared across files. The active save file is the one the game's other trackers use
 * too.
 */
class KinstoneStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_kinstones@${file.id}", KinstoneProgress.serializer(), KinstoneProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_kinstones_prefs", KinstonePrefs.serializer(), KinstonePrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<KinstonePrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<KinstoneProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setFused(saveFileId: String, fusionId: String, done: Boolean) =
        store(saveFileId).update { it.withFused(fusionId, done) }

    suspend fun updatePrefs(transform: (KinstonePrefs) -> KinstonePrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<KinstoneProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
