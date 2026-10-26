package gx.zelda.bossrush.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.bossrush.model.BossRushRecords
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow

/**
 * The Lightning Round's times, one [BossRushRecords] per save file under `<game>_boss_rush@<file>`.
 * The active save file is the game's, shared with its other trackers.
 */
class BossRushStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val stores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, recordsKey(game.id, file.id), BossRushRecords.serializer(), BossRushRecords())
    }
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data

    fun records(saveFileId: String): Flow<BossRushRecords> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    /** Changes one file's records in one write, from what's stored, and returns what it replaced. */
    suspend fun swap(saveFileId: String, transform: (BossRushRecords) -> BossRushRecords): BossRushRecords =
        store(saveFileId).swap(transform)

    private fun store(saveFileId: String) = stores.getValue(game.saveFile(saveFileId).id)

    companion object {
        fun recordsKey(gameId: String, saveFileId: String): String = "${gameId}_boss_rush@$saveFileId"
    }
}
