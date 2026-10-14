package gx.zelda.shrines.scan

import gx.zelda.core.Game
import gx.zelda.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A world position on the map: x runs west to east, z north to south. */
data class WorldPoint(val x: Double, val z: Double)

/** A square grey icon silhouette, centred on the icon's world point; values 0..1, row by row. */
class IconTemplate(val size: Int, val values: FloatArray) {
    val radius: Int get() = size / 2
}

/**
 * Everything the map scanner knows about the game world: where each shrine and Sheikah Tower is,
 * the screen scale of the fully zoomed-out map, and the icon silhouettes it matches against.
 */
class ShrineMapData(
    /** Screen pixels per world unit on the fully zoomed-out 1920×1080 map. */
    val minZoomScale: Double,
    /** Keyed by shrine id, as in `shrines.json`. */
    val shrines: Map<String, WorldPoint>,
    val towers: List<WorldPoint>,
    val shrineTemplate: IconTemplate,
    val towerTemplate: IconTemplate,
)

object ShrineMapRepository {
    /** Each game's map data lives at `files/<game id>/shrine_map.json`. */
    fun path(game: Game): String = "files/${game.id}/shrine_map.json"

    @Serializable
    private class TemplateJson(val size: Int, val rows: List<String>)

    @Serializable
    private class MapJson(
        val minZoomScale: Double,
        val shrines: Map<String, List<Double>>,
        val towers: Map<String, List<Double>>,
        val shrineTemplate: TemplateJson,
        val towerTemplate: TemplateJson,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ShrineMapData {
        val m = json.decodeFromString(MapJson.serializer(), text)
        return ShrineMapData(
            minZoomScale = m.minZoomScale,
            shrines = m.shrines.mapValues { (_, p) -> WorldPoint(p[0], p[1]) },
            towers = m.towers.values.map { WorldPoint(it[0], it[1]) },
            shrineTemplate = m.shrineTemplate.toTemplate(),
            towerTemplate = m.towerTemplate.toTemplate(),
        )
    }

    suspend fun load(game: Game): ShrineMapData = parse(Res.readBytes(path(game)).decodeToString())

    private fun TemplateJson.toTemplate(): IconTemplate {
        val values = rows.flatMap { row -> row.trim().split(' ').map { it.toInt() / 255f } }
        require(rows.size == size && values.size == size * size) { "template must be $size × $size" }
        return IconTemplate(size, values.toFloatArray())
    }
}
