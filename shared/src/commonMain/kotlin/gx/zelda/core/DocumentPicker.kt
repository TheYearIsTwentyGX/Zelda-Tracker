package gx.zelda.core

import androidx.compose.runtime.Composable

/** A file the user picked; [copyTo] writes its bytes to the local file at a path, off the main thread. */
class PickedDocument(val name: String, val copyTo: suspend (path: String) -> Unit)

/**
 * Remembers a launcher for the system file picker, offering files of [mimeTypes]. [onPicked] isn't
 * called when the user backs out.
 */
@Composable
expect fun rememberDocumentPicker(mimeTypes: List<String>, onPicked: (PickedDocument) -> Unit): () -> Unit
