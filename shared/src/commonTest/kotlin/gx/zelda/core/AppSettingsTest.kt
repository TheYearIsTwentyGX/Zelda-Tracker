package gx.zelda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Which games are shown, and the menu built from them. */
class AppSettingsTest {
    @Test
    fun byDefaultOnlyGamesWithTrackersAreShown() {
        val defaults = AppSettings()
        assertEquals(listOf("loz", "aol", "alttp", "la", "oot", "mm", "mc", "ss", "botw", "totk", "eow"), defaults.shownGames().map { it.id })
        assertTrue(Games.all.filter { it.comingSoon }.none(defaults::isShown))
    }

    @Test
    fun turningOnAComingSoonGameShowsItInReleaseOrder() {
        val settings = AppSettings().withShown(Games.TFH, true).withShown(Games.WW, true)
        assertEquals(listOf("loz", "aol", "alttp", "la", "oot", "mm", "ww", "mc", "ss", "tfh", "botw", "totk", "eow"), settings.shownGames().map { it.id })
    }

    @Test
    fun theLastShownGameCannotBeHidden() {
        val one = Games.all.filter { !it.comingSoon && it != Games.MC }.fold(AppSettings()) { s, game -> s.withShown(game, false) }
        assertEquals(listOf("mc"), one.shownGames().map { it.id })
        assertFalse(one.canHide(Games.MC))
        assertSame(one, one.withShown(Games.MC, false))
        assertEquals(listOf("mc"), one.withShown(Games.MC, false).shownGames().map { it.id })

        // With another game on, either can go.
        val two = one.withShown(Games.TFH, true)
        assertTrue(two.canHide(Games.MC))
        assertEquals(listOf("tfh"), two.withShown(Games.MC, false).shownGames().map { it.id })
    }

    /** Stored choices that somehow hide everything fall back to the defaults rather than an empty app. */
    @Test
    fun choicesHidingEveryGameFallBackToTheDefaults() {
        val broken = AppSettings(gameChoices = Games.all.associate { it.id to false })
        assertEquals(listOf("loz", "aol", "alttp", "la", "oot", "mm", "mc", "ss", "botw", "totk", "eow"), broken.shownGames().map { it.id })
    }

    @Test
    fun menuListsShownGamesWithTrackersUnderOpenSections() {
        val settings = AppSettings(expandedGames = setOf("botw", "tfh")).withShown(Games.TFH, true)
        val menu = settings.gameMenu()
        assertEquals(listOf("loz", "aol", "alttp", "la", "oot", "mm", "mc", "ss", "tfh", "botw", "totk", "eow"), menu.map { it.game.id })

        val tfh = menu.first { it.game.id == "tfh" }
        assertFalse(tfh.expandable)
        assertFalse(tfh.expanded, "a coming-soon game never opens, even if stored as open")
        assertTrue(tfh.rows.isEmpty())

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
        val settings = AppSettings(expandedGames = setOf("totk")).withShown(Games.TOTK, false)
        assertEquals(listOf("loz", "aol", "alttp", "la", "oot", "mm", "mc", "ss", "botw", "eow"), settings.gameMenu().map { it.game.id })
        // The open state is kept for when it's shown again.
        assertTrue(settings.withShown(Games.TOTK, true).gameMenu().first { it.game.id == "totk" }.expanded)
    }

    @Test
    fun expandingAndCollapsing() {
        val open = AppSettings().withExpanded("mc", true)
        assertEquals(setOf("mc"), open.expandedGames)
        assertEquals(emptySet(), open.withExpanded("mc", false).expandedGames)
    }
}
