package gx.zelda.bosses.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.bosses.model.BossPrefs
import gx.zelda.bosses.model.BossProgress
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's overworld boss tracker: found and defeated spawns per save file, plus
 * viewing preferences shared across files. The active save file is the one the game's other
 * trackers use too.
 */
class BossStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_bosses@${file.id}", BossProgress.serializer(), BossProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_bosses_prefs", BossPrefs.serializer(), BossPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<BossPrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<BossProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setState(saveFileId: String, spawnId: String, state: DiscoveryState) =
        store(saveFileId).update { it.withState(spawnId, state) }

    /** Unticking a defeated boss leaves it found. */
    suspend fun setDefeated(saveFileId: String, spawnId: String, defeated: Boolean) =
        setState(saveFileId, spawnId, if (defeated) DiscoveryState.COMPLETED else DiscoveryState.FOUND)

    suspend fun updatePrefs(transform: (BossPrefs) -> BossPrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<BossProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
