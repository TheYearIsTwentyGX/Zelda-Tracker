package gx.zelda.checklist.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.LevelRef
import gx.zelda.checklist.model.storedAt
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.MapSnapshot
import gx.zelda.core.TrackerKind
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persistence for one checklist tracker of [game]: progress per save file, plus viewing
 * preferences shared across files. Keys are `<game>_checklist_<file stem>@<save file>`, so two
 * checklists of a game (or a checklist and a bespoke tracker) never share one. The active save
 * file is the one the game's other trackers use too.
 *
 * **Linked items.** Progress is read with the game's other checklists' levels on the same save
 * file ([ChecklistProgress.linked]), so an item with a `sameAs` link shows the level of the item it
 * names. A change is written back to whichever files it touched in one DataStore edit, so ticking a
 * linked item is still one write, and so is a mark-all across links.
 */
class ChecklistStorage(private val dataStore: DataStore<Preferences>, val game: Game, val dataFile: String) {
    /** "eow/heart_pieces.json" → "heart_pieces". */
    val stem: String = stemOf(dataFile)

    private val progressStores = game.saveFiles.associate { file -> file.id to progressStore(dataFile, file.id) }

    /** Per save file, the game's other checklists' progress, by stem. */
    private val linkedStores = game.saveFiles.associate { file ->
        file.id to checklistFiles(game).filter { it != dataFile }.associate { stemOf(it) to progressStore(it, file.id) }
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_checklist_${stem}_prefs", ChecklistPrefs.serializer(), ChecklistPrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<ChecklistPrefs> = prefsStore.data

    /** [saveFileId]'s progress, with the linked files'. It's read again only when one of the game's checklists changes. */
    fun progress(saveFileId: String): Flow<ChecklistProgress> {
        val file = game.saveFile(saveFileId).id
        val stores = listOf(progressStores.getValue(file)) + linkedStores.getValue(file).values
        return dataStore.data
            .map { prefs -> prefs to stores.map { it.raw(prefs) } }
            .distinctUntilChanged { a, b -> a.second == b.second }
            .map { (prefs, _) -> read(file, prefs) }
            .distinctUntilChanged()
    }

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setLevel(saveFileId: String, item: ChecklistItem, level: Int) {
        swap(saveFileId) { it.withLevel(item, level) }
    }

    suspend fun setDone(saveFileId: String, item: ChecklistItem, done: Boolean) {
        swap(saveFileId) { it.withDone(item, done) }
    }

    /**
     * Sets each item's level in one write, so a "mark all" lands whole or not at all, and returns
     * what it replaced where those items are stored (as stored, absent for 0; a linked item's is the
     * item it names) for its undo.
     */
    suspend fun setLevels(saveFileId: String, levels: Map<ChecklistItem, Int>): MapSnapshot<LevelRef, Int> {
        val before = swap(saveFileId) { it.withLevels(levels) }
        return MapSnapshot(levels.keys.map { it.storedAt }.distinct().associateWith(before::raw))
    }

    /** Puts back what [setLevels] replaced, in one write; levels it didn't touch stay as they are. */
    suspend fun restore(saveFileId: String, snapshot: MapSnapshot<LevelRef, Int>) {
        swap(saveFileId) { progress -> snapshot.before.entries.fold(progress) { p, (ref, level) -> p.withRaw(ref, level) } }
    }

    suspend fun updatePrefs(transform: (ChecklistPrefs) -> ChecklistPrefs) = prefsStore.update(transform)

    /** Applies [transform] to [saveFileId]'s progress in one edit, returning the progress it replaced. */
    private suspend fun swap(saveFileId: String, transform: (ChecklistProgress) -> ChecklistProgress): ChecklistProgress {
        val file = game.saveFile(saveFileId).id
        var before = ChecklistProgress()
        dataStore.edit { prefs ->
            before = read(file, prefs)
            write(file, prefs, before, transform(before))
        }
        return before
    }

    private fun read(file: String, prefs: Preferences): ChecklistProgress = ChecklistProgress(
        levels = progressStores.getValue(file).read(prefs).levels,
        linked = linkedStores.getValue(file).mapValues { it.value.read(prefs).levels },
    )

    /** Writes back only the files whose levels changed, so an untouched checklist's key is never made. */
    private fun write(file: String, prefs: MutablePreferences, before: ChecklistProgress, after: ChecklistProgress) {
        if (after.levels != before.levels) progressStores.getValue(file).write(prefs, ChecklistProgress(after.levels))
        for ((stem, store) in linkedStores.getValue(file)) {
            val levels = after.linked[stem].orEmpty()
            if (levels != before.linked[stem].orEmpty()) store.write(prefs, ChecklistProgress(levels))
        }
    }

    private fun progressStore(dataFile: String, saveFileId: String) =
        JsonStore(dataStore, progressKey(game.id, dataFile, saveFileId), ChecklistProgress.serializer(), ChecklistProgress())

    companion object {
        /**
         * The key [dataFile]'s progress on one save file is stored under, e.g. `ss_checklist_items@file_1`,
         * for another tracker that reads or migrates it (Skyward Sword's Item Upgrades).
         */
        fun progressKey(gameId: String, dataFile: String, saveFileId: String): String =
            "${gameId}_checklist_${stemOf(dataFile)}@$saveFileId"

        /** "eow/heart_pieces.json" → "heart_pieces": what a `sameAs` link calls the file. */
        fun stemOf(dataFile: String): String = dataFile.substringAfterLast('/').removeSuffix(".json")

        /** Every checklist [game] lists, as `dataFile`s, in its order. */
        fun checklistFiles(game: Game): List<String> =
            game.trackers.filter { it.kind == TrackerKind.CHECKLIST }.mapNotNull { it.dataFile }
    }
}
