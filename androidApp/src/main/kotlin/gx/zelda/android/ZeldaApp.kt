package gx.zelda.android

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.art.ArtPackStore
import gx.zelda.art.GameArt
import gx.zelda.core.createDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

class ZeldaApp : Application() {
    /** DataStore must be a process-wide singleton, so it lives here rather than in the activity. */
    val dataStore: DataStore<Preferences> by lazy { createDataStore(filesDir.absolutePath) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Art packs live in app-private storage, left out of backups (res/xml/backup_rules.xml).
        scope.launch { GameArt.attach(ArtPackStore("${filesDir.absolutePath}/artpacks".toPath())) }
    }
}
