package gx.zelda.kinstones.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.SetSnapshot
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

    /**
     * Marks every one of [fusionIds] done, or none, in one write; returns which of them were done
     * before, for its undo.
     */
    suspend fun setAllFused(saveFileId: String, fusionIds: Collection<String>, done: Boolean): SetSnapshot<String> {
        val before = store(saveFileId).swap { it.withAllFused(fusionIds, done) }
        return SetSnapshot.of(before.fused, fusionIds)
    }

    /** Puts back what [setAllFused] replaced, in one write; fusions it didn't touch stay as they are. */
    suspend fun restore(saveFileId: String, snapshot: SetSnapshot<String>) =
        store(saveFileId).update { it.copy(fused = snapshot.restore(it.fused)) }

    suspend fun updatePrefs(transform: (KinstonePrefs) -> KinstonePrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<KinstoneProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
