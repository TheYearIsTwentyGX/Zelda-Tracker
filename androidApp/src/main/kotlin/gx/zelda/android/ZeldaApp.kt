package gx.zelda.android

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.createDataStore

class ZeldaApp : Application() {
    /** DataStore must be a process-wide singleton, so it lives here rather than in the activity. */
    val dataStore: DataStore<Preferences> by lazy { createDataStore(filesDir.absolutePath) }
}
