package gx.zelda.upgrades.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import gx.zelda.armor.model.PieceProgress
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.LevelRef
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.MapSnapshot
import gx.zelda.core.activeSaveFileStore
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persistence for a game's item-upgrade tracker: one [UpgradeProgress] per save file, plus the
 * viewing preferences, shared by every file. The active save file is the game's, shared with its
 * other trackers. Keys are prefixed with the game id.
 *
 * **Shared progress.** Copy 1 of each [links] item is the checklist item it names, so ticking the
 * Quick Beetle in Items & Equipment shows here and the other way round. Its state is read from and
 * written to that checklist's levels; this tracker's own key keeps the rest (extra pouch copies,
 * the copy counts and the potions). [progress] joins the two and [update] splits them again, in
 * one DataStore edit, so callers see one [UpgradeProgress] and every change is still one write.
 */
class UpgradeStorage(
    private val dataStore: DataStore<Preferences>,
    val game: Game,
    val links: List<ChecklistLink> = UpgradeLinks.of(game),
) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, progressKey(game.id, file.id), UpgradeProgress.serializer(), UpgradeProgress())
    }

    /** Per save file, the linked checklists' progress by `dataFile`. */
    private val checklistStores = game.saveFiles.associate { file ->
        file.id to links.map { it.storedFile }.distinct().associateWith { dataFile ->
            JsonStore(dataStore, ChecklistStorage.progressKey(game.id, dataFile, file.id), ChecklistProgress.serializer(), ChecklistProgress())
        }
    }
    private val linkedIds = links.map { it.itemId }.toSet()
    private val prefsStore = JsonStore(dataStore, "${game.id}_upgrades_prefs", UpgradePrefs.serializer(), UpgradePrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<UpgradePrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<UpgradeProgress> {
        val file = game.saveFile(saveFileId).id
        return dataStore.data.map { read(file, it) }.distinctUntilChanged()
    }

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun updatePrefs(transform: (UpgradePrefs) -> UpgradePrefs) = prefsStore.update(transform)

    suspend fun update(saveFileId: String, transform: (UpgradeProgress) -> UpgradeProgress) {
        swap(saveFileId, transform)
    }

    /** Like [update], returning the progress it replaced, read inside the same edit. */
    private suspend fun swap(saveFileId: String, transform: (UpgradeProgress) -> UpgradeProgress): UpgradeProgress {
        val file = game.saveFile(saveFileId).id
        var before = UpgradeProgress()
        dataStore.edit { prefs ->
            before = read(file, prefs)
            write(file, prefs, transform(before))
        }
        return before
    }

    /**
     * Marks every one of [keys] (copy keys) collected, or not, in one write; levels stay as they
     * are, as when a single row is ticked, except that a linked copy's level goes with it when it's
     * uncollected (its checklist has no level without the item). Returns what it replaced, for [restore].
     */
    suspend fun setAllOwned(saveFileId: String, keys: Collection<String>, owned: Boolean): UpgradeSnapshot =
        setAll(saveFileId, keys) { _, piece -> piece.copy(owned = owned) }

    /** Sets each of [keys] to `transform` of its state in one write, returning what it replaced, for [restore]. */
    suspend fun setAll(
        saveFileId: String,
        keys: Collection<String>,
        transform: (key: String, PieceProgress) -> PieceProgress,
    ): UpgradeSnapshot {
        val file = game.saveFile(saveFileId).id
        var before = UpgradeProgress()
        var stored = emptyMap<String, Int?>()
        dataStore.edit { prefs ->
            before = read(file, prefs)
            stored = links.filter { it.via != null && it.itemId in keys }.associate { it.itemId to storedLevel(file, prefs, it) }
            write(file, prefs, before.copy(items = before.items + keys.associateWith { transform(it, before.of(it)) }))
        }
        return UpgradeSnapshot(MapSnapshot.of(before.items, keys).before, stored)
    }

    /** Puts back what [setAll] replaced, in one write; everything else keeps its state. */
    suspend fun restore(saveFileId: String, snapshot: UpgradeSnapshot) {
        val file = game.saveFile(saveFileId).id
        dataStore.edit { prefs ->
            val progress = read(file, prefs)
            write(file, prefs, progress.copy(items = MapSnapshot(snapshot.before).restore(progress.items)))
            // A row linked onward (the Hylian Shield's Lightning Round tier) gets its exact level back,
            // with the tiers around it, which owning or not alone can't say.
            for ((itemId, level) in snapshot.stored) {
                val link = links.single { it.itemId == itemId }
                val store = checklistStores.getValue(file).getValue(link.storedFile)
                val checklist = store.read(prefs)
                store.write(prefs, checklist.withRaw(LevelRef(null, link.storedItemId), level))
            }
        }
    }

    private fun storedLevel(file: String, prefs: Preferences, link: ChecklistLink): Int? =
        checklistStores.getValue(file).getValue(link.storedFile).read(prefs).levels[link.storedItemId]

    /** This tracker's own progress, with each linked copy's state taken from its checklist. */
    private fun read(file: String, prefs: Preferences): UpgradeProgress {
        val own = progressStores.getValue(file).read(prefs)
        if (links.isEmpty()) return own
        val checklists = checklistStores.getValue(file).mapValues { it.value.read(prefs) }
        val linked = links.mapNotNull { link ->
            val stored = checklists.getValue(link.storedFile).levels[link.storedItemId] ?: 0
            link.pieceOf(link.rowLevel(stored))?.let { link.itemId to it }
        }
        return own.copy(items = own.items - linkedIds + linked)
    }

    /** Splits [progress] back: linked copies to their checklists' levels, the rest to this tracker's key. */
    private fun write(file: String, prefs: MutablePreferences, progress: UpgradeProgress) {
        val ownStore = progressStores.getValue(file)
        val own = progress.copy(items = progress.items - linkedIds)
        if (own != ownStore.read(prefs)) ownStore.write(prefs, own)
        for ((dataFile, fileLinks) in links.groupBy { it.storedFile }) {
            val store = checklistStores.getValue(file).getValue(dataFile)
            val checklist = store.read(prefs)
            val levels = fileLinks.fold(checklist.levels) { levels, link ->
                val level = link.storedLevel(link.checklistLevelOf(progress.items[link.itemId]), levels[link.storedItemId] ?: 0)
                if (level == 0) levels - link.storedItemId else levels + (link.storedItemId to level)
            }
            if (levels != checklist.levels) store.write(prefs, checklist.copy(levels = levels))
        }
    }

    /**
     * What a bulk change replaced: each copy's state by key, as stored (null for none), and for a
     * linked item whose checklist row is itself linked ([ChecklistLink.via]) the level stored there,
     * so its undo puts that back exactly.
     */
    data class UpgradeSnapshot(val before: Map<String, PieceProgress?>, val stored: Map<String, Int?> = emptyMap())

    companion object {
        /** The key one save file's own upgrade progress is stored under, e.g. `ss_upgrades@file_1`. */
        fun progressKey(gameId: String, saveFileId: String): String = "${gameId}_upgrades@$saveFileId"
    }
}
