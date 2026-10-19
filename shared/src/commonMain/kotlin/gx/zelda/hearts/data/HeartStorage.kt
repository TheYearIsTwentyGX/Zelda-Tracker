package gx.zelda.hearts.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.SetSnapshot
import gx.zelda.core.activeSaveFileStore
import gx.zelda.hearts.model.HeartPrefs
import gx.zelda.hearts.model.HeartProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's heart tracker: collected Pieces of Heart and Heart Containers per save
 * file, plus viewing preferences shared across files. The active save file is the one the game's
 * other trackers use too.
 */
class HeartStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_heart_pieces@${file.id}", HeartProgress.serializer(), HeartProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_heart_pieces_prefs", HeartPrefs.serializer(), HeartPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<HeartPrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<HeartProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setCollected(saveFileId: String, entryId: String, collected: Boolean) =
        store(saveFileId).update { it.with(entryId, collected) }

    /**
     * Collects every one of [entryIds], or none, in one write; returns which of them were
     * collected before, for its undo.
     */
    suspend fun setAllCollected(saveFileId: String, entryIds: Collection<String>, collected: Boolean): SetSnapshot<String> {
        val before = store(saveFileId).swap { it.withAll(entryIds, collected) }
        return SetSnapshot.of(before.collected, entryIds)
    }

    /** Puts back what [setAllCollected] replaced, in one write; entries it didn't touch stay as they are. */
    suspend fun restore(saveFileId: String, snapshot: SetSnapshot<String>) =
        store(saveFileId).update { it.copy(collected = snapshot.restore(it.collected)) }

    suspend fun updatePrefs(transform: (HeartPrefs) -> HeartPrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<HeartProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
