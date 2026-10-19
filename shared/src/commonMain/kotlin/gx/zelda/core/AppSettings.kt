package gx.zelda.core

import kotlinx.serialization.Serializable

/** App-wide choices, not tied to a game or save file. Stored by [AppSettingsStorage]. */
@Serializable
data class AppSettings(
    /** Games the user has turned on or off in Settings, by id; the rest follow [Game.shownByDefault]. */
    val gameChoices: Map<String, Boolean> = emptyMap(),
    /** The games whose section in the menu is open. */
    val expandedGames: Set<String> = emptySet(),
    /** Light or dark, or whichever the system is set to. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Screens inside a game wear that game's theme; off keeps the app's own look everywhere. */
    val gameThemes: Boolean = true,
) {
    fun isShown(game: Game): Boolean = gameChoices[game.id] ?: game.shownByDefault

    /**
     * The games on the home screen and in the menu, oldest first. Never empty: if the stored
     * choices hide every game (say one was removed from the app), the defaults show instead.
     */
    fun shownGames(games: List<Game> = Games.all): List<Game> =
        games.filter(::isShown).ifEmpty { games.filter { it.shownByDefault } }.ifEmpty { games }

    /** False for the last game shown, which can't be hidden. */
    fun canHide(game: Game, games: List<Game> = Games.all): Boolean =
        isShown(game) && shownGames(games).any { it.id != game.id }

    /** Turns [game] on or off; hiding the last game shown is ignored. */
    fun withShown(game: Game, shown: Boolean, games: List<Game> = Games.all): AppSettings =
        if (!shown && !canHide(game, games)) this else copy(gameChoices = gameChoices + (game.id to shown))

    fun withExpanded(gameId: String, expanded: Boolean): AppSettings =
        copy(expandedGames = if (expanded) expandedGames + gameId else expandedGames - gameId)

    /** The menu's sections: every shown game in release order, open if the user left it open. */
    fun gameMenu(games: List<Game> = Games.all): List<GameMenuSection> =
        shownGames(games).map { GameMenuSection(it, expanded = !it.comingSoon && it.id in expandedGames) }
}

/** The app's light or dark look, chosen in Settings. */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Every game with trackers is shown until turned off; "coming soon" games are hidden until turned on. */
val Game.shownByDefault: Boolean get() = !comingSoon

/**
 * One game in the menu. A game with trackers is a collapsible section listing them; a coming-soon
 * game is a single row that doesn't open.
 */
data class GameMenuSection(val game: Game, val expanded: Boolean) {
    val expandable: Boolean get() = !game.comingSoon

    /** The rows under the game's title: its trackers while open, otherwise none. */
    val rows: List<Tracker> get() = if (expanded) game.trackers else emptyList()
}
