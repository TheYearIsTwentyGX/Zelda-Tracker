package gx.zelda.core

import androidx.compose.runtime.Composable

/** The JVM target only exists to run tests; there's no desktop app to pick images in. */
@Composable
actual fun rememberImagesPicker(onPicked: (List<PickedImage>) -> Unit): () -> Unit = {}
