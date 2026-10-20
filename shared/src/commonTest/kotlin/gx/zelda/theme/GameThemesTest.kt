package gx.zelda.theme

import gx.zelda.core.Games
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameThemesTest {
    @Test
    fun everyGameHasATheme() {
        for (game in Games.all) assertEquals(GameThemes.byId[game.id] ?: GameThemes.Neutral, GameThemes.of(game.id), game.id)
    }

    @Test
    fun theThemedGamesHaveTheirOwn() {
        for (id in listOf("loz", "aol", "alttp", "la", "oot", "mm", "oos", "ooa", "fs", "ww", "fsa", "mc", "tp", "ph", "st", "ss", "albw", "tfh", "botw", "totk", "eow", "hwde")) assertEquals(id, GameThemes.of(id).id)
    }

    /** Every game, spin-offs included, has a theme of its own; Neutral is only for screens outside a game. */
    @Test
    fun everyGameHasItsOwnTheme() {
        for (game in Games.all) assertTrue(game.id in GameThemes.byId, "${game.id} has no theme")
    }

    @Test
    fun noGameAndUnknownGamesFallBackToNeutral() {
        assertSame(GameThemes.Neutral, GameThemes.of(null))
        assertSame(GameThemes.Neutral, GameThemes.of("no_such_game"))
    }

    @Test
    fun everyThemeBelongsToAGame() {
        val ids = Games.all.map { it.id }.toSet() + "hwde"
        for (id in GameThemes.byId.keys) assertTrue(id in ids, "$id is not a game")
        for ((id, theme) in GameThemes.byId) assertEquals(id, theme.id)
    }

    /** The neutral dark scheme is the app's original one. */
    @Test
    fun neutralDarkIsTheOriginalScheme() {
        val dark = GameThemes.Neutral.colorScheme(dark = true)
        assertEquals(0xFF5FD4EA.toInt(), dark.primary.toArgbInt())
        assertEquals(0xFF0B1619.toInt(), dark.surface.toArgbInt())
        assertEquals(0xFF152427.toInt(), dark.surfaceContainer.toArgbInt())
        assertEquals(0xFF34474B.toInt(), dark.outlineVariant.toArgbInt())
        assertEquals(DisplayFont.DEFAULT, GameThemes.Neutral.display)
        assertEquals(Ornament.NONE, GameThemes.Neutral.ornament)
        assertEquals(ProgressStyle.SOLID, GameThemes.Neutral.progress)
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        ((alpha * 255 + 0.5f).toInt() shl 24) or ((red * 255 + 0.5f).toInt() shl 16) or
            ((green * 255 + 0.5f).toInt() shl 8) or (blue * 255 + 0.5f).toInt()
}
