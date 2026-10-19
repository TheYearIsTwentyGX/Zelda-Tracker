package gx.zelda.adventure.ui

import androidx.compose.runtime.Composable

/** While [enabled], the system back gesture calls [onBack] instead of leaving the screen. */
@Composable
expect fun CloseOnBack(enabled: Boolean, onBack: () -> Unit)
