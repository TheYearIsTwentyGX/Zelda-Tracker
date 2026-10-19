package gx.zelda.theme

import gx.zelda.core.Games
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bundled header fonts load, cover what they're asked to show, and stay inside their budget. */
@OptIn(ExperimentalResourceApi::class)
class GameFontsTest {
    private val bundled = DisplayFont.entries.filter { it.resource != null }

    private suspend fun bytes(font: DisplayFont): ByteArray = getFontResourceBytes(getSystemResourceEnvironment(), font.resource!!)

    @Test
    fun everyFontLoadsAsTrueType() = runTest {
        for (font in bundled) {
            val data = bytes(font)
            val awt = Font.createFont(Font.TRUETYPE_FONT, data.inputStream())
            assertTrue(awt.numGlyphs > 90, "$font has ${awt.numGlyphs} glyphs")
        }
    }

    /** Any game may get any font, so every font covers every game's and tracker's title. */
    @Test
    fun everyFontCoversTheTitles() = runTest {
        val text = (Games.all.flatMap { game -> listOf(game.title) + game.trackers.map { it.title } } + "Hyrule Warriors: Definitive Edition é ü ’ × 0123456789 / %")
            .joinToString(" ")
        for (font in bundled) {
            val awt = Font.createFont(Font.TRUETYPE_FONT, bytes(font).inputStream())
            val missing = text.filterNot { it.isWhitespace() || awt.canDisplay(it) }.toSet()
            assertTrue(missing.isEmpty(), "$font can't show ${missing.joinToString("")}")
        }
    }

    /** The plan's budget for theme fonts, and no font file is bundled without a theme using it. */
    @Test
    fun fontsStayWithinBudget() = runTest {
        val total = bundled.sumOf { bytes(it).size }
        assertTrue(total <= 400 * 1024, "fonts take ${total / 1024} KB")
        val files = File("src/commonMain/composeResources/font").listFiles().orEmpty().filter { it.extension == "ttf" }
        assertEquals(bundled.size, files.size, "font files: ${files.map { it.name }}")
        assertEquals(files.sumOf { it.length().toInt() }, total)
    }
}
