package gx.zelda.map.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.data.StoryProgress
import gx.zelda.core.CachedLoad
import gx.zelda.core.Game
import gx.zelda.map.model.MapProjection
import gx.zelda.map.model.MarkerIcon
import gx.zelda.map.model.WorldBounds
import kotlin.math.roundToInt

/**
 * One game's map: its art's tile pyramid, the part of the world the art covers, the colour around
 * the art (ARGB, matching the edge tiles' padding), and what its About dialog says.
 */
class MapSpec(val tiles: TilePyramid, val world: WorldBounds, val background: Long, val about: String)

object MapSpecs {
    val BOTW = MapSpec(
        tiles = MapTiles.pyramid,
        world = MapProjection.bounds,
        background = 0xFF283C46,
        about = "Map art © Nintendo, from Breath of the Wild's game files, via objmap.zeldamods.org. " +
            "It is bundled with the app, so the map works offline.\n\n" +
            "Shrine, tower, Korok and boss positions are objmap's too, and so are the shrine and tower " +
            "icons, traced from the game's. As in the game, a shrine's glyph is orange until it's completed " +
            "and a tower is orange until it's activated; both turn blue when done. Korok seeds and bosses " +
            "sit on a blue disc once collected or defeated and an orange one when found. Bosses show " +
            "Kilton's Medal of Honor for their kind, or the Lynel Mask for Lynels. Undiscovered places " +
            "are grey. Numbers on Korok clusters count the seeds not yet collected.",
    )

    val EOW = MapSpec(
        tiles = EowMap.tiles,
        world = EowMap.world,
        background = 0xFF261A10,
        about = "Map art © Nintendo: Echoes of Wisdom's map of Hyrule, as Zelda Wiki hosts it. " +
            "It is bundled with the app, so the map works offline.\n\n" +
            "Each place is where the game's own map pins it (datamined, and the same on Zelda Dungeon's " +
            "interactive map). Things inside caves, rifts and dungeons are pinned at their entrance; a " +
            "Heart Container is at its dungeon. The icons are the game's map icons, on an orange disc until " +
            "you tick the place and a blue one after; a badge counts the Might Crystals a place holds. " +
            "Ticking a marker ticks the same row in its checklist, and the story setting and \"Hide " +
            "unchecked\" hide the same places there and here.",
    )
}

/**
 * Echoes of Wisdom's map: Zelda Wiki's map of Hyrule, cut by `tools/map-tiles/build_eow_tiles.py`,
 * with the Pieces of Heart, Stamp Stands and Might Crystals at the game's own map pins
 * (`files/eow/map/markers.json`, docs/eow-map.md).
 */
object EowMap {
    val tiles = TilePyramid("files/eow/map", levels = 4, fullWidth = 3600, fullHeight = 2442)

    /** The world area the 3600 px art covers, from the build script's transform; its tiles.json records the same. */
    val world = WorldBounds(minX = -49.1018, maxX = 712.5749, minZ = -20.0479, maxZ = 496.6228)

    const val MARKERS = "files/eow/map/markers.json"

    /** The checklists on the map, in chip order. */
    val sources = listOf(
        ChecklistMapSource(
            dataFile = "eow/heart_pieces.json",
            title = "Hearts",
            noun = "heart",
            hiddenLabel = "Uncollected heart",
            sections = linkedMapOf("pieces" to MarkerIcon.EOW_HEART_PIECE, "containers" to MarkerIcon.EOW_HEART_CONTAINER),
        ),
        ChecklistMapSource(
            dataFile = "eow/stamp_stands.json",
            title = "Stamps",
            noun = "stamp",
            hiddenLabel = "Unfound Stamp Stand",
            sections = mapOf("stands" to MarkerIcon.EOW_STAMP),
            notDone = "Not stamped",
            done = "Stamped",
        ),
        ChecklistMapSource(
            dataFile = "eow/might_crystals.json",
            title = "Might Crystals",
            noun = "Might Crystal",
            hiddenLabel = "Unfound Might Crystal",
            sections = mapOf("crystals" to MarkerIcon.EOW_MIGHT_CRYSTAL),
            count = { it.value.roundToInt() },
        ),
    )

    private val markers = CachedLoad { ChecklistMarkers.load(MARKERS) }

    fun layers(dataStore: DataStore<Preferences>, game: Game): List<MapLayerProvider> = sources.map { source ->
        val storage = ChecklistStorage(dataStore, game, source.dataFile)
        ChecklistMapLayer(source, storage, StoryProgress(dataStore, game, source.dataFile)::gate, ChecklistMapLayer.placedItems(source, markers))
    }
}

/** [game]'s map; a game without its own is drawn on BotW's (only BotW and EoW have a map tracker). */
fun mapSpecFor(game: Game): MapSpec = if (game.id == "eow") MapSpecs.EOW else MapSpecs.BOTW

/** [game]'s map layers, in chip order. */
fun mapLayersFor(dataStore: DataStore<Preferences>, game: Game): List<MapLayerProvider> =
    if (game.id == "eow") EowMap.layers(dataStore, game) else botwMapLayers(dataStore, game)

/** The checklists [game]'s map shows, so their item pages can offer "Show on map". */
fun checklistMapSources(game: Game): List<ChecklistMapSource> = if (game.id == "eow") EowMap.sources else emptyList()
