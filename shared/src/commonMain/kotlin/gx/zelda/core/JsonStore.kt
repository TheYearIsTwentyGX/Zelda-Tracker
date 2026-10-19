package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Persists one serializable value under its own key in the app's DataStore.
 * Unreadable data (e.g. after an incompatible schema change) falls back to [default].
 */
class JsonStore<T>(
    private val dataStore: DataStore<Preferences>,
    key: String,
    private val serializer: KSerializer<T>,
    private val default: T,
) {
    private val prefKey = stringPreferencesKey(key)

    val data: Flow<T> = dataStore.data.map(::read).distinctUntilChanged()

    suspend fun update(transform: (T) -> T) {
        dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    /**
     * Like [update], but returns the value it replaced, read inside the same edit: a bulk change
     * snapshots what it overwrites this way, so its undo puts back exactly that.
     */
    suspend fun swap(transform: (T) -> T): T {
        var before = default
        dataStore.edit { prefs -> before = read(prefs); write(prefs, transform(before)) }
        return before
    }

    /** For multi-key transactions inside a single [DataStore.edit]. */
    fun read(prefs: Preferences): T = prefs[prefKey]?.let(::decode) ?: default

    fun write(prefs: MutablePreferences, value: T) {
        prefs[prefKey] = json.encodeToString(serializer, value)
    }

    fun has(prefs: Preferences): Boolean = prefKey in prefs

    fun remove(prefs: MutablePreferences) {
        prefs.remove(prefKey)
    }

    private fun decode(text: String): T? = runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
