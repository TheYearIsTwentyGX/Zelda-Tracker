package gx.zelda.botw.armor.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import gx.zelda.botw.armor.model.ArmorPrefs
import gx.zelda.botw.armor.model.ArmorProgress
import gx.zelda.botw.armor.model.PieceProgress
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.core.JsonStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private typealias Pieces = Map<String, PieceProgress>

/**
 * Persistence for the BotW armor tracker: one piece map per save file, plus the active save
 * file and the viewing preferences, which are shared across files.
 */
class ArmorStorage(private val dataStore: DataStore<Preferences>, val game: Game = Games.BOTW) {
    private val piecesSerializer = MapSerializer(String.serializer(), PieceProgress.serializer())

    private val pieceStores = game.saveFiles.associate { file ->
        file.id to JsonStore<Pieces>(dataStore, "botw_armor_pieces@${file.id}", piecesSerializer, emptyMap())
    }
    private val prefsStore = JsonStore(dataStore, "botw_armor_prefs", ArmorPrefs.serializer(), ArmorPrefs())
    private val activeStore = JsonStore(dataStore, "botw_active_save_file", String.serializer(), game.saveFiles.first().id)

    /** Before save files existed everything lived under this key. */
    private val legacyStore = JsonStore(dataStore, "botw_armor_progress", ArmorProgress.serializer(), ArmorProgress())

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<ArmorPrefs> = prefsStore.data.map { it.normalized() }

    fun pieces(saveFileId: String): Flow<Pieces> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun updatePrefs(transform: (ArmorPrefs) -> ArmorPrefs) =
        prefsStore.update { transform(it.normalized()).normalized() }

    suspend fun updatePieces(saveFileId: String, transform: (Pieces) -> Pieces) = store(saveFileId).update(transform)

    /**
     * Moves data saved before save files existed into [LEGACY_SAVE_FILE] and makes that file
     * active. Runs as one transaction; does nothing once the legacy key is gone.
     */
    suspend fun migrateLegacy() {
        dataStore.edit { prefs ->
            if (!legacyStore.has(prefs)) return@edit
            val legacy = legacyStore.read(prefs)
            val target = store(LEGACY_SAVE_FILE)
            if (!target.has(prefs)) target.write(prefs, legacy.pieces)
            if (!prefsStore.has(prefs)) prefsStore.write(prefs, legacy.prefs.normalized())
            activeStore.write(prefs, LEGACY_SAVE_FILE)
            legacyStore.remove(prefs)
        }
    }

    private fun store(saveFileId: String): JsonStore<Pieces> = pieceStores.getValue(game.saveFile(saveFileId).id)

    companion object {
        /** Progress tracked before save files existed was the user's Master Mode run. */
        const val LEGACY_SAVE_FILE = "master"
    }
}
