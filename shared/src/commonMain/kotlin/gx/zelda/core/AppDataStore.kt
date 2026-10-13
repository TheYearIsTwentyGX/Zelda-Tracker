package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

const val DATA_STORE_FILE = "zeldagx.preferences_pb"

/** Each platform supplies the directory; [DATA_STORE_FILE] is created inside it. */
fun createDataStore(directory: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "$directory/$DATA_STORE_FILE".toPath() },
    )
