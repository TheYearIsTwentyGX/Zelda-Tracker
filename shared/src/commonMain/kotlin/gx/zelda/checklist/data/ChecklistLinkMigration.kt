package gx.zelda.checklist.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Game
import kotlinx.serialization.json.Json

/**
 * The one-time merge of progress kept twice before `sameAs` links (October 2026). Until then a
 * linked item and the item it names were ticked separately: a save can have Batreaux's 5-crystal
 * reward ticked in Gratitude Crystals and no Wallet in Items & Equipment, or the reverse.
 *
 * For each linked item ([ChecklistLinks.Alias]) still holding a level of its own, on each save file
 * of each game in [games] on its own: ticked means the item it names is raised to at least the
 * linked tier (or ticked), so ticked on either side is ticked and the higher tier wins; then the
 * linked item's own entry is removed, since its level is now read from the other.
 *
 * Moving rather than copying is what makes it idempotent: nothing writes a linked item's own entry
 * any more, so once a file is merged there's nothing left to merge, and [shouldMigrate] stays false.
 * A link added in a later release is merged the same way on the next start. It runs as a DataStore
 * migration after [gx.zelda.upgrades.data.SharedProgressMigration], before anything reads the store.
 * A save file with a store that can't be read is left alone for the links that touch it.
 */
class ChecklistLinkMigration(
    private val games: List<Game> = ChecklistLinks.GAMES,
    private val load: suspend (String) -> ChecklistCatalog = { ChecklistRepository.load(it) },
) : DataMigration<Preferences> {
    private val aliases = mutableMapOf<String, List<ChecklistLinks.Alias>>()

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        games.any { game -> hasProgress(currentData, game) && game.saveFiles.any { pending(currentData, game, aliasesOf(game), it.id) } }

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().also { prefs ->
        for (game in games) {
            if (!hasProgress(prefs, game)) continue
            val aliases = aliasesOf(game)
            game.saveFiles.forEach { migrate(prefs, game, aliases, it.id) }
        }
    }

    override suspend fun cleanUp() = Unit

    /** True when [saveFileId] has a linked item with a level of its own still, and the file it links to can be read. */
    fun pending(prefs: Preferences, game: Game, aliases: List<ChecklistLinks.Alias>, saveFileId: String): Boolean = aliases.any {
        read(prefs, game, it.dataFile, saveFileId)?.levels?.containsKey(it.item.id) == true && read(prefs, game, it.targetFile, saveFileId) != null
    }

    /** Merges one save file's linked items into the items they name, in [prefs]. */
    fun migrate(prefs: MutablePreferences, game: Game, aliases: List<ChecklistLinks.Alias>, saveFileId: String) {
        if (!pending(prefs, game, aliases, saveFileId)) return
        val files = aliases.flatMap { listOf(it.dataFile, it.targetFile) }.distinct()
        val before = files.associateWith { read(prefs, game, it, saveFileId) }
        val after = before.toMutableMap()
        for (alias in aliases) {
            val own = after[alias.dataFile] ?: continue
            val level = own.levels[alias.item.id] ?: continue
            // An unreadable target keeps the entry where it is, rather than lose the tick.
            if (after[alias.targetFile] == null) continue
            after[alias.dataFile] = own.copy(levels = own.levels - alias.item.id)
            if (level <= 0) continue
            val target = after.getValue(alias.targetFile)!!
            val raised = alias.link.canonicalFor(true, target.levels[alias.link.itemId] ?: 0)
            after[alias.targetFile] = target.copy(levels = target.levels + (alias.link.itemId to raised))
        }
        for ((file, progress) in after) {
            if (progress != null && progress != before[file]) {
                prefs[key(game, file, saveFileId)] = json.encodeToString(ChecklistProgress.serializer(), progress)
            }
        }
    }

    private suspend fun aliasesOf(game: Game): List<ChecklistLinks.Alias> = aliases.getOrPut(game.id) {
        runCatching {
            ChecklistLinks.aliases(game, ChecklistStorage.checklistFiles(game).associateWith { load(it) })
        }.getOrDefault(emptyList())
    }

    /** True when [game] has any checklist progress at all, so a game never played loads nothing. */
    private fun hasProgress(prefs: Preferences, game: Game): Boolean =
        prefs.asMap().keys.any { it.name.startsWith("${game.id}_checklist_") && '@' in it.name }

    private fun key(game: Game, dataFile: String, saveFileId: String) =
        stringPreferencesKey(ChecklistStorage.progressKey(game.id, dataFile, saveFileId))

    /** Empty when absent, null when there but unreadable. */
    private fun read(prefs: Preferences, game: Game, dataFile: String, saveFileId: String): ChecklistProgress? {
        val text = prefs[key(game, dataFile, saveFileId)] ?: return ChecklistProgress()
        return runCatching { json.decodeFromString(ChecklistProgress.serializer(), text) }.getOrNull()
    }

    private companion object {
        /** As `JsonStore`'s, so what this writes reads back the same. */
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
