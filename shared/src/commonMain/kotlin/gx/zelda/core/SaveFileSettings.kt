package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * What the user says about one save file, as opposed to what [SaveFile] fixes for every user (BotW's
 * Master Mode file). Kept per file, so it's user data like progress.
 */
@Serializable
data class SaveFileSettings(
    /**
     * A Hero Mode file. Skyward Sword locks the mode to the file: Hero Mode is a new file started from
     * a cleared one. Only Skyward Sword's Lightning Round reads it so far (Demise joins the boss
     * battles); other trackers could use it for their own Hero Mode differences.
     */
    val heroMode: Boolean = false,
)

/** Persists [SaveFileSettings] under `<game>_file_settings@<file>`, one key per save file. */
class SaveFileSettingsStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val stores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, key(game.id, file.id), SaveFileSettings.serializer(), SaveFileSettings())
    }

    fun settings(saveFileId: String): Flow<SaveFileSettings> = stores.getValue(game.saveFile(saveFileId).id).data

    /** Changes one file's settings in one write. */
    suspend fun update(saveFileId: String, transform: (SaveFileSettings) -> SaveFileSettings) =
        stores.getValue(game.saveFile(saveFileId).id).update(transform)

    companion object {
        fun key(gameId: String, saveFileId: String): String = "${gameId}_file_settings@$saveFileId"
    }
}
