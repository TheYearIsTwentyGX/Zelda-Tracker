package gx.zelda.core

import androidx.compose.runtime.Composable
import gx.zelda.shrines.scan.MapImage

/**
 * A video the user picked. [frames] decodes one frame every [everyMs] milliseconds, at most
 * [maxWidth] px wide, off the main thread, handing each to [onFrame] with how many there will be.
 */
class PickedVideo(
    val name: String,
    val frames: suspend (everyMs: Long, maxWidth: Int, onFrame: suspend (index: Int, total: Int, frame: MapImage) -> Unit) -> Unit,
)

/** Remembers a launcher for the system photo picker, showing videos only. */
@Composable
expect fun rememberVideoPicker(onPicked: (PickedVideo) -> Unit): () -> Unit
