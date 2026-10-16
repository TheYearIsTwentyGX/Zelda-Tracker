package gx.zelda.kinstones.data

import gx.zelda.core.Game
import gx.zelda.kinstones.model.KinstoneCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object KinstoneRepository {
    /** Each game's fusions live at `files/<game id>/kinstones.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/kinstones.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): KinstoneCatalog = json.decodeFromString(KinstoneCatalog.serializer(), text)

    suspend fun load(game: Game): KinstoneCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
