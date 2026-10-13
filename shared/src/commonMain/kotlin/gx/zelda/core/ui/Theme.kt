package gx.zelda.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Sheikah-slate blue on deep teal, with Triforce gold for highlights. */
private val HyruleDark = darkColorScheme(
    primary = Color(0xFF5FD4EA),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0F4A55),
    onPrimaryContainer = Color(0xFFB3EDF8),
    secondary = Color(0xFFE8C15A),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF574500),
    onSecondaryContainer = Color(0xFFFFE08E),
    tertiary = Color(0xFF9CD67F),
    onTertiary = Color(0xFF113800),
    tertiaryContainer = Color(0xFF285112),
    onTertiaryContainer = Color(0xFFB7F399),
    background = Color(0xFF0B1619),
    onBackground = Color(0xFFDCE6E8),
    surface = Color(0xFF0B1619),
    onSurface = Color(0xFFDCE6E8),
    surfaceVariant = Color(0xFF213236),
    onSurfaceVariant = Color(0xFFB5C7CB),
    surfaceContainerLowest = Color(0xFF071012),
    surfaceContainerLow = Color(0xFF111E21),
    surfaceContainer = Color(0xFF152427),
    surfaceContainerHigh = Color(0xFF1C2D31),
    surfaceContainerHighest = Color(0xFF24373B),
    outline = Color(0xFF7F9195),
    outlineVariant = Color(0xFF34474B),
)

/** Gold used for upgrade stars. */
val StarGold = Color(0xFFE8C15A)

@Composable
fun ZeldaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HyruleDark, content = content)
}
