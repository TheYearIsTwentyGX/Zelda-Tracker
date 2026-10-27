package gx.zelda.art

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.compose.resources.painterResource

/**
 * The picture for asset [id] ([ArtIds]), from an art pack or the app ([GameArt]). With neither,
 * or while a pack's file is still decoding, it draws [missing] instead: by default an empty box
 * the same size, so rows stay aligned.
 */
@Composable
fun ArtImage(
    id: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    missing: @Composable () -> Unit = { Box(modifier) },
) {
    val painter = rememberArtPainter(id)
    if (painter == null) {
        missing()
    } else {
        Image(painter, contentDescription, modifier, contentScale = contentScale, alpha = alpha, colorFilter = colorFilter)
    }
}

/** [id]'s picture as a painter, or null; see [ArtImage]. */
@Composable
fun rememberArtPainter(id: String): Painter? {
    val index by GameArt.index.collectAsState()
    return when (val ref = remember(index, id) { GameArt.icon(id, index) }) {
        null -> null
        is ArtRef.Bundled -> painterResource(ref.resource)
        is ArtRef.File -> rememberFilePainter(ref)
    }
}

@Composable
private fun rememberFilePainter(ref: ArtRef.File): Painter? {
    val density = LocalDensity.current
    val decoded by produceState(IconCache[ref], ref) {
        if (value == null) value = decode(ref, density)?.also { IconCache.put(ref, it) }
    }
    return when (val d = decoded) {
        is ImageBitmap -> remember(d) { BitmapPainter(d) }
        is ImageVector -> rememberVectorPainter(d)
        else -> null
    }
}

private suspend fun decode(ref: ArtRef.File, density: Density): Any? {
    val bytes = GameArt.read(ref.path) ?: return null
    return withContext(Dispatchers.Default) {
        runCatching { if (ref.vector) bytes.decodeToImageVector(density) else bytes.decodeToImageBitmap() }.getOrNull()
    }
}

/**
 * Decoded pack pictures, the most recently used [CAPACITY]. Only touched on the main thread:
 * decoding runs elsewhere, but the result is stored once it's back.
 */
private object IconCache {
    private const val CAPACITY = 256
    private val entries = LinkedHashMap<ArtRef.File, Any>()

    operator fun get(ref: ArtRef.File): Any? = entries.remove(ref)?.also { entries[ref] = it }

    fun put(ref: ArtRef.File, value: Any) {
        entries[ref] = value
        while (entries.size > CAPACITY) entries.remove(entries.keys.first())
    }
}
