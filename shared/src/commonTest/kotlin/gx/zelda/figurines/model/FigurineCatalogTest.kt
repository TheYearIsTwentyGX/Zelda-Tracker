package gx.zelda.figurines.model

import gx.zelda.figurines.data.FigurineProgressProvider
import gx.zelda.figurines.ui.oddsLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FigurineCatalogTest {
    private val catalog = FigurineCatalog(
        categories = listOf(FigurineCategory("characters", "Characters", "Blue"), FigurineCategory("bosses", "Bosses", "Gold")),
        checkpoints = listOf(
            FigurineCheckpoint("start", "Start", ""),
            FigurineCheckpoint("middle", "Middle", "Reach the middle"),
            FigurineCheckpoint("end", "End", "Reach the end", finalSeries = true),
        ),
        figurines = listOf(
            Figurine(1, "One", "characters", "start", "…"),
            Figurine(2, "Two", "characters", "start", "…", requirement = "Fuse Kinstones"),
            Figurine(3, "Three", "bosses", "middle", "…"),
            Figurine(4, "Four", "bosses", "end", "…"),
        ),
    )

    private fun available(progress: FigurineProgress) = catalog.figurines.filter { catalog.isAvailable(it, progress) }.map { it.number }

    @Test
    fun noCheckpointSetMeansTheFirst() {
        assertEquals("start", catalog.reached(FigurineProgress()).id)
        assertEquals(listOf(1), available(FigurineProgress()))
    }

    @Test
    fun sideTasksAndCheckpointsBothCount() {
        assertEquals(listOf(1, 2), available(FigurineProgress(requirementsMet = setOf(2))))
        assertEquals(listOf(1, 3), available(FigurineProgress(checkpoint = "middle")))
        assertEquals(listOf(1, 2, 3, 4), available(FigurineProgress(checkpoint = "end", requirementsMet = setOf(2))))
    }

    @Test
    fun ownedIsAlwaysAvailable() {
        assertEquals(listOf(1, 4), available(FigurineProgress(owned = setOf(4))))
    }

    @Test
    fun tallyCountsCategoriesAndThePool() {
        val tally = catalog.tally(FigurineProgress(owned = setOf(1, 3), checkpoint = "middle"))
        assertEquals(2, tally.owned)
        assertEquals(4, tally.total)
        assertEquals(2, tally.available)
        assertEquals(listOf(1 to 2, 1 to 2), tally.categories.map { it.owned to it.total })
        assertTrue(tally.odds.complete)
    }

    private fun row(progress: FigurineProgress, hide: Boolean = false) =
        FigurineProgressProvider.summarize(catalog, progress, FigurinePrefs(hideUnavailable = hide))

    @Test
    fun overviewRowFollowsTheSpoilerSetting() {
        val one = FigurineProgress(owned = setOf(1))
        val row = row(one)
        assertEquals(1, row.done)
        assertEquals(4, row.total)
        assertEquals("0 shells · Characters 1 / 2 · Bosses 0 / 2", row.secondary)
        // Nothing to win now, but #2's side task would open one; hidden, it's only counted.
        assertEquals("#002 needs a side task: Fuse Kinstones", row.next)
        assertEquals("1 figurine waiting on a side task", row(one, hide = true).next)

        // With the side task done too, the next checkpoint is named only while spoilers are shown.
        val start = FigurineProgress(owned = setOf(1, 2), requirementsMet = setOf(2))
        assertEquals("Nothing new at Carlov's until the story moves on: Reach the middle", row(start).next)
        assertEquals("Nothing new at Carlov's until the story moves on", row(start, hide = true).next)

        val middle = FigurineProgress(checkpoint = "middle")
        assertEquals("2 figurines to win now: find Mysterious Shells to draw", row(middle).next)
        assertEquals("2 figurines to win now · 100% a draw with one shell", row(middle.copy(shells = 3)).next)
        assertEquals("3 shells · Characters 0 / 2 · Bosses 0 / 2", row(middle.copy(shells = 3)).secondary)

        assertEquals("Every figurine won", row(FigurineProgress(owned = setOf(1, 2, 3, 4)), hide = true).next)
    }

    @Test
    fun oddsLinesExplainTheHiddenMinimum() {
        val tally = FigurineTally(owned = 45, total = 136, available = 48, categories = emptyList())
        val lines = oddsLines(tally)
        assertEquals("Next draw: 15% with one shell. Carlov shows 6%, but the game never rolls below 15% at 45 figurines owned.", lines[0])
        assertFalse(oddsLines(tally.copy(owned = 136, available = 136)).any { "%" in it })
    }
}
