package gx.zelda.map.data

import gx.zelda.map.model.MapProjection
import gx.zelda.resources.Res
import kotlinx.io.Buffer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ovh.plrapps.mapcompose.core.TileStreamProvider

/**
 * The bundled tile pyramid, built by `tools/map-tiles/build_tiles.py`: four levels of 256 px WebP
 * tiles at `files/botw/map/<level>/<row>/<col>.webp`, level 0 the most zoomed out. The top level is
 * one pixel per world unit across the playable area. `tiles.json` beside them records the same
 * numbers, and a test keeps the two in step.
 */
object MapTiles {
    const val DIR = "files/botw/map"
    const val LEVELS = 4
    const val TILE_SIZE = 256
    val FULL_WIDTH = (MapProjection.MAX_X - MapProjection.MIN_X).toInt()
    val FULL_HEIGHT = (MapProjection.MAX_Z - MapProjection.MIN_Z).toInt()

    fun path(level: Int, row: Int, col: Int): String = "$DIR/$level/$row/$col.webp"

    /** Serves tiles from the app's resources; a tile outside the pyramid is simply not drawn. */
    val provider = TileStreamProvider { row, col, zoomLvl ->
        runCatching { Res.readBytes(path(zoomLvl, row, col)) }.getOrNull()?.let { bytes -> Buffer().apply { write(bytes) } }
    }
}

/** `files/botw/map/tiles.json`, as the build script writes it. */
@Serializable
data class TileManifest(
    val tileSize: Int,
    val format: String,
    val fullWidth: Int,
    val fullHeight: Int,
    val world: World,
    val levels: List<Level>,
) {
    @Serializable
    data class World(val minX: Double, val maxX: Double, val minZ: Double, val maxZ: Double)

    @Serializable
    data class Level(val level: Int, val width: Int, val height: Int, val cols: Int, val rows: Int)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): TileManifest = json.decodeFromString(serializer(), text)
    }
}
