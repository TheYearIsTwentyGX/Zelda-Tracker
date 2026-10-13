package gx.zelda.botw.fairies.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Persistence for the BotW Great Fairy tracker: the opened fountains per save file. The active
 * save file is the one the armor tracker uses too.
 */
class FairyStorage(dataStore: DataStore<Preferences>, val game: Game = Games.BOTW) {
    private val unlockedStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "botw_fairies_unlocked@${file.id}", SetSerializer(String.serializer()), emptySet())
    }
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data

    fun unlocked(saveFileId: String): Flow<Set<String>> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setUnlocked(saveFileId: String, fairyId: String, unlocked: Boolean) =
        store(saveFileId).update { if (unlocked) it + fairyId else it - fairyId }

    private fun store(saveFileId: String): JsonStore<Set<String>> = unlockedStores.getValue(game.saveFile(saveFileId).id)
}
