package gx.zelda.checklist.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for one checklist tracker of [game]: progress per save file, plus viewing
 * preferences shared across files. Keys are `<game>_checklist_<file stem>@<save file>`, so two
 * checklists of a game (or a checklist and a bespoke tracker) never share one. The active save
 * file is the one the game's other trackers use too.
 */
class ChecklistStorage(dataStore: DataStore<Preferences>, val game: Game, val dataFile: String) {
    /** "eow/heart_pieces.json" → "heart_pieces". */
    val stem: String = dataFile.substringAfterLast('/').removeSuffix(".json")

    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_checklist_$stem@${file.id}", ChecklistProgress.serializer(), ChecklistProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_checklist_${stem}_prefs", ChecklistPrefs.serializer(), ChecklistPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<ChecklistPrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<ChecklistProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setLevel(saveFileId: String, item: ChecklistItem, level: Int) =
        store(saveFileId).update { it.withLevel(item, level) }

    suspend fun setDone(saveFileId: String, item: ChecklistItem, done: Boolean) =
        store(saveFileId).update { it.withDone(item, done) }

    suspend fun updatePrefs(transform: (ChecklistPrefs) -> ChecklistPrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<ChecklistProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
