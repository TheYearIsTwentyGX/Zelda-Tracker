package gx.zelda.upgrades.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.MapSnapshot
import gx.zelda.core.activeSaveFileStore
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's item-upgrade tracker: one [UpgradeProgress] per save file (each copy's
 * state and the copy counts, together, so every change is one write), plus the viewing
 * preferences, shared by every file. The active save file is the game's, shared with its other
 * trackers. Keys are prefixed with the game id.
 */
class UpgradeStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_upgrades@${file.id}", UpgradeProgress.serializer(), UpgradeProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_upgrades_prefs", UpgradePrefs.serializer(), UpgradePrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<UpgradePrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<UpgradeProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun updatePrefs(transform: (UpgradePrefs) -> UpgradePrefs) = prefsStore.update(transform)

    suspend fun update(saveFileId: String, transform: (UpgradeProgress) -> UpgradeProgress) = store(saveFileId).update(transform)

    /**
     * Marks every one of [keys] (copy keys) collected, or not, in one write; levels stay as they
     * are, as when a single row is ticked. Returns what it replaced, for [restore].
     */
    suspend fun setAllOwned(saveFileId: String, keys: Collection<String>, owned: Boolean): MapSnapshot<String, PieceProgress> {
        val before = store(saveFileId).swap { p -> p.copy(items = p.items + keys.associateWith { p.of(it).copy(owned = owned) }) }
        return MapSnapshot.of(before.items, keys)
    }

    /** Puts back what [setAllOwned] replaced, in one write; everything else keeps its state. */
    suspend fun restore(saveFileId: String, snapshot: MapSnapshot<String, PieceProgress>) =
        store(saveFileId).update { it.copy(items = snapshot.restore(it.items)) }

    private fun store(saveFileId: String): JsonStore<UpgradeProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
