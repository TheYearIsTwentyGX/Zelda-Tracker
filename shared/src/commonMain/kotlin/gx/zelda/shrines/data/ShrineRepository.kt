package gx.zelda.shrines.data

import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.shrines.model.ShrineCatalog
import kotlinx.serialization.json.Json

object ShrineRepository {
    /** Each game's shrines live at `files/<game id>/shrines.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/shrines.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ShrineCatalog = json.decodeFromString(ShrineCatalog.serializer(), text)

    suspend fun load(game: Game): ShrineCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
