package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import gx.zelda.upgrades.data.SharedProgressMigration
import kotlinx.coroutines.flow.Flow
import okio.Path.Companion.toPath

const val DATA_STORE_FILE = "zeldagx.preferences_pb"

/**
 * Each platform supplies the directory; [DATA_STORE_FILE] is created inside it. The migrations run
 * once the store is first read, before anything sees its data.
 */
fun createDataStore(directory: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        migrations = listOf(SharedProgressMigration()),
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

    suspend fun setThemeMode(mode: ThemeMode) = store.update { it.copy(themeMode = mode) }

    suspend fun setGameThemes(on: Boolean) = store.update { it.copy(gameThemes = on) }

    suspend fun setStoryGating(on: Boolean) = store.update { it.copy(storyGating = on) }
}
