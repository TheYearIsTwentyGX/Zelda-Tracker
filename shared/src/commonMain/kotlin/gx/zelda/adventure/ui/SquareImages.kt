package gx.zelda.adventure.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.Square
import gx.zelda.art.ArtIds
import gx.zelda.art.GameArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * A map's pictures, decoded as they're needed: the small whole-map preview once, and the full-size
 * squares only while they're on screen, keeping the last [capacity] (each is 256×176, about 180 KB
 * decoded, so the whole Adventure Map would be 23 MB). [version] changes whenever a picture
 * arrives, so a drawing that reads it redraws.
 */
class SquareImages(private val map: AdventureMap, private val capacity: Int = 48) {
    var version by mutableIntStateOf(0)
        private set

    var preview: ImageBitmap? = null
        private set

    // Kept in use order (each read moves a square to the end), so the first is the one to drop.
    private val squares = LinkedHashMap<String, ImageBitmap>()
    private val failed = mutableSetOf<String>()

    /** The square's picture if it's been decoded, marking it recently used. */
    fun square(square: Square): ImageBitmap? = squares.remove(square.id)?.also { squares[square.id] = it }

    /** Decodes the preview, if the map has one and it isn't loaded. */
    suspend fun loadPreview() {
        if (preview != null) return
        val art = map.art ?: return
        preview = decode(null, art.previewPath ?: return) ?: return
        version++
    }

    /** Decodes whichever of [wanted] aren't loaded yet, dropping the least recently used over [capacity]. */
    suspend fun load(wanted: List<Square>) {
        val art = map.art ?: return
        for (square in wanted) {
            if (square.id in squares || square.id in failed) continue
            val bitmap = decode(square.id, art.squarePath(square))
            if (bitmap == null) {
                failed += square.id
                continue
            }
            squares[square.id] = bitmap
            while (squares.size > capacity) squares.remove(squares.keys.first())
            version++
        }
    }

    /** [square]'s picture, or the preview's when it's null, from an art pack or the app ([GameArt]). */
    private suspend fun decode(square: String?, bundledPath: String): ImageBitmap? {
        val bytes = GameArt.square(ArtIds.hwdeMap(map.id), square, bundledPath) ?: return null
        return withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
    }
}
