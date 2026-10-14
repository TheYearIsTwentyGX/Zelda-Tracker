package gx.zelda.mainquest.data

import gx.zelda.core.Game
import gx.zelda.mainquest.model.MainQuestCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object MainQuestRepository {
    /** Each game's main quest checklists live at `files/<game id>/main_quest.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/main_quest.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): MainQuestCatalog = json.decodeFromString(MainQuestCatalog.serializer(), text)

    suspend fun load(game: Game): MainQuestCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
