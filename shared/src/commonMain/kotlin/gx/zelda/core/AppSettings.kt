package gx.zelda.core

import kotlinx.serialization.Serializable

/** App-wide choices, not tied to a game or save file. Stored by [AppSettingsStorage]. */
@Serializable
data class AppSettings(
    /** Games the user has turned on or off in Settings, by id; the rest follow [Game.shownByDefault]. */
    val gameChoices: Map<String, Boolean> = emptyMap(),
    /** Light or dark, or whichever the system is set to. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Screens inside a game wear that game's theme; off keeps the app's own look everywhere. */
    val gameThemes: Boolean = true,
    /** Checklists hide what the game's Main Quest hasn't unlocked yet on the active save file. */
    val storyGating: Boolean = false,
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

    /**
     * The menu's sections: every shown game in release order, open if its id is in [expanded]. Which
     * sections are open isn't stored; see [menuOpenedIn].
     */
    fun gameMenu(expanded: Set<String>, games: List<Game> = Games.all): List<GameMenuSection> =
        shownGames(games).map { GameMenuSection(it, expanded = !it.comingSoon && it.id in expanded) }
}

/**
 * The sections open each time the menu opens: only the current game's, or none on Home and
 * Settings. Sections opened or closed by hand last until the menu closes.
 */
fun menuOpenedIn(currentGameId: String?): Set<String> = setOfNotNull(currentGameId)

/** [this] with [gameId]'s section opened or closed. */
fun Set<String>.withExpanded(gameId: String, expanded: Boolean): Set<String> = if (expanded) this + gameId else this - gameId

/**
 * [items] as the home screen, the menu and Settings list them: the mainline games, then the
 * spin-offs under their own heading, each part in [items]' order.
 */
fun <T> splitSpinOffs(items: List<T>, gameOf: (T) -> Game): Pair<List<T>, List<T>> = items.partition { !gameOf(it).spinOff }

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
