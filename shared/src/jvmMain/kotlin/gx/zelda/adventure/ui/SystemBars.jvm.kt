package gx.zelda.adventure.ui

import androidx.compose.runtime.Composable

/** The JVM build has no system bars. */
@Composable
actual fun HideSystemBars(hidden: Boolean) = Unit
