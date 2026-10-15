package gx.zelda.bosses.data

import gx.zelda.bosses.model.BossCatalog
import gx.zelda.bosses.model.BossData
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.core.Game
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object BossRepository {
    /** Each game's overworld bosses live at `files/<game id>/bosses.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/bosses.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): BossCatalog = json.decodeFromString(BossCatalog.serializer(), text)

    suspend fun load(game: Game): BossCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())

    /** The bosses and the Compendium that names them, as the tracker uses them. */
    suspend fun loadData(game: Game): BossData = BossData(load(game), CompendiumRepository.load(game))
}
