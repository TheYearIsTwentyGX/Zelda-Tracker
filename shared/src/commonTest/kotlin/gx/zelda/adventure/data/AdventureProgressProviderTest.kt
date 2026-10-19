package gx.zelda.adventure.data

import gx.zelda.adventure.AdventureFixture.map
import gx.zelda.adventure.AdventureFixture.square
import gx.zelda.adventure.model.AdventurePrefs
import gx.zelda.adventure.model.AdventureProgress
import gx.zelda.adventure.model.SquareStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** A map's Progress Overview row: everything done of all, the collectibles, and a spoiler-safe next step. */
class AdventureProgressProviderTest {
    @Test
    fun theRowCountsEveryThingAndNamesTheNextSquare() {
        val p = AdventureProgress()
            .withStatus(square("r1c1"), SquareStatus.A_RANK)
            .withStatus(square("r2c1"), SquareStatus.CLEARED)
            .withDone("r1c1.hc_link", true)
        val row = AdventureProgressProvider.summarize("Test Map", map, p, AdventurePrefs())
        assertEquals("Test Map", row.title)
        assertEquals(2 + 1 + 1, row.done)
        assertEquals(21, row.total)
        assertEquals("Cleared 2/5 · A-rank 1 · Heart Containers 1/2 · Pieces 0/1 · Skulltulas 0/2 · Weapons 0/1", row.secondary)
        assertEquals("A-1: Piece of Heart for Zelda (in North Keep)", row.next)
    }

    @Test
    fun spoilerSafeModeOnlyNamesReachedSquares() {
        val p = AdventureProgress().withAll(listOf(square("r1c1")), complete = true)
        // The start's Hookshot search opens its wall, so the green square next door is reached.
        assertEquals("A-2: clear it (Capture the keeps!)", AdventureProgressProvider.summarize("Test Map", map, p, AdventurePrefs(spoilerSafe = true)).next)
        val none = AdventureProgress()
        assertEquals("A-1: clear it (Defeat 300 enemies in time!)", AdventureProgressProvider.summarize("Test Map", map, none, AdventurePrefs(spoilerSafe = true)).next)
        val all = AdventureProgress().withAll(map.squares, complete = true)
        assertEquals("All done", AdventureProgressProvider.summarize("Test Map", map, all, AdventurePrefs()).next)
    }
}
