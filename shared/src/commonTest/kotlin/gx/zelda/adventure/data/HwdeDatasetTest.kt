package gx.zelda.adventure.data

import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Side
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every Adventure Mode map HW:DE lists, checked by [AdventureMap.problems], and the Adventure Map
 * against the counts and squares in docs/hwde.md: the Helper and SBAllen's guide agree on these
 * after tools/hwde/corrections.json, and Nintendo Life and RPG Site give the weapon and warrior
 * squares independently.
 */
class HwdeDatasetTest {
    private fun withMaps(block: (Map<String, AdventureMap>) -> Unit) = runTest {
        val files = Games.all.flatMap { g -> g.trackers.filter { it.kind == TrackerKind.ADVENTURE_MAP }.mapNotNull { it.dataFile } }
        block(files.associate { it.substringAfterLast('/').removeSuffix(".json") to AdventureRepository.load(it, strict = true) })
    }

    @Test
    fun everyMapLoadsStrictlyAndIsSound() = withMaps { maps ->
        assertEquals(setOf("adventure_map"), maps.keys)
        maps.forEach { (name, map) ->
            assertEquals(emptyList(), map.problems(), name)
            assertEquals(name, map.id)
        }
    }

    @Test
    fun theAdventureMapHasItsCounts() = withMaps { maps ->
        val map = maps.getValue("adventure_map")
        assertEquals(16 to 8, map.cols to map.rows)
        assertEquals(128, map.squares.size)
        val rewards = map.squares.flatMap { it.rewards }.groupingBy { it.type }.eachCount()
        assertEquals(111, rewards[RewardType.HEART_CONTAINER])
        assertEquals(58, rewards[RewardType.HEART_PIECE])
        assertEquals(10, rewards[RewardType.WEAPON])
        assertEquals(4, rewards[RewardType.WARRIOR])
        assertEquals(6, rewards[RewardType.OUTFIT])
        assertEquals(24, map.squares.sumOf { it.skulltulas.size })
        assertEquals(94, map.squares.sumOf { it.searches.size })
        assertEquals(
            setOf("compass", "bomb", "candle", "ladder", "power_bracelet", "water_bomb", "digging_mitts", "ice_arrow", "raft", "hookshot", "recorder", "goddess_harp"),
            map.cards.map { it.id }.toSet(),
            "Zelda Wiki's twelve Adventure Map cards",
        )
        assertEquals(listOf("Black", "Green", "Yellow", "Purple", "Orange", "Blue", "Red"), map.regions.map { it.name })
    }

    /** Start H-8 and the Dark Ruler at A-6 (Prima's guide via Zelda Wiki; SBAllen's H-8 and F-1). */
    @Test
    fun theStartAndTheDarkRuler() = withMaps { maps ->
        val map = maps.getValue("adventure_map")
        assertEquals("H-8", map.square(map.start!!)!!.label)
        val boss = map.square(map.finalBoss!!)!!
        assertEquals("A-6", boss.label)
        assertEquals("F-1", boss.altLabel)
        assertEquals(listOf("r1c6"), map.squares.filter { it.finalBoss }.map { it.id })
    }

    /** Nintendo Life's DE weapon list, in Prima's labels. Its Great Fairy (G-16) is the one it gets wrong: docs/hwde.md. */
    @Test
    fun weaponSquaresMatchNintendoLife() = withMaps { maps ->
        val map = maps.getValue("adventure_map")
        val weapons = map.squares.flatMap { s -> s.rewards.filter { it.type == RewardType.WEAPON }.map { it.weapon!! to s.label } }.toMap()
        assertEquals(
            mapOf(
                "gauntlets" to "G-14", "epona" to "E-15", "spinner" to "C-2", "naginata" to "G-11", "summoning_gate" to "A-9",
                "baton" to "C-15", "dominion_rod" to "A-2", "trident" to "B-8",
                // SBAllen and the Helper, against Nintendo Life's G-16.
                "great_fairy" to "H-1",
                // Linkle's boots aren't in Nintendo Life's list.
                "boots" to "A-16",
            ),
            weapons,
        )
    }

    /** RPG Site's DE character list: the four warriors the Adventure Map unlocks. */
    @Test
    fun warriorSquaresMatchRpgSite() = withMaps { maps ->
        val map = maps.getValue("adventure_map")
        val unlocks = map.squares.flatMap { s -> s.rewards.filter { it.type == RewardType.WARRIOR }.map { it.warrior!! to s.label } }.toMap()
        assertEquals(mapOf("young_link" to "G-9", "skull_kid" to "C-13", "twili_midna" to "C-6", "tingle" to "A-12"), unlocks)
    }

    /** The corrections from tools/hwde/corrections.json are in. */
    @Test
    fun theCorrectionsAreApplied() = withMaps { maps ->
        val map = maps.getValue("adventure_map")
        val a16 = map.squares.single { it.label == "A-16" }
        assertTrue(a16.rewards.none { it.id == "r1c16.poh_toon_link" }, "3DS-only")
        assertTrue(a16.rewards.any { it.id == "r1c16.fairy_clothing_royal_hood" })
        assertTrue("recorder" in map.squares.single { it.label == "A-12" }.givesCards)
        assertTrue(map.squares.single { it.label == "B-1" }.note!!.startsWith("Guides disagree"))
        assertTrue(map.squares.single { it.label == "G-7" }.note!!.startsWith("Guides disagree"))
    }

    @Test
    fun wallsAreDrawnFromBothSides() = withMaps { maps ->
        val map = maps.getValue("adventure_map")
        for (s in map.squares) for (side in s.blockades) {
            val other = map.neighbour(s, side) ?: continue
            assertTrue(side.opposite in other.blockades, "${s.label} ${side.name}")
        }
        assertEquals(listOf(Side.SOUTH), map.square("r1c6")!!.blockades, "the Dark Ruler's wall")
    }
}
