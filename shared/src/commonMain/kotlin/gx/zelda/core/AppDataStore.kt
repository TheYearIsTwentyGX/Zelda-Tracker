package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import okio.Path.Companion.toPath

const val DATA_STORE_FILE = "zeldagx.preferences_pb"

/** Each platform supplies the directory; [DATA_STORE_FILE] is created inside it. */
fun createDataStore(directory: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "$directory/$DATA_STORE_FILE".toPath() },
    )

/** [AppSettings], under one key so each change is written whole. */
class AppSettingsStorage(dataStore: DataStore<Preferences>, private val games: List<Game> = Games.all) {
    private val store = JsonStore(dataStore, "app_settings", AppSettings.serializer(), AppSettings())

    val settings: Flow<AppSettings> = store.data

    /** Hiding the last game shown is ignored. */
    suspend fun setShown(gameId: String, shown: Boolean) {
        val game = games.firstOrNull { it.id == gameId } ?: return
        store.update { it.withShown(game, shown, games) }
    }

    suspend fun setExpanded(gameId: String, expanded: Boolean) = store.update { it.withExpanded(gameId, expanded) }
}
