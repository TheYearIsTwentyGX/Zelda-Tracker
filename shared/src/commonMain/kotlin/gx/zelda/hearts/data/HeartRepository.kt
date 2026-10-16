package gx.zelda.hearts.data

import gx.zelda.core.Game
import gx.zelda.hearts.model.HeartCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object HeartRepository {
    /** Each game's hearts live at `files/<game id>/heart_pieces.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/heart_pieces.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): HeartCatalog = json.decodeFromString(HeartCatalog.serializer(), text)

    suspend fun load(game: Game): HeartCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
