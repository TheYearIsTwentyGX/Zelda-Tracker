package gx.zelda.adventure.model

import gx.zelda.adventure.AdventureFixture.map
import gx.zelda.adventure.AdventureFixture.square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Spoiler-safe mode: which squares are reached, what stays hidden, and what the map and next step show. */
class AdventureSpoilersTest {
    private fun cleared(vararg ids: String) = ids.fold(AdventureProgress()) { p, id -> p.withStatus(square(id), SquareStatus.CLEARED) }

    @Test
    fun theFrontierGrowsAcrossOpenSidesOnly() {
        assertEquals(setOf("r1c1"), AdventureSpoilers.explored(map, AdventureProgress()), "only the start at first")
        assertEquals(setOf("r1c1", "r2c1"), AdventureSpoilers.explored(map, cleared("r1c1")), "the wall keeps r1c2 out")
        assertEquals(setOf("r1c1", "r2c1", "r2c2"), AdventureSpoilers.explored(map, cleared("r1c1", "r2c1")))
        // A path card used on the walled square opens its walls too.
        val hooked = cleared("r1c1").withDone("r1c1.search_hookshot", true)
        assertTrue("r1c2" in AdventureSpoilers.explored(map, hooked))
    }

    @Test
    fun hiddenSquaresAndSecretsStayHiddenUntilRevealed() {
        val p = cleared("r1c1")
        val off = AdventureSpoilers(map, p, on = false)
        assertTrue(map.squares.none(off::isHidden))
        assertTrue(map.squares.none(off::secretsHidden))

        val on = AdventureSpoilers(map, p, on = true)
        assertFalse(on.isHidden(square("r1c1")))
        assertTrue(on.isHidden(square("r1c3")))
        assertTrue(on.secretsHidden(square("r1c1")), "reached, but the Compass hasn't been used on it")
        assertEquals(emptyList(), on.visibleSearches(square("r1c1")))

        val revealed = AdventureSpoilers(map, p, on = true, revealed = setOf("r1c3"), compassed = setOf("r1c1"))
        assertFalse(revealed.isHidden(square("r1c3")))
        assertTrue(revealed.secretsHidden(square("r1c3")))
        assertFalse(revealed.secretsHidden(square("r1c1")))
        assertEquals(square("r1c1").searches, revealed.visibleSearches(square("r1c1")))

        val done = AdventureSpoilers(map, p.withDone("r1c1.search_hookshot", true), on = true)
        assertEquals(square("r1c1").searches, done.visibleSearches(square("r1c1")), "a search already done is no secret")
    }

    @Test
    fun aHiddenSquareShowsNothingAboutItself() {
        val p = cleared("r1c1")
        val spoilers = AdventureSpoilers(map, p, on = true)
        val look = SquareLook.of(map, square("r1c3"), p, spoilers, matches = true, selected = false)
        assertTrue(look.hidden)
        assertEquals(emptyList(), look.markers)
        assertFalse(look.finalBoss, "not even that it's the Dark Ruler's")

        val start = SquareLook.of(map, square("r1c1"), p, spoilers, matches = true, selected = true)
        assertTrue(start.start)
        assertTrue(start.selected)
        assertEquals(SquareStatus.CLEARED, start.status)
        assertEquals(
            listOf(SquareMarker(MarkerKind.HEART_CONTAINER, 1), SquareMarker(MarkerKind.HEART_PIECE, 1), SquareMarker(MarkerKind.SKULLTULA, 2)),
            start.markers,
            "its search is still a secret",
        )
        val compassed = SquareLook.of(map, square("r1c1"), p, AdventureSpoilers(map, p, on = true, compassed = setOf("r1c1")), matches = false, selected = false)
        assertTrue(SquareMarker(MarkerKind.SEARCH, 1) in compassed.markers)
        assertTrue(compassed.dimmed)
    }

    @Test
    fun aCompleteSquareHasNothingLeftToShow() {
        val p = AdventureProgress().withAll(listOf(square("r1c3")), complete = true)
        val look = SquareLook.of(map, square("r1c3"), p, AdventureSpoilers(map, p, on = false), matches = true, selected = false)
        assertTrue(look.complete)
        assertEquals(SquareStatus.A_RANK, look.status)
        assertTrue(look.finalBoss)
        assertEquals(emptyList(), look.markers)
    }

    @Test
    fun theNextStepIsTheEasiestSquareWithSomethingLeft() {
        val none = AdventureProgress()
        assertEquals("A-1: clear it (Defeat 300 enemies in time!)", AdventureNext.line(map, none, AdventureSpoilers(map, none, on = false)))
        val p = cleared("r1c1")
        assertEquals("A-1: Heart Container for Link (A rank)", AdventureNext.line(map, p, AdventureSpoilers(map, p, on = false)))
        val ticked = p.withDone("r1c1.hc_link", true).withDone("r1c1.poh_zelda", true)
        assertEquals("A-1: get an A rank", AdventureNext.line(map, ticked, AdventureSpoilers(map, ticked, on = false)))

        // With its secrets hidden, a search is only "a search left".
        val searchLeft = ticked.withStatus(square("r1c1"), SquareStatus.A_RANK).withDone("r1c1.gs1", true).withDone("r1c1.gs2", true)
        assertEquals("A-1: Hookshot search", AdventureNext.line(map, searchLeft, AdventureSpoilers(map, searchLeft, on = false)))
        assertEquals("A-1: a search left", AdventureNext.line(map, searchLeft, AdventureSpoilers(map, searchLeft, on = true)))

        // Spoiler-safe: the walled-off green square isn't named until it's reached. (Here the start's
        // search is a Bomb one, which doesn't open the wall the way the Hookshot does.)
        val walled = map.copy(squares = map.squares.map { if (it.id == "r1c1") it.copy(searches = listOf(Search("r1c1.search_bomb", "bomb", row = 3, col = 3))) else it })
        val r1c1Done = AdventureProgress().withAll(listOf(walled.square("r1c1")!!), complete = true)
        assertEquals("A-2: clear it (Capture the keeps!)", AdventureNext.line(walled, r1c1Done, AdventureSpoilers(walled, r1c1Done, on = false)))
        assertEquals("B-1: clear it (Stop the healers!)", AdventureNext.line(walled, r1c1Done, AdventureSpoilers(walled, r1c1Done, on = true)))

        val all = AdventureProgress().withAll(map.squares, complete = true)
        assertEquals("All done", AdventureNext.line(map, all, AdventureSpoilers(map, all, on = true)))
    }
}
