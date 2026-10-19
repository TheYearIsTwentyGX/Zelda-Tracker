package gx.zelda.adventure.model

import gx.zelda.adventure.AdventureFixture.map
import gx.zelda.adventure.AdventureFixture.square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The lines between squares and which squares can be challenged, on the fixture's walled start. */
class AdventureReachTest {
    private fun cleared(vararg ids: String) = ids.fold(AdventureProgress()) { p, id -> p.withStatus(square(id), SquareStatus.CLEARED) }

    @Test
    fun eachEdgeHasAStyle() {
        val none = AdventureProgress()
        assertEquals(EdgeStyle.WALL, AdventureReach.edge(map, none, square("r1c1"), Side.EAST), "the start's wall")
        assertEquals(EdgeStyle.WALL, AdventureReach.edge(map, none, square("r1c2"), Side.WEST), "the same wall from the other side")
        assertEquals(EdgeStyle.OPEN, AdventureReach.edge(map, none, square("r1c1"), Side.SOUTH))
        assertNull(AdventureReach.edge(map, none, square("r1c1"), Side.NORTH), "the map's border")
        assertNull(AdventureReach.edge(map, none, square("r1c3"), Side.SOUTH), "a blank square")
        // A path card used on the start opens its wall: it's drawn straight.
        val hooked = none.withDone("r1c1.search_hookshot", true)
        assertEquals(EdgeStyle.OPENED, AdventureReach.edge(map, hooked, square("r1c1"), Side.EAST))
        assertEquals(EdgeStyle.OPENED, AdventureReach.edge(map, hooked, square("r1c2"), Side.WEST))
        // A search that doesn't open paths leaves the wall as it is.
        val bombed = none.withDone("r1c2.search_bomb", true)
        assertEquals(EdgeStyle.WALL, AdventureReach.edge(map, bombed, square("r1c2"), Side.WEST))
    }

    @Test
    fun everyEdgeIsListedOnce() {
        val edges = AdventureReach.edges(map, AdventureProgress())
        assertEquals(
            listOf("r1c1 EAST r1c2 WALL", "r1c1 SOUTH r2c1 OPEN", "r1c2 EAST r1c3 OPEN", "r1c2 SOUTH r2c2 OPEN", "r2c1 EAST r2c2 OPEN"),
            edges.map { "${it.square.id} ${it.side} ${it.neighbour.id} ${it.style}" },
        )
    }

    @Test
    fun aWallStopsTheWayAndAPathSearchOpensIt() {
        assertEquals(setOf("r1c1"), AdventureReach.challengeable(map, AdventureProgress()), "only the start")
        assertEquals(setOf("r1c1", "r2c1"), AdventureReach.challengeable(map, cleared("r1c1")), "not across the wall")
        val hooked = cleared("r1c1").withDone("r1c1.search_hookshot", true)
        assertEquals(setOf("r1c1", "r2c1", "r1c2"), AdventureReach.challengeable(map, hooked))
    }

    @Test
    fun aClearedSquareIsChallengeableWhereverItIs() {
        // Cleared without a path to it (marked by hand, or reached a way the data doesn't know).
        val p = cleared("r1c3")
        assertEquals(setOf("r1c1", "r1c3", "r1c2"), AdventureReach.challengeable(map, p), "and it opens its own neighbours")
    }

    /** A wall recorded on one square only still blocks both ways (the converter records walls on both). */
    @Test
    fun aOneSidedWallBlocksBothWays() {
        val oneSided = map.copy(squares = map.squares.map { if (it.id == "r1c2") it.copy(blockades = emptyList()) else it })
        assertEquals(EdgeStyle.WALL, AdventureReach.edge(oneSided, AdventureProgress(), oneSided.square("r1c2")!!, Side.WEST))
        val from = cleared("r1c2")
        assertTrue("r1c1" !in AdventureReach.challengeable(oneSided.copy(start = "r2c2"), from), "r1c2 doesn't open the start across it")
    }

    /** Grand Travels: two starts, and a square that opens when two others are cleared. */
    @Test
    fun otherStartsAndGatesOpenSquares() {
        val two = map.copy(otherStarts = listOf("r2c2"), gates = listOf(MapGate("r1c3", afterCleared = listOf("r2c1", "r2c2"))))
        assertEquals(listOf("r1c1", "r2c2"), two.starts)
        assertEquals(setOf("r1c1", "r2c2"), AdventureReach.challengeable(two, AdventureProgress()))
        assertTrue("r1c3" !in AdventureReach.challengeable(two, cleared("r2c1")), "the gate waits for both")
        // r2c2 cleared opens its neighbours too (r1c2 above it, r2c1 beside it); the gate opens r1c3.
        assertEquals(setOf("r1c1", "r2c2", "r2c1", "r1c2", "r1c3"), AdventureReach.challengeable(two, cleared("r2c1", "r2c2")))
    }

    /** Lorule: a Fissure search opens a square elsewhere, and doesn't open the square's own walls. */
    @Test
    fun aSearchThatOpensElsewhereOpensThatSquareOnly() {
        val fissure = map.copy(squares = map.squares.map {
            if (it.id == "r1c1") it.copy(searches = listOf(Search("r1c1.search_hookshot", "hookshot", row = 0, col = 8, opens = listOf("r2c2")))) else it
        })
        val done = AdventureProgress().withDone("r1c1.search_hookshot", true)
        assertTrue("r2c2" in AdventureReach.challengeable(fissure, done))
        assertEquals(EdgeStyle.WALL, AdventureReach.edge(fissure, done, fissure.square("r1c1")!!, Side.EAST), "the wall stays")
    }

    /** Koholint: a Nightmare seals a square until its own Instrument search is done, or the Egg's. */
    @Test
    fun aSealedSquareIsReachedButNotChallengeableUntilItsSearchIsDone() {
        // B-1 under a Nightmare that its own Bomb search banishes, and so does A-2's (the Egg's).
        val sealed = map.copy(squares = map.squares.map {
            when (it.id) {
                "r1c1" -> it.copy(searches = emptyList())
                "r2c1" -> it.copy(searches = listOf(Search("r2c1.search_bomb", "bomb", wholeSquare = true)), sealedBy = listOf("r2c1.search_bomb", "r1c2.search_bomb"))
                else -> it
            }
        })
        assertEquals(emptyList(), sealed.problems())
        val p = cleared("r1c1")
        assertEquals(setOf("r1c1", "r2c1"), AdventureReach.reached(sealed, p))
        assertEquals(setOf("r1c1"), AdventureReach.challengeable(sealed, p), "the Nightmare is still there")
        assertEquals(setOf("r1c1", "r2c1"), AdventureReach.challengeable(sealed, p.withDone("r2c1.search_bomb", true)))
        assertEquals(setOf("r1c1", "r2c1"), AdventureReach.challengeable(sealed, p.withDone("r1c2.search_bomb", true)), "the Egg's lifts them all")
        assertTrue("r2c1" in AdventureReach.challengeable(sealed, cleared("r1c1", "r2c1")), "a cleared square is never sealed")

        // Reached, so not Unexplored, but drawn dim; the seal's search shows before the Compass does.
        val b1 = sealed.square("r2c1")!!
        val spoilers = AdventureSpoilers(sealed, p, on = true)
        val look = SquareLook.of(sealed, b1, p, spoilers, matches = true, selected = false)
        assertTrue(!look.hidden && !look.challengeable)
        assertEquals(SquareMarker(MarkerKind.SEARCH, 1), look.markers.single { it.kind == MarkerKind.SEARCH })
        assertEquals(listOf("r2c1.search_bomb"), spoilers.visibleSearches(b1).map { it.id })

        // The next step lifts the seal first. Without the card, B-1 can't be acted on, so it waits.
        val a1 = AdventureProgress().withAll(listOf(sealed.square("r1c1")!!), complete = true)
        assertEquals("B-1: Bomb search, then clear it", AdventureNext.line(sealed, a1, AdventureSpoilers(sealed, a1, on = false)))
        val noBomb = a1.withCard("bomb", false)
        assertEquals("A-2: clear it (Capture the keeps!)", AdventureNext.line(sealed, noBomb, AdventureSpoilers(sealed, noBomb, on = false)))
        assertTrue(sealed.copy(squares = sealed.squares.map { if (it.id == "r2c1") it.copy(sealedBy = listOf("r1c2.search_bomb")) else it }).problems()
            .any { "none of its own searches" in it })
    }

    @Test
    fun aMapWithNoStartIsOpenEverywhere() {
        assertEquals(map.squares.map { it.id }.toSet(), AdventureReach.challengeable(map.copy(start = null), AdventureProgress()))
    }

    @Test
    fun squaresThatCantBeChallengedAreDrawnDim() {
        val p = cleared("r1c1")
        val spoilers = AdventureSpoilers(map, p, on = false)
        assertTrue(SquareLook.of(map, square("r2c1"), p, spoilers, matches = true, selected = false).challengeable)
        val walled = SquareLook.of(map, square("r1c2"), p, spoilers, matches = true, selected = false)
        assertTrue(!walled.challengeable && !walled.hidden, "dim, but with spoiler-safe mode off nothing is hidden")
        assertTrue(walled.markers.isNotEmpty())
    }

    @Test
    fun theNextStepPrefersASquareThatCanBeChallenged() {
        // A-1 is done but for its Hookshot search; A-2 is behind the wall, B-1 is open.
        val p = AdventureProgress().withAll(listOf(square("r1c1")), complete = true).withDone("r1c1.search_hookshot", false)
        assertEquals("A-1: Hookshot search", AdventureNext.line(map, p, AdventureSpoilers(map, p, on = false)))
        val done = p.withDone("r1c1.search_hookshot", true).withStatus(square("r2c1"), SquareStatus.NONE)
        // With the wall open both A-2 (green) and B-1 (red) can be played: the easier colour first.
        assertEquals("A-2: clear it (Capture the keeps!)", AdventureNext.line(map, done, AdventureSpoilers(map, done, on = false)))
        // Without the Hookshot, A-1 complete leaves A-2 behind its wall: B-1, open, comes before it,
        // though A-2's colour is easier.
        val walled = AdventureProgress().withStatus(square("r1c1"), SquareStatus.A_RANK).withDone("r1c1.hc_link", true)
            .withDone("r1c1.poh_zelda", true).withDone("r1c1.gs1", true).withDone("r1c1.gs2", true)
        val noSearch = map.copy(squares = map.squares.map { if (it.id == "r1c1") it.copy(searches = emptyList()) else it })
        assertEquals("B-1: clear it (Stop the healers!)", AdventureNext.line(noSearch, walled, AdventureSpoilers(noSearch, walled, on = false)))
    }
}
