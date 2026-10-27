package gx.zelda.art

import gx.zelda.core.Games
import gx.zelda.map.data.MapTiles

/**
 * Stable asset ids, `<game>/<kind>/<name>` in lowercase letters, digits and underscores
 * (`botw/armor/hylian_hood`, `ss/thrill/bomb`, `botw/map/world`). Art packs key their files by
 * them, so they never change once published; docs/art-packs.md lists every kind.
 */
object ArtIds {
    private val PATTERN = Regex("[a-z0-9_]+/[a-z0-9_]+/[a-z0-9_]+")

    /** Kinds drawn as a single picture. */
    val ICON_KINDS = setOf("marker", "armor", "upgrade", "material", "boss", "thrill", "card", "reward", "scan")

    fun isValid(id: String): Boolean = PATTERN.matches(id)

    fun game(id: String): String = id.substringBefore('/')

    fun kind(id: String): String = id.substringAfter('/').substringBefore('/')

    /** A well-formed id for a game the app knows; anything else in a pack is ignored. */
    fun isKnownGame(id: String): Boolean = isValid(id) && Games.all.any { it.id == game(id) }

    fun marker(game: String, name: String) = "$game/marker/$name"
    fun armor(game: String, pieceId: String) = "$game/armor/$pieceId"
    fun ssUpgrade(levelId: String) = "ss/upgrade/$levelId"
    fun ssMaterial(materialId: String) = "ss/material/$materialId"
    fun ssBoss(icon: String) = "ss/boss/$icon"
    fun thrill(name: String) = "ss/thrill/$name"
    fun hwdeCard(cardId: String) = "hwde/card/$cardId"
    fun hwdeReward(kind: String) = "hwde/reward/$kind"
    fun hwdeMap(mapId: String) = "hwde/map/$mapId"

    /** BotW's world map, as tiles. */
    const val BOTW_WORLD = "botw/map/world"

    /**
     * Where each icon kind lived as a bundled drawable: `botw/armor/x` was `armor_x`. Pack 0 (the
     * art built into a personal build) is read through this table, and tools/artpack mirrors it.
     */
    val BUNDLED_PREFIXES = listOf(
        "botw/marker/" to "map_",
        "botw/armor/" to "armor_",
        "totk/armor/" to "armor_",
        "ss/upgrade/" to "ss_up_",
        "ss/material/" to "ss_mat_",
        "ss/boss/" to "ss_boss_",
        "ss/thrill/" to "thrill_",
        "hwde/card/" to "hwde_card_",
        "hwde/reward/" to "hwde_reward_",
    )

    /** The bundled drawable's name for [id], or null if the kind was never bundled. */
    fun bundledName(id: String): String? =
        BUNDLED_PREFIXES.firstOrNull { id.startsWith(it.first) }?.let { (prefix, name) -> name + id.removePrefix(prefix) }

    /** Tile pyramids the app can draw, by id. A pack's tileset must match its geometry exactly. */
    val TILESETS: Map<String, TilesetGeometry> = mapOf(
        BOTW_WORLD to TilesetGeometry(MapTiles.LEVELS, MapTiles.TILE_SIZE, MapTiles.FULL_WIDTH, MapTiles.FULL_HEIGHT),
    )
}

/**
 * A tile pyramid's shape: [levels] levels of [tileSize] px tiles, the last [width]×[height] px and
 * each one before it half the size, level 0 the most zoomed out (as MapCompose numbers them).
 */
data class TilesetGeometry(val levels: Int, val tileSize: Int, val width: Int, val height: Int) {
    private fun scale(level: Int) = 1 shl (levels - 1 - level)

    fun cols(level: Int): Int = ceilDiv(ceilDiv(width, scale(level)), tileSize)

    fun rows(level: Int): Int = ceilDiv(ceilDiv(height, scale(level)), tileSize)

    /** Every tile as `level/row/col`. */
    fun tiles(): List<String> = (0 until levels).flatMap { l -> (0 until rows(l)).flatMap { r -> (0 until cols(l)).map { c -> "$l/$r/$c" } } }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
}
