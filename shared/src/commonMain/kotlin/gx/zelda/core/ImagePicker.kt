package gx.zelda.core

import androidx.compose.runtime.Composable

/** An image the user picked; [read] loads its bytes off the main thread. */
class PickedImage(val name: String, val read: suspend () -> ByteArray)

/**
 * Remembers a launcher for the system photo picker, allowing several images. [onPicked] isn't
 * called when the user backs out without choosing any.
 */
@Composable
expect fun rememberImagesPicker(onPicked: (List<PickedImage>) -> Unit): () -> Unit
