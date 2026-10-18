package gx.zelda.core

import androidx.compose.runtime.Composable

/** The JVM target only exists to run tests; there's no desktop app to pick videos in. */
@Composable
actual fun rememberVideoPicker(onPicked: (PickedVideo) -> Unit): () -> Unit = {}
