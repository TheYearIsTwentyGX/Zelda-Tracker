package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Persists one serializable value per tracker under its own key in the app's DataStore.
 * Unreadable data (e.g. after an incompatible schema change) falls back to [default].
 */
class JsonStore<T>(
    private val dataStore: DataStore<Preferences>,
    key: String,
    private val serializer: KSerializer<T>,
    private val default: T,
) {
    private val prefKey = stringPreferencesKey(key)

    val data: Flow<T> = dataStore.data.map { prefs -> prefs[prefKey]?.let(::decode) ?: default }

    suspend fun update(transform: (T) -> T) {
        dataStore.edit { prefs ->
            val current = prefs[prefKey]?.let(::decode) ?: default
            prefs[prefKey] = json.encodeToString(serializer, transform(current))
        }
    }

    private fun decode(text: String): T? = runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
