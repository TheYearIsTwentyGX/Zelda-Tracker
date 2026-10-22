package gx.zelda.upgrades.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.armor.model.PieceProgress
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * The one-time move to shared progress (October 2026). Before it, Skyward Sword's Item Upgrades
 * tracker and its checklists each kept their own progress for the same things:
 *
 * - **Linked items** ([UpgradeLinks]): copy 1's state in `ss_upgrades@file_N` moves to the
 *   checklist item's level (Items & Equipment, Shields), at the higher of the two, and leaves the
 *   upgrade key. Extra pouch copies (`seed_satchel#2`…) and the copy counts stay where they are.
 * - **Potions**: Items & Equipment's Potion Infusions tab (each potion's infusions, as tiers) is
 *   gone, so its levels move to the upgrade key, which the Potion Infusions recipe book and Item
 *   Upgrades share, at the higher of the two; the infusions done mean the base potion was bought.
 *
 * Each save file is merged on its own. Moving rather than copying is what makes it idempotent:
 * nothing new ever writes copy 1 of a linked item to the upgrade key, or a potion to the
 * checklist's, so [shouldMigrate] is false once it has run and stays false. It runs as a DataStore
 * migration, before anything reads the store, so no screen ever sees the two stores unmerged.
 */
class SharedProgressMigration(
    private val game: Game = Games.SS,
    private val links: List<ChecklistLink> = UpgradeLinks.SS,
    private val potions: List<String> = POTIONS,
    private val potionChecklist: String = "ss/items.json",
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = game.saveFiles.any { pending(currentData, it.id) }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().also { prefs -> game.saveFiles.forEach { migrate(prefs, it.id) } }

    override suspend fun cleanUp() = Unit

    /** True when [saveFileId] still has progress in an old place. */
    fun pending(prefs: Preferences, saveFileId: String): Boolean {
        val own = read(prefs, upgradeKey(saveFileId), UpgradeProgress.serializer(), UpgradeProgress()) ?: return false
        val checklist = read(prefs, checklistKey(potionChecklist, saveFileId), ChecklistProgress.serializer(), ChecklistProgress()) ?: return false
        return links.any { it.itemId in own.items } || potions.any { it in checklist.levels }
    }

    /** Merges one save file's progress into its shared places, in [prefs]. A file with a store that can't be read is left alone. */
    fun migrate(prefs: MutablePreferences, saveFileId: String) {
        if (!pending(prefs, saveFileId)) return
        val ownKey = upgradeKey(saveFileId)
        val ownBefore = read(prefs, ownKey, UpgradeProgress.serializer(), UpgradeProgress()) ?: return
        val dataFiles = (links.map { it.dataFile } + potionChecklist).distinct()
        val before = dataFiles.associateWith { dataFile ->
            read(prefs, checklistKey(dataFile, saveFileId), ChecklistProgress.serializer(), ChecklistProgress()) ?: return
        }
        var own = ownBefore
        val checklists = before.toMutableMap()

        for (link in links) {
            val piece = own.items[link.itemId] ?: continue
            val checklist = checklists.getValue(link.dataFile)
            val level = maxOf(checklist.levels[link.checklistItemId] ?: 0, link.checklistLevelOf(piece))
            checklists[link.dataFile] = checklist.withRaw(link.checklistItemId, level)
            own = own.copy(items = own.items - link.itemId)
        }
        for (potion in potions) {
            val checklist = checklists.getValue(potionChecklist)
            val infusions = checklist.levels[potion] ?: continue
            checklists[potionChecklist] = checklist.withRaw(potion, 0)
            if (infusions <= 0) continue
            val piece = own.of(potion)
            val counted = if (piece.owned) piece.level else 0
            own = own.copy(items = own.items + (potion to PieceProgress(owned = true, level = maxOf(counted, infusions))))
        }

        if (own != ownBefore) prefs[ownKey] = json.encodeToString(UpgradeProgress.serializer(), own)
        for ((dataFile, progress) in checklists) {
            if (progress != before[dataFile]) prefs[checklistKey(dataFile, saveFileId)] = json.encodeToString(ChecklistProgress.serializer(), progress)
        }
    }

    private fun upgradeKey(saveFileId: String) = stringPreferencesKey(UpgradeStorage.progressKey(game.id, saveFileId))

    private fun checklistKey(dataFile: String, saveFileId: String) =
        stringPreferencesKey(ChecklistStorage.progressKey(game.id, dataFile, saveFileId))

    private fun ChecklistProgress.withRaw(id: String, level: Int) =
        copy(levels = if (level <= 0) levels - id else levels + (id to level))

    /** [default] when absent, null when there but unreadable. */
    private fun <T> read(prefs: Preferences, key: Preferences.Key<String>, serializer: KSerializer<T>, default: T): T? {
        val text = prefs[key] ?: return default
        return runCatching { json.decodeFromString(serializer, text) }.getOrNull()
    }

    companion object {
        /** Items & Equipment's old Potion Infusions rows; the upgrade items have the same ids. */
        val POTIONS = listOf("heart_potion", "revitalizing_potion", "stamina_potion", "air_potion", "guardian_potion")

        /** As `JsonStore`'s, so what this writes reads back the same. */
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
