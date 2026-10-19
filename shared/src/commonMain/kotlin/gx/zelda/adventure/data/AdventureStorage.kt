package gx.zelda.adventure.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.adventure.model.AdventureMapPrefs
import gx.zelda.adventure.model.AdventurePrefs
import gx.zelda.adventure.model.AdventureProgress
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.SquareStatus
import gx.zelda.adventure.model.checkIds
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.MapSnapshot
import gx.zelda.core.SetSnapshot
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow

/** What a mark-all or clear-all replaced on one map: the squares' statuses and their ticks. */
data class AdventureSnapshot(val status: MapSnapshot<String, Int>, val done: SetSnapshot<String>)

/**
 * Persistence for one Adventure Mode map of [game]: progress per save file under
 * `<game>_adventure_<map>@<save file>`, the map's own viewing choices under
 * `<game>_adventure_<map>_prefs`, and spoiler-safe mode for every map under `<game>_adventure_prefs`.
 * The active save file is the one the game's other trackers use.
 *
 * Every write sets or clears values explicitly, inside one edit, so two quick taps can't undo
 * each other.
 */
class AdventureStorage(dataStore: DataStore<Preferences>, val game: Game, val dataFile: String) {
    /** "hwde/adventure_map.json" → "adventure_map". */
    val stem: String = dataFile.substringAfterLast('/').removeSuffix(".json")

    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_adventure_$stem@${file.id}", AdventureProgress.serializer(), AdventureProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_adventure_prefs", AdventurePrefs.serializer(), AdventurePrefs())
    private val mapPrefsStore = JsonStore(dataStore, "${game.id}_adventure_${stem}_prefs", AdventureMapPrefs.serializer(), AdventureMapPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<AdventurePrefs> = prefsStore.data
    val mapPrefs: Flow<AdventureMapPrefs> = mapPrefsStore.data

    fun progress(saveFileId: String): Flow<AdventureProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setStatus(saveFileId: String, square: Square, status: SquareStatus) =
        store(saveFileId).update { it.withStatus(square, status) }

    /** Ticks or unticks one reward, Skulltula or search. */
    suspend fun setDone(saveFileId: String, id: String, done: Boolean) = store(saveFileId).update { it.withDone(id, done) }

    /** Sets whether the save file has [cardId], or with null lets the cleared squares decide again. */
    suspend fun setCard(saveFileId: String, cardId: String, owned: Boolean?) = store(saveFileId).update { it.withCard(cardId, owned) }

    /**
     * Makes every one of [squares] complete, or clears them all, in one write, and returns what
     * they had before for its undo.
     */
    suspend fun setAll(saveFileId: String, squares: List<Square>, complete: Boolean): AdventureSnapshot {
        val before = store(saveFileId).swap { it.withAll(squares, complete) }
        return AdventureSnapshot(
            status = MapSnapshot.of(before.status, squares.map { it.id }),
            done = SetSnapshot.of(before.done, squares.flatMap { it.checkIds }),
        )
    }

    /** Puts back what [setAll] replaced, in one write; other squares keep what they have now. */
    suspend fun restore(saveFileId: String, snapshot: AdventureSnapshot) = store(saveFileId).update {
        it.copy(status = snapshot.status.restore(it.status), done = snapshot.done.restore(it.done))
    }

    suspend fun updatePrefs(transform: (AdventurePrefs) -> AdventurePrefs) = prefsStore.update(transform)

    suspend fun updateMapPrefs(transform: (AdventureMapPrefs) -> AdventureMapPrefs) = mapPrefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<AdventureProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
