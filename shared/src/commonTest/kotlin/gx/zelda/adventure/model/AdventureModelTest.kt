package gx.zelda.adventure.model

import gx.zelda.adventure.AdventureFixture
import gx.zelda.adventure.AdventureFixture.map
import gx.zelda.adventure.AdventureFixture.square
import gx.zelda.adventure.ui.howToEarn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The map's checks, progress and its rules: A-rank includes cleared, ticks are separate, cards come from clears. */
class AdventureModelTest {
    @Test
    fun theFixtureIsSound() {
        assertEquals(emptyList(), map.problems())
        assertEquals(5, map.squares.size)
        assertNull(map.squareAt(2, 3), "a blank square")
        assertNull(map.squareAt(0, 1), "off the grid")
        assertEquals("r2c1", map.neighbour(square("r1c1"), Side.SOUTH)?.id)
        assertNull(map.neighbour(square("r1c1"), Side.NORTH))
        assertFalse(map.isOpen(square("r1c1"), Side.EAST), "walled")
        assertTrue(map.isOpen(square("r1c1"), Side.SOUTH))
        assertEquals(listOf("r1c1"), map.sourcesOf("bomb").map { it.id })
    }

    @Test
    fun problemsCatchBrokenFiles() {
        fun problems(edit: (List<Square>) -> List<Square>) = map.copy(squares = edit(map.squares)).problems()
        fun List<Square>.change(id: String, f: Square.() -> Square) = map { if (it.id == id) it.f() else it }

        assertTrue(problems { it.change("r1c2") { copy(blockades = emptyList()) } }.any { "isn't on r1c2 too" in it }, "a wall on one side only")
        assertTrue(problems { it.change("r1c2") { copy(searches = listOf(Search("r1c2.search_bomb", "bomb", row = 11, col = 0))) } }.any { "outside the 16x11" in it })
        assertTrue(problems { it.change("r1c2") { copy(searches = listOf(Search("r1c2.search_bomb", "bomb", col = 16, row = 0))) } }.any { "outside the 16x11" in it })
        assertTrue(problems { it.change("r1c2") { copy(givesCards = listOf("raft")) } }.any { "unknown card 'raft'" in it })
        assertTrue(problems { it.change("r1c2") { copy(rewards = rewards + rewards) } }.any { "duplicate id" in it })
        assertTrue(problems { it.change("r1c2") { copy(id = "r1c9") } }.any { "doesn't match row 1, col 2" in it })
        assertTrue(problems { it.change("r1c2") { copy(region = "blue") } }.any { "unknown region 'blue'" in it })
        assertTrue(problems { it.change("r2c1") { copy(givesCards = emptyList()) } }.any { "no square on the map gives the hookshot card" in it })
        assertTrue(problems { it.change("r1c2") { copy(rewards = listOf(rewards.single().copy(warrior = "ganon"))) } }.any { "unknown warrior 'ganon'" in it })
        assertTrue(problems { it.change("r1c2") { copy(rewards = listOf(rewards.single().copy(id = "r1c1.weapon_spinner_1"))) } }.any { "not under r1c2" in it })
        assertTrue(map.copy(start = "r2c3").problems().any { "start 'r2c3' isn't a square" in it })
        assertTrue(map.copy(finalBoss = "r1c2").problems().any { "final boss" in it })
    }

    @Test
    fun aRankIsAlsoClearedAndTicksAreSeparate() {
        val sq = square("r1c1")
        val p = AdventureProgress().withStatus(sq, SquareStatus.A_RANK)
        assertTrue(p.isCleared(sq))
        assertTrue(p.isARanked(sq))
        assertFalse(p.isComplete(sq), "its rewards aren't ticked: they need the pictured warrior")
        assertEquals(2, p.doneCount(sq))
        val back = p.withStatus(sq, SquareStatus.CLEARED)
        assertTrue(back.isCleared(sq))
        assertFalse(back.isARanked(sq))
        assertEquals(emptyMap(), back.withStatus(sq, SquareStatus.NONE).status, "none is stored as no entry")
        assertTrue(AdventureProgress().withDone("r1c1.gs1", true).isStarted(sq))
    }

    @Test
    fun withAllMakesSquaresCompleteAndClearsThemAgain() {
        val squares = listOf(square("r1c1"), square("r1c2"))
        val other = AdventureProgress().withDone("r1c3.hc_zelda", true)
        val all = other.withAll(squares, complete = true)
        assertTrue(squares.all(all::isComplete))
        assertEquals(squares.sumOf { it.checkCount }, squares.sumOf(all::doneCount))
        val cleared = all.withAll(squares, complete = false)
        assertTrue(squares.none(cleared::isStarted))
        assertEquals(other, cleared, "other squares are left as they were")
    }

    @Test
    fun aCardIsFoundOnceASquareThatGivesItIsCleared() {
        var p = AdventureProgress()
        assertFalse(AdventureInventory(map, p).has("bomb"))
        p = p.withStatus(square("r1c1"), SquareStatus.CLEARED)
        val inv = AdventureInventory(map, p)
        assertTrue(inv.has("bomb"))
        assertTrue(inv.isAutomatic("bomb"))
        assertTrue(inv.canDo(square("r1c2").searches.single()))
        assertFalse(inv.canDo(square("r1c1").searches.single()), "no Hookshot yet")
        // Set by hand, either way, until the user hands it back.
        assertFalse(AdventureInventory(map, p.withCard("bomb", false)).has("bomb"))
        assertTrue(AdventureInventory(map, p.withCard("hookshot", true)).has("hookshot"))
        assertTrue(AdventureInventory(map, p.withCard("bomb", false).withCard("bomb", null)).isAutomatic("bomb"))
        val done = p.withDone("r1c2.search_bomb", true)
        assertFalse(AdventureInventory(map, done).canDo(square("r1c2").searches.single()), "a done search isn't open")
    }

    @Test
    fun totalsCountEveryKind() {
        val p = AdventureProgress()
            .withStatus(square("r1c1"), SquareStatus.A_RANK)
            .withStatus(square("r2c1"), SquareStatus.CLEARED)
            .withDone("r1c1.hc_link", true)
            .withDone("r1c1.gs1", true)
        val t = AdventureTotals(map, p)
        assertEquals(Tally(2, 5), t.cleared)
        assertEquals(Tally(1, 5), t.aRanked)
        assertEquals(Tally(1, 2), t.rewards(RewardType.HEART_CONTAINER))
        assertEquals(Tally(0, 1), t.rewards(RewardType.HEART_PIECE))
        assertEquals(Tally(1, 2), t.skulltulas)
        assertEquals(Tally(0, 3), t.searches)
        assertEquals(Tally(2 + 1 + 1 + 1, 2 * 5 + 2 + 1 + 1 + 1 + 1 + 2 + 3), t.all)
    }

    @Test
    fun howToEarnSaysWhoAndWhere() {
        assertEquals("A rank as Link", howToEarn(map, square("r1c1").rewards[0]))
        assertEquals("Capture North Keep as Zelda", howToEarn(map, square("r1c1").rewards[1]))
        assertEquals("Clear it as Zelda", howToEarn(map, square("r1c3").rewards[0]))
        assertEquals("Clear it, any warrior", howToEarn(map, square("r2c1").rewards[0]), "costumes go to whoever clears it")
        assertEquals("In a pot at South Keep", howToEarn(map, square("r2c2").rewards[0]))
    }

    @Test
    fun sidesAreOpposite() {
        assertEquals(Side.SOUTH, Side.NORTH.opposite)
        assertEquals(Side.WEST, Side.EAST.opposite)
        assertEquals(Side.entries, Side.entries.map { it.opposite.opposite })
        assertEquals(AdventureFixture.map.squares.map { it.id }.toSet(), AdventureFixture.map.squares.map { "r${it.row}c${it.col}" }.toSet())
    }
}
