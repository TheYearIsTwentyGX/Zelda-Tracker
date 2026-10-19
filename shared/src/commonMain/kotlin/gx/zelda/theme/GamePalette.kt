package gx.zelda.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The hand-picked colours of one side (light or dark) of a [GameTheme]. [toColorScheme] fills in the
 * rest of Material's roles, so a theme stays a short list of colours from the game's own menus.
 * The contrast every pair needs is checked by `GameThemeContrastTest`.
 */
data class GamePalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    /** Upgrade stars and other gold highlights; needs 3:1 on [surface]. */
    val star: Color,
    /**
     * surfaceContainerLowest, Low, (plain), High and Highest, when the theme picks them itself;
     * otherwise they step from [surface] toward [onSurface].
     */
    val containers: List<Color>? = null,
    val outlineVariant: Color? = null,
    /** Material's error red, unless it's too light for this [surface]. */
    val error: Color? = null,
    val onError: Color? = null,
) {
    init {
        require(containers == null || containers.size == 5) { "containers lists Lowest, Low, plain, High and Highest" }
    }

    /**
     * The full scheme. [other] is the opposite side of the same theme: a snackbar is drawn in its
     * surface, text and action colours, so those pairs pass wherever [other]'s own do.
     */
    fun toColorScheme(dark: Boolean, other: GamePalette): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        // Light containers darken toward the text colour; dark ones lighten, in bigger steps since
        // dark tones are closer together. Lowest goes the other way.
        val step = if (dark) 0.03f else 0.02f
        val steps = containers ?: listOf(
            lerp(surface, if (dark) Color.Black else Color.White, if (dark) 0.35f else 0.6f),
            lerp(surface, onSurface, step),
            lerp(surface, onSurface, step * 2),
            lerp(surface, onSurface, step * 3),
            lerp(surface, onSurface, step * 4),
        )
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = other.primary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = other.surface,
            inverseOnSurface = other.onSurface,
            outline = outline,
            outlineVariant = outlineVariant ?: lerp(surface, outline, 0.45f),
            surfaceBright = lerp(surface, onSurface, if (dark) 0.15f else 0f),
            surfaceDim = lerp(surface, onSurface, if (dark) 0f else 0.1f),
            surfaceContainerLowest = steps[0],
            surfaceContainerLow = steps[1],
            surfaceContainer = steps[2],
            surfaceContainerHigh = steps[3],
            surfaceContainerHighest = steps[4],
            error = error ?: base.error,
            onError = onError ?: base.onError,
        )
    }
}
