package gx.zelda.checklist.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.CachedLoad
import gx.zelda.core.Game
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The checklist a game's [gx.zelda.checklist.model.ChecklistItem.unlockedBy] links point into: its
 * `<game id>/main_quest.json`, when the game lists one. Null for a game without (BotW's Main Quest
 * is bespoke; LoZ, AoL and the stage-select games have none), so nothing there is gated.
 */
fun storyFileOf(game: Game): String? =
    game.trackers.firstOrNull { it.kind == TrackerKind.CHECKLIST && it.dataFile == "${game.id}/main_quest.json" }?.dataFile

/**
 * What [game]'s story has unlocked, for the checklist [dataFile]: the Settings toggle with the
 * Main Quest's progress on a save file. The Main Quest itself is never gated.
 */
class StoryProgress(
    dataStore: DataStore<Preferences>,
    game: Game,
    dataFile: String,
    private val loadStory: suspend (String) -> ChecklistCatalog = { ChecklistRepository.load(it) },
) {
    private val storyFile: String? = storyFileOf(game)?.takeIf { it != dataFile }
    private val storage: ChecklistStorage? = storyFile?.let { ChecklistStorage(dataStore, game, it) }
    private val enabled: Flow<Boolean> = AppSettingsStorage(dataStore).settings.map { it.storyGating }.distinctUntilChanged()
    private val story = CachedLoad { loadStory(storyFile!!) }

    /** The gate on [saveFileId]; [StoryGate.OFF] when there's no story file or it can't be read. */
    fun gate(saveFileId: String): Flow<StoryGate> {
        val storage = storage ?: return flowOf(StoryGate.OFF)
        return flow {
            val catalog = runCatching { story.get() }.getOrNull()
            if (catalog == null) {
                emit(StoryGate.OFF)
            } else {
                emitAll(combine(enabled, storage.progress(saveFileId)) { on, progress -> StoryGate.of(on, catalog, progress) })
            }
        }
    }
}
