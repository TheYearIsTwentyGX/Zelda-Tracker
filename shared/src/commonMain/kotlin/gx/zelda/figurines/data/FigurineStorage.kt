package gx.zelda.figurines.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.figurines.model.FigurinePrefs
import gx.zelda.figurines.model.FigurineProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's figurine tracker: the collection, story checkpoint and Mysterious
 * Shells per save file, plus viewing preferences shared across files. The active save file is the
 * one the game's other trackers use too.
 */
class FigurineStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_figurines@${file.id}", FigurineProgress.serializer(), FigurineProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_figurines_prefs", FigurinePrefs.serializer(), FigurinePrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<FigurinePrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<FigurineProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setOwned(saveFileId: String, number: Int, owned: Boolean) =
        store(saveFileId).update { it.withOwned(number, owned) }

    suspend fun setRequirementMet(saveFileId: String, number: Int, met: Boolean) =
        store(saveFileId).update { it.withRequirementMet(number, met) }

    suspend fun setCheckpoint(saveFileId: String, checkpointId: String) =
        store(saveFileId).update { it.copy(checkpoint = checkpointId) }

    /** Kept between 0 and [FigurineProgress.MAX_SHELLS], as the game does. */
    suspend fun setShells(saveFileId: String, shells: Int) =
        store(saveFileId).update { it.copy(shells = shells.coerceIn(0, FigurineProgress.MAX_SHELLS)) }

    suspend fun updatePrefs(transform: (FigurinePrefs) -> FigurinePrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<FigurineProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
