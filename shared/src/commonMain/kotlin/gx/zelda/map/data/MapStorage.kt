package gx.zelda.map.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.map.model.MapPrefs
import kotlinx.coroutines.flow.Flow

/**
 * The map's own settings, shared across save files. Progress stays with each layer's tracker; the
 * active save file is the one every tracker for the game uses.
 */
class MapStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val prefsStore = JsonStore(dataStore, "${game.id}_map_prefs", MapPrefs.serializer(), MapPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<MapPrefs> = prefsStore.data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun updatePrefs(transform: (MapPrefs) -> MapPrefs) = prefsStore.update(transform)
}
