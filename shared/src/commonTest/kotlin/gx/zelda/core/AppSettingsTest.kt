package gx.zelda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Which games are shown, and the menu built from them. The coming-soon cases use [comingSoonGame]
 * alongside the real games, so they hold however many games have trackers.
 */
class AppSettingsTest {
    private val games = gamesWithComingSoon
    private val soon = comingSoonGame

    @Test
    fun byDefaultOnlyGamesWithTrackersAreShown() {
        val defaults = AppSettings()
        assertEquals(gamesWithTrackers, defaults.shownGames().map { it.id })
        assertEquals(gamesWithTrackers, defaults.shownGames(games).map { it.id })
        assertTrue(listOf("loz", "mc", "botw", "eow").all { it in gamesWithTrackers })
        assertFalse(defaults.isShown(soon))
        assertTrue(Games.all.filter { it.comingSoon }.none(defaults::isShown))
    }

    @Test
    fun turningOnAComingSoonGameShowsItInReleaseOrder() {
        val settings = AppSettings().withShown(soon, true, games)
        assertTrue(settings.isShown(soon))
        assertEquals(games.filter { !it.comingSoon || it == soon }, settings.shownGames(games))
        // Between the 2015 and 2017 games.
        val ids = settings.shownGames(games).map { it.id }
        assertEquals(ids.indexOf("botw") - 1, ids.indexOf(soon.id))
    }

    @Test
    fun theLastShownGameCannotBeHidden() {
        val one = Games.all.filter { !it.comingSoon && it != Games.MC }.fold(AppSettings()) { s, game -> s.withShown(game, false, games) }
        assertEquals(listOf("mc"), one.shownGames(games).map { it.id })
        assertFalse(one.canHide(Games.MC, games))
        assertSame(one, one.withShown(Games.MC, false, games))
        assertEquals(listOf("mc"), one.withShown(Games.MC, false, games).shownGames(games).map { it.id })

        // With another game on, either can go.
        val two = one.withShown(soon, true, games)
        assertTrue(two.canHide(Games.MC, games))
        assertEquals(listOf(soon.id), two.withShown(Games.MC, false, games).shownGames(games).map { it.id })
    }

    /** The home screen, menu and Settings list the spin-offs after the mainline games, each part in order. */
    @Test
    fun spinOffsAreListedAfterTheMainlineGames() {
        val (mainline, spinOffs) = splitSpinOffs(AppSettings().gameMenu(emptySet(), games)) { it.game }
        assertEquals(listOf("hwde"), spinOffs.map { it.game.id })
        assertTrue(mainline.none { it.game.spinOff })
        assertEquals(gamesWithTrackers - "hwde", mainline.map { it.game.id })
        // Hidden in Settings, it leaves no empty heading behind.
        val hidden = AppSettings().withShown(Games.HWDE, false, games)
        assertTrue(splitSpinOffs(hidden.gameMenu(emptySet(), games)) { it.game }.second.isEmpty())
    }

    /** Stored choices that somehow hide everything fall back to the defaults rather than an empty app. */
    @Test
    fun choicesHidingEveryGameFallBackToTheDefaults() {
        val broken = AppSettings(gameChoices = games.associate { it.id to false })
        assertEquals(gamesWithTrackers, broken.shownGames(games).map { it.id })
        // With no game built out at all, every game shows.
        assertEquals(listOf(soon), broken.shownGames(listOf(soon)))
    }

    @Test
    fun menuListsShownGamesWithTrackersUnderOpenSections() {
        val settings = AppSettings().withShown(soon, true, games)
        val menu = settings.gameMenu(setOf("botw", soon.id), games)
        assertEquals(games.filter { !it.comingSoon || it == soon }.map { it.id }, menu.map { it.game.id })

        val coming = menu.first { it.game == soon }
        assertFalse(coming.expandable)
        assertFalse(coming.expanded, "a coming-soon game never opens")
        assertTrue(coming.rows.isEmpty())

        val botw = menu.first { it.game.id == "botw" }
        assertTrue(botw.expandable && botw.expanded)
        assertEquals(Games.BOTW.trackers, botw.rows)

        val mc = menu.first { it.game.id == "mc" }
        assertTrue(mc.expandable)
        assertFalse(mc.expanded)
        assertTrue(mc.rows.isEmpty())
    }

    @Test
    fun hiddenGamesLeaveTheMenu() {
        val settings = AppSettings().withShown(Games.TOTK, false)
        assertEquals(gamesWithTrackers - "totk", settings.gameMenu(menuOpenedIn("totk")).map { it.game.id })
    }

    /** Each open of the menu shows only the current game's section open. */
    @Test
    fun theMenuOpensWithOnlyTheCurrentGameOpen() {
        val menu = AppSettings().gameMenu(menuOpenedIn("mc"))
        assertEquals(listOf("mc"), menu.filter { it.expanded }.map { it.game.id })
        assertEquals(gamesWithTrackers - "mc", menu.filter { !it.expanded }.map { it.game.id })
    }

    /** On Home and Settings there's no current game, so every section starts closed. */
    @Test
    fun withNoCurrentGameEverySectionStartsClosed() {
        assertTrue(AppSettings().gameMenu(menuOpenedIn(null)).none { it.expanded })
    }

    /** Sections opened and closed by hand while the menu is open; the next open starts over. */
    @Test
    fun expandingAndCollapsingWithinOneOpen() {
        val opened = menuOpenedIn("mc").withExpanded("botw", true).withExpanded("mc", false)
        val menu = AppSettings().gameMenu(opened)
        assertEquals(listOf("botw"), menu.filter { it.expanded }.map { it.game.id })
        assertEquals(setOf("mc"), menuOpenedIn("mc"))
    }
}
