package gx.zelda.compendium.data

import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.core.Game
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object CompendiumRepository {
    /** Each game's Compendium lives at `files/<game id>/compendium.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/compendium.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CompendiumCatalog = json.decodeFromString(CompendiumCatalog.serializer(), text)

    suspend fun load(game: Game): CompendiumCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
