package gx.zelda.adventure.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun CloseOnBack(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
