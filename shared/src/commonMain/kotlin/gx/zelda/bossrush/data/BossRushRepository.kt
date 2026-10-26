package gx.zelda.bossrush.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.bossrush.model.BossRushCatalog
import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.StoryProgress
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.Game
import gx.zelda.resources.Res
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** The Lightning Round's bosses with the Side Quests item they point at: its prizes and story gate. */
data class BossRushData(val catalog: BossRushCatalog, val sideQuest: ChecklistItem?)

object BossRushRepository {
    /** A game's Lightning Round lives at `files/<game id>/boss_rush.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/boss_rush.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): BossRushCatalog = json.decodeFromString(BossRushCatalog.serializer(), text)

    /** The bosses, and the Side Quests item they name; a missing item leaves the board ungated, without prizes. */
    suspend fun load(game: Game): BossRushData {
        val catalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
        val sideQuest = runCatching { ChecklistRepository.load(catalog.sideQuest.dataFile).item(catalog.sideQuest.itemId) }.getOrNull()
        return BossRushData(catalog, sideQuest)
    }
}

/**
 * The story gate on each save file, as Side Quests & Minigames sees it: the Lightning Round's item
 * there (`sideQuest` in boss_rush.json, checked by the dataset test) carries its `unlockedBy` step.
 */
fun bossRushStoryGate(dataStore: DataStore<Preferences>, game: Game): (String) -> Flow<StoryGate> =
    StoryProgress(dataStore, game, "${game.id}/side_quests.json")::gate
