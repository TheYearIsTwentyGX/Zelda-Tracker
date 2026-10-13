package gx.zelda.fairies.data

import gx.zelda.core.Game
import gx.zelda.fairies.model.FairyCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object FairyRepository {
    /** Each game's fountains live at `files/<game id>/fairies.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/fairies.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): FairyCatalog = json.decodeFromString(FairyCatalog.serializer(), text)

    suspend fun load(game: Game): FairyCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
