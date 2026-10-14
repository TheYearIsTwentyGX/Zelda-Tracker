package gx.zelda.compendium.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Persistence for a game's Hyrule Compendium tracker: the photographed entries per save file.
 * The active save file is the one the game's other trackers use too.
 */
class CompendiumStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val photographedStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_compendium_photographed@${file.id}", SetSerializer(String.serializer()), emptySet())
    }
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data

    fun photographed(saveFileId: String): Flow<Set<String>> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setPhotographed(saveFileId: String, entryId: String, photographed: Boolean) =
        store(saveFileId).update { if (photographed) it + entryId else it - entryId }

    private fun store(saveFileId: String): JsonStore<Set<String>> = photographedStores.getValue(game.saveFile(saveFileId).id)
}
