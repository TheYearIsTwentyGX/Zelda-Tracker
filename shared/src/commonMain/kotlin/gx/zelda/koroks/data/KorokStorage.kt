package gx.zelda.koroks.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.DiscoverySnapshot
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's Korok tracker: found and collected seeds and Hestu upgrades per save
 * file, plus viewing preferences shared across files. The active save file is the one the game's
 * other trackers use too.
 */
class KorokStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_koroks@${file.id}", KorokProgress.serializer(), KorokProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_koroks_prefs", KorokPrefs.serializer(), KorokPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<KorokPrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<KorokProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setState(saveFileId: String, korokId: String, state: DiscoveryState) =
        updateProgress(saveFileId) { it.withState(korokId, state) }

    /** Unticking a collected seed leaves its Korok found. */
    suspend fun setCompleted(saveFileId: String, korokId: String, completed: Boolean) =
        setState(saveFileId, korokId, if (completed) DiscoveryState.COMPLETED else DiscoveryState.FOUND)

    /**
     * Collects every one of [korokIds], or clears them back to found, in one write. Returns what
     * it replaced, for [restore].
     */
    suspend fun setAllCompleted(saveFileId: String, korokIds: Collection<String>, completed: Boolean): DiscoverySnapshot {
        val before = store(saveFileId).swap { it.withAll(korokIds, completed) }
        return DiscoverySnapshot.of(before, korokIds)
    }

    /** Puts back what [setAllCompleted] replaced, in one write; other Koroks keep their state. */
    suspend fun restore(saveFileId: String, snapshot: DiscoverySnapshot) = updateProgress(saveFileId) { it.restored(snapshot) }

    suspend fun setBought(saveFileId: String, pouchId: String, upgrades: Int) =
        updateProgress(saveFileId) { it.withBought(pouchId, upgrades) }

    suspend fun updateProgress(saveFileId: String, transform: (KorokProgress) -> KorokProgress) =
        store(saveFileId).update(transform)

    suspend fun updatePrefs(transform: (KorokPrefs) -> KorokPrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<KorokProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
