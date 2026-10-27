package gx.zelda.map.data

import gx.zelda.map.model.MapProjection
import gx.zelda.resources.Res
import kotlinx.io.Buffer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ovh.plrapps.mapcompose.core.TileStreamProvider

/**
 * A bundled tile pyramid: [levels] levels of [tileSize] px WebP tiles at
 * `<dir>/<level>/<row>/<col>.webp`, level 0 the most zoomed out and each level twice the one
 * before, up to [fullWidth] × [fullHeight] px. `tiles.json` beside them records the same numbers,
 * and a test keeps the two in step.
 */
class TilePyramid(val dir: String, val levels: Int, val fullWidth: Int, val fullHeight: Int, val tileSize: Int = 256) {
    fun path(level: Int, row: Int, col: Int): String = "$dir/$level/$row/$col.webp"

    /** Serves tiles from the app's resources; a tile outside the pyramid is simply not drawn. */
    val provider = TileStreamProvider { row, col, zoomLvl ->
        runCatching { Res.readBytes(path(zoomLvl, row, col)) }.getOrNull()?.let { bytes -> Buffer().apply { write(bytes) } }
    }
}

/**
 * BotW's tile pyramid, built by `tools/map-tiles/build_tiles.py`: four levels at
 * `files/botw/map`, the top one a pixel per world unit across the playable area.
 */
object MapTiles {
    const val DIR = "files/botw/map"
    const val LEVELS = 4
    const val TILE_SIZE = 256
    val FULL_WIDTH = (MapProjection.MAX_X - MapProjection.MIN_X).toInt()
    val FULL_HEIGHT = (MapProjection.MAX_Z - MapProjection.MIN_Z).toInt()

    val pyramid = TilePyramid(DIR, LEVELS, FULL_WIDTH, FULL_HEIGHT, TILE_SIZE)

    fun path(level: Int, row: Int, col: Int): String = pyramid.path(level, row, col)

    val provider: TileStreamProvider get() = pyramid.provider
}

/** A pyramid's `tiles.json`, as its build script writes it. */
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
