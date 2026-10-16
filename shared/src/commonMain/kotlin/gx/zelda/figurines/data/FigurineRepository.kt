package gx.zelda.figurines.data

import gx.zelda.core.Game
import gx.zelda.figurines.model.FigurineCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object FigurineRepository {
    /** Each game's figurines live at `files/<game id>/figurines.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/figurines.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): FigurineCatalog = json.decodeFromString(FigurineCatalog.serializer(), text)

    suspend fun load(game: Game): FigurineCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
