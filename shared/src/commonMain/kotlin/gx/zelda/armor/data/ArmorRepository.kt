package gx.zelda.armor.data

import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.core.Game
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object ArmorRepository {
    /** Each game's armor lives at `files/<game id>/armor.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/armor.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ArmorCatalog = json.decodeFromString(ArmorCatalog.serializer(), text)

    suspend fun load(game: Game): ArmorCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
