package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

/** A DataStore that counts its writes, to check that a bulk change is a single edit. */
class CountingDataStore(private val inner: DataStore<Preferences>) : DataStore<Preferences> {
    var writes = 0
        private set

    override val data: Flow<Preferences> get() = inner.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writes++
        return inner.updateData(transform)
    }
}
