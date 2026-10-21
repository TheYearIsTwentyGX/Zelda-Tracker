package gx.zelda.upgrades.data

import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.upgrades.model.UpgradeCatalog
import kotlinx.serialization.json.Json

object UpgradeRepository {
    /** A game's upgrades live at `files/<game id>/upgrades.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/upgrades.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): UpgradeCatalog = json.decodeFromString(UpgradeCatalog.serializer(), text)

    suspend fun load(game: Game): UpgradeCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())
}
