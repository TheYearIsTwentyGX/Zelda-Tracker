package gx.zelda.art

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An art pack's `manifest.json`, at the root of its zip. It maps asset ids ([ArtIds]) to files
 * in the zip; files it doesn't name are ignored. docs/art-packs.md describes the format.
 */
@Serializable
data class ArtManifest(
    val format: Int,
    val pack: PackInfo,
    /** Single pictures: PNG, WebP or an Android vector drawable (`.xml`). */
    val icons: Map<String, String> = emptyMap(),
    val tilesets: Map<String, Tileset> = emptyMap(),
    val squaresets: Map<String, Squareset> = emptyMap(),
    /** JSON datasets a pack may carry instead of the app (HW:DE's maps). */
    val data: Map<String, String> = emptyMap(),
) {
    @Serializable
    data class PackInfo(val id: String, val name: String, val version: String = "", val created: String = "")

    /** A tile pyramid; [path] has `{level}`, `{row}` and `{col}` in it. */
    @Serializable
    data class Tileset(val path: String, val levels: Int, val tileSize: Int, val width: Int, val height: Int) {
        fun file(tile: String): String {
            val (level, row, col) = tile.split('/')
            return path.replace("{level}", level).replace("{row}", row).replace("{col}", col)
        }

        val geometry: TilesetGeometry get() = TilesetGeometry(levels, tileSize, width, height)
    }

    /** An Adventure Map's squares, [width]×[height] each; [path] has `{id}` in it. */
    @Serializable
    data class Squareset(
        val path: String,
        val squares: List<String>,
        val preview: String? = null,
        val width: Int,
        val height: Int,
    ) {
        fun file(square: String): String = path.replace("{id}", square)
    }

    companion object {
        /** The newest format this app reads. */
        const val FORMAT = 1

        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

        fun parse(text: String): ArtManifest = json.decodeFromString(serializer(), text)
    }
}
