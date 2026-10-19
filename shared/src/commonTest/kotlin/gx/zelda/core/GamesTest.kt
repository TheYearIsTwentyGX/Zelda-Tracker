package gx.zelda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every mainline game, in the order it came out. */
class GamesTest {
    @Test
    fun gamesAreInReleaseOrder() {
        assertEquals(
            listOf(
                "loz", "aol", "alttp", "la", "oot", "mm", "oos", "ooa", "fs", "ww",
                "fsa", "mc", "tp", "ph", "st", "ss", "albw", "tfh", "botw", "totk", "eow",
            ),
            Games.all.map { it.id },
        )
        assertEquals(Games.all.sortedBy { it.released }, Games.all)
    }

    /** Same-year games order by the full date, and same-day ones by their tiebreak. */
    @Test
    fun gamesFromTheSameYearOrderByDate() {
        assertTrue(Games.FSA.released < Games.MC.released)
        assertEquals(Games.FSA.year, Games.MC.year)
        assertTrue(Games.FS.released < Games.WW.released)
        assertEquals(Games.OOS.released.copy(sameDay = 0), Games.OOA.released.copy(sameDay = 0))
        assertTrue(Games.OOS.released < Games.OOA.released)
        assertTrue(ReleaseDate(2004, 3, 18) < ReleaseDate(2004, 11, 4))
        assertTrue(ReleaseDate(2004, 12, 1) < ReleaseDate(2005, 1, 1))
    }

    /** A game added anywhere in the list still lands in release order. */
    @Test
    fun aNewGameSlotsInByItsDate() {
        val newGame = Game("new", "New Game", ReleaseDate(2010, 1, 1), "Test", listOf(SaveFile("file_1", "File 1")))
        val sorted = (listOf(newGame) + Games.all).sortedBy { it.released }
        assertEquals("st", sorted[sorted.indexOf(newGame) - 1].id)
        assertEquals("ss", sorted[sorted.indexOf(newGame) + 1].id)
    }

    @Test
    fun releaseYearsMatchTheOriginals() {
        val years = mapOf(
            "loz" to 1986, "aol" to 1987, "alttp" to 1991, "la" to 1993, "oot" to 1998, "mm" to 2000,
            "oos" to 2001, "ooa" to 2001, "fs" to 2002, "ww" to 2002, "fsa" to 2004, "mc" to 2004,
            "tp" to 2006, "ph" to 2007, "st" to 2009, "ss" to 2011, "albw" to 2013, "tfh" to 2015,
            "botw" to 2017, "totk" to 2023, "eow" to 2024,
        )
        assertEquals(years, Games.all.associate { it.id to it.year })
    }

    @Test
    fun idsAreUniqueAndEveryGameHasASaveFile() {
        assertEquals(Games.all.size, Games.all.map { it.id }.toSet().size)
        Games.all.forEach { game ->
            assertTrue(game.saveFiles.isNotEmpty(), game.id)
            assertTrue(game.platform.isNotBlank(), game.id)
            assertEquals(game.saveFiles.size, game.saveFiles.map { it.id }.toSet().size, game.id)
            assertEquals(game, Games.byId(game.id))
        }
    }

    /** These games have trackers; the rest are coming soon with placeholder files. */
    @Test
    fun theNewGamesAreComingSoon() {
        assertEquals(listOf("loz", "aol", "alttp", "la", "oot", "mm", "oos", "ooa", "fs", "ww", "fsa", "mc", "tp", "tfh", "botw", "totk", "eow"), gamesWithTrackers)
        Games.all.forEach { assertEquals(it.trackers.isEmpty(), it.comingSoon, it.id) }
        assertTrue(comingSoonGame.comingSoon)
        assertFalse(comingSoonGame.shownByDefault)
        assertEquals(listOf("file_1", "file_2"), Games.PH.saveFiles.map { it.id })
    }

    @Test
    fun existingGamesKeepTheirSaveFiles() {
        assertEquals(listOf("normal_1", "normal_2", "master"), Games.BOTW.saveFiles.map { it.id })
        assertEquals(listOf("file_1", "file_2"), Games.TOTK.saveFiles.map { it.id })
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.MC.saveFiles.map { it.id })
        // The N64's two files; they were the placeholder's ids, so nothing saved under them moves.
        assertEquals(listOf("file_1", "file_2"), Games.MM.saveFiles.map { it.id })
    }

    /** Three files on the SNES cartridge (docs/alttp.md); every tracker after the overview is a checklist. */
    @Test
    fun aLinkToThePastHasThreeSaveFilesAndAnOverviewFirst() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.ALTTP.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.ALTTP.trackers.first().kind)
        assertTrue(Games.ALTTP.trackers.drop(1).all { it.kind == TrackerKind.CHECKLIST })
    }

    /** A Link to the Past's three GBA files, or the Anniversary Edition's own three (docs/fs.md). */
    @Test
    fun fourSwordsHasThreeSaveFilesAndAnOverviewFirst() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.FS.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.FS.trackers.first().kind)
        assertTrue(Games.FS.trackers.drop(1).all { it.kind == TrackerKind.CHECKLIST })
    }

    /** Three memory-card files, "Quest Log 1-3" in the manual (docs/fsa.md). */
    @Test
    fun fourSwordsAdventuresHasThreeSaveFilesAndAnOverviewFirst() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.FSA.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.FSA.trackers.first().kind)
        assertTrue(Games.FSA.trackers.drop(1).all { it.kind == TrackerKind.CHECKLIST })
    }

    /**
     * One save file (Zelda Wiki, Saving: the only mainline game without a choice of files). It keeps
     * the placeholder's first id, so nothing stored under `file_1` moves.
     */
    @Test
    fun triForceHeroesHasOneSaveFileAndAnOverviewFirst() {
        assertEquals(listOf("file_1"), Games.TFH.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.TFH.trackers.first().kind)
        assertTrue(Games.TFH.trackers.drop(1).all { it.kind == TrackerKind.CHECKLIST })
    }

    /** Two files per Switch user (Zelda Wiki, Saving; Game8); autosaves live inside each file. */
    @Test
    fun echoesOfWisdomHasTwoSaveFilesAndAnOverviewFirst() {
        assertEquals(listOf("file_1", "file_2"), Games.EOW.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.EOW.trackers.first().kind)
        assertTrue(Games.EOW.trackers.drop(1).all { it.kind == TrackerKind.CHECKLIST })
    }
}
