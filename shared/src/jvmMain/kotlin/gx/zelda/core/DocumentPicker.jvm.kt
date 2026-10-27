package gx.zelda.core

import androidx.compose.runtime.Composable

/** The JVM target only exists to run tests; there's no desktop app to pick files in. */
@Composable
actual fun rememberDocumentPicker(mimeTypes: List<String>, onPicked: (PickedDocument) -> Unit): () -> Unit = {}
