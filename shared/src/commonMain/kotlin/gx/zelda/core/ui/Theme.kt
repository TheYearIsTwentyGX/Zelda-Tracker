package gx.zelda.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import gx.zelda.core.AppSettings
import gx.zelda.core.Game
import gx.zelda.core.ThemeMode
import gx.zelda.theme.Appearance
import gx.zelda.theme.DisplayFont
import gx.zelda.theme.GameTheme
import gx.zelda.theme.GameThemes
import gx.zelda.theme.LocalAppearance
import gx.zelda.theme.LocalGameDecor
import gx.zelda.theme.typography

/**
 * A fixed gold for things that are gold in the game whatever the theme, like a medal. Upgrade stars
 * use the current theme's star colour instead ([LocalGameDecor]), which is checked for contrast.
 */
val StarGold = Color(0xFFE8C15A)

/**
 * [theme]'s colours, header lettering, shapes and decor, light or dark as Settings chose. With game
 * themes turned off in Settings every [theme] shows as [GameThemes.Neutral].
 */
@Composable
fun ZeldaTheme(theme: GameTheme = GameThemes.Neutral, content: @Composable () -> Unit) {
    val appearance = LocalAppearance.current
    val dark = appearance?.dark ?: isSystemInDarkTheme()
    val shown = if (appearance?.gameThemes == false) GameThemes.Neutral else theme
    MaterialTheme(
        colorScheme = shown.colorScheme(dark),
        typography = shown.display.typography(),
        shapes = shown.shapes.shapes,
    ) {
        CompositionLocalProvider(LocalGameDecor provides shown.decor(dark), content = content)
    }
}

/**
 * The whole app: Settings' light/dark choice and game-theme switch for everything inside, which is
 * in the neutral theme until a game's screen puts on its own. [settings] is null while loading:
 * the system's mode, game themes on. [onDarkTheme] hears the resolved mode, for the status bar.
 */
@Composable
fun AppTheme(settings: AppSettings?, onDarkTheme: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val dark = when (settings?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, null -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) { onDarkTheme(dark) }
    CompositionLocalProvider(LocalAppearance provides Appearance(dark, settings?.gameThemes != false)) {
        ZeldaTheme(content = content)
    }
}

/**
 * [game]'s own accent, for its row on the home screen, in the menu and in Settings: its theme's
 * primary colour in the current mode. Null when it has no theme yet or game themes are off.
 */
@Composable
fun gameAccent(game: Game): Color? {
    val appearance = LocalAppearance.current
    if (appearance?.gameThemes == false) return null
    val theme = GameThemes.byId[game.id] ?: return null
    return theme.colorScheme(appearance?.dark ?: isSystemInDarkTheme()).primary
}

/** The lettering [game]'s headers use, or the system font when game themes are off. */
@Composable
fun gameDisplayFont(game: Game): DisplayFont =
    if (LocalAppearance.current?.gameThemes == false) DisplayFont.DEFAULT else GameThemes.of(game.id).display
