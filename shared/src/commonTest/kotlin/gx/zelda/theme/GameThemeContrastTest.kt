package gx.zelda.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * WCAG 2.1 AA for every theme, light and dark: 4.5:1 for text against what it's drawn on, 3:1 for
 * the parts of controls you need to see (outlines, progress, stars).
 */
class GameThemeContrastTest {
    private val themes = GameThemes.byId.values + GameThemes.Neutral

    @Test
    fun everyThemeMeetsAA() {
        val failures = themes.flatMap { theme ->
            listOf(false, true).flatMap { dark -> failures(theme, dark) }
        }
        if (failures.isNotEmpty()) fail("Contrast below WCAG AA:\n" + failures.joinToString("\n"))
    }

    @Test
    fun theRatioMatchesKnownValues() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Color.Red, Color.Red), 0.01)
        // #767676 is the lightest grey with 4.5:1 on white.
        assertEquals(4.54, contrast(Color(0xFF767676), Color.White), 0.01)
    }

    @Test
    fun lightAndDarkAreLightAndDark() {
        for (theme in themes) {
            assertTrue(luminance(theme.colorScheme(dark = false).surface) > 0.5, "${theme.id} light surface is light")
            assertTrue(luminance(theme.colorScheme(dark = true).surface) < 0.05, "${theme.id} dark surface is dark")
        }
    }

    private fun failures(theme: GameTheme, dark: Boolean): List<String> {
        val c = theme.colorScheme(dark)
        val star = theme.palette(dark).star
        val surfaces = c.surfaces()
        val checks = buildList {
            fun text(name: String, fg: Color, bg: Color) = add(Check(name, fg, bg, 4.5))
            fun part(name: String, fg: Color, bg: Color) = add(Check(name, fg, bg, 3.0))
            text("onPrimary/primary", c.onPrimary, c.primary)
            text("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer)
            text("onSecondary/secondary", c.onSecondary, c.secondary)
            text("onSecondaryContainer/secondaryContainer", c.onSecondaryContainer, c.secondaryContainer)
            text("onTertiary/tertiary", c.onTertiary, c.tertiary)
            text("onTertiaryContainer/tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer)
            text("onError/error", c.onError, c.error)
            text("onErrorContainer/errorContainer", c.onErrorContainer, c.errorContainer)
            text("onBackground/background", c.onBackground, c.background)
            text("inverseOnSurface/inverseSurface", c.inverseOnSurface, c.inverseSurface)
            text("inversePrimary/inverseSurface", c.inversePrimary, c.inverseSurface)
            for ((name, bg) in surfaces) {
                text("onSurface/$name", c.onSurface, bg)
                text("onSurfaceVariant/$name", c.onSurfaceVariant, bg)
                text("primary/$name", c.primary, bg)
                text("error/$name", c.error, bg)
            }
            // "Fully upgraded." is set in the star colour.
            text("star/surface", star, c.surface)
            for ((name, bg) in surfaces) part("star/$name", star, bg)
            part("outline/surface", c.outline, c.surface)
            part("outline/surfaceContainer", c.outline, c.surfaceContainer)
            // A segmented bar's filled blocks against its empty ones.
            part("primary/outlineVariant", c.primary, c.outlineVariant)
        }
        return checks.mapNotNull { check ->
            val ratio = contrast(check.fg, check.bg)
            if (ratio + 1e-9 >= check.min) null else "${theme.id} ${if (dark) "dark" else "light"}: ${check.name} = ${ratio.fmt()} (needs ${check.min})"
        }
    }

    private data class Check(val name: String, val fg: Color, val bg: Color, val min: Double)

    private fun ColorScheme.surfaces() = listOf(
        "surface" to surface,
        "surfaceVariant" to surfaceVariant,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    private fun Double.fmt() = (kotlin.math.round(this * 100) / 100).toString()
}

/** WCAG 2.1 contrast ratio, 1 to 21. */
internal fun contrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** WCAG 2.1 relative luminance of an opaque sRGB colour. */
internal fun luminance(c: Color): Double {
    fun channel(v: Float): Double = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}
