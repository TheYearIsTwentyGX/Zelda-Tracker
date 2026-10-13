package gx.zelda.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.builtins.serializer

/**
 * The save file a game's trackers are showing. Every tracker for a game reads the same key,
 * so switching files in one switches it in all of them.
 */
fun activeSaveFileStore(dataStore: DataStore<Preferences>, game: Game): JsonStore<String> =
    JsonStore(dataStore, "${game.id}_active_save_file", String.serializer(), game.saveFiles.first().id)
