package gx.zelda.sidequests.data

import gx.zelda.armor.data.ArmorRepository
import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.sidequests.model.SideQuestCatalog
import kotlinx.serialization.json.Json

/**
 * The quests and the names of what they link to: shrines from the shrine tracker's dataset and
 * armor from the armor tracker's.
 */
data class SideQuestData(
    val catalog: SideQuestCatalog,
    /** Shrine names by id, e.g. "hila_rao" to "Hila Rao". */
    val shrineNames: Map<String, String> = emptyMap(),
    /** Armor piece names by id. */
    val armorNames: Map<String, String> = emptyMap(),
)

object SideQuestRepository {
    /** Each game's side quests live at `files/<game id>/side_quests.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/side_quests.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): SideQuestCatalog = json.decodeFromString(SideQuestCatalog.serializer(), text)

    suspend fun load(game: Game): SideQuestCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())

    /** The quests plus the linked names. Missing shrine or armor data only drops the names. */
    suspend fun loadData(game: Game): SideQuestData = SideQuestData(
        catalog = load(game),
        shrineNames = runCatching { ShrineRepository.load(game).shrines.associate { it.id to it.name } }.getOrDefault(emptyMap()),
        armorNames = runCatching { ArmorRepository.load(game).pieces.associate { it.id to it.name } }.getOrDefault(emptyMap()),
    )
}
