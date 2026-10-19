package gx.zelda.adventure.ui

import androidx.compose.runtime.Composable

/** The JVM build has no back gesture. */
@Composable
actual fun CloseOnBack(enabled: Boolean, onBack: () -> Unit) = Unit
