package gx.zelda.adventure.data

import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.CardEffect
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Side
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertEquals(
            setOf("adventure_map", "great_sea_map", "master_quest_map", "master_wind_waker_map", "twilight_map", "termina_map", "rewards_map"),
            maps.keys,
        )
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

    /** The Great Sea: 14×7 squares, its hazards' rules, and four warriors (RPG Site's squares). */
    @Test
    fun theGreatSeaMapHasItsCounts() = withMaps { maps ->
        val map = maps.getValue("great_sea_map")
        assertEquals(14 to 7, map.cols to map.rows)
        assertEquals(98, map.squares.size)
        val rewards = map.squares.flatMap { it.rewards }.groupingBy { it.type }.eachCount()
        assertEquals(54, rewards[RewardType.HEART_CONTAINER])
        assertEquals(11, rewards[RewardType.HEART_PIECE])
        assertEquals(27, rewards[RewardType.WEAPON])
        assertEquals(26, rewards[RewardType.OUTFIT])
        assertEquals(32, map.squares.sumOf { it.skulltulas.size })
        assertEquals(85, map.squares.sumOf { it.searches.size })
        assertEquals(7, map.squares.sumOf { s -> s.searches.count { it.wholeSquare } }, "the Wind Waker's")
        assertEquals(14, map.squares.count { it.rule?.startsWith("Great Sea Rule") == true })
        assertEquals("B-9", map.square(map.start!!)!!.label)
        assertEquals("C-9", map.square(map.finalBoss!!)!!.label)
        val unlocks = map.squares.flatMap { s -> s.rewards.filter { it.type == RewardType.WARRIOR }.map { it.warrior!! to s.label } }.toMap()
        assertEquals(mapOf("medli" to "B-8", "toon_link" to "B-9", "tetra" to "C-11", "king_daphnes" to "C-13"), unlocks)
        // The salvage-arm cards SBAllen gives that the Helper missed (tools/hwde/corrections.json).
        for (label in listOf("B-8", "C-13")) assertTrue("salvage_arm" in map.squares.single { it.label == label }.givesCards, label)
        assertEquals(listOf(CardEffect.RULE), map.card("boomerang")!!.effects, "lifts a Great Sea Rule")
        assertEquals(listOf(CardEffect.PATH), map.card("windwaker")!!.effects, "stops headwinds")
    }

    /** The Great Sea under Endless Night: every square has a rule, and the map's only weapons are Medli's harps. */
    @Test
    fun theMasterWindWakerMapHasItsCounts() = withMaps { maps ->
        val map = maps.getValue("master_wind_waker_map")
        assertEquals(14 to 7, map.cols to map.rows)
        assertEquals(98, map.squares.size)
        assertTrue(map.squares.all { it.rule != null })
        assertEquals(85, map.squares.count { it.rule!!.startsWith("Endless Night Rule") })
        assertTrue(map.squares.none { "Engless" in it.rule!! }, "SBAllen's typo, fixed")
        val rewards = map.squares.flatMap { it.rewards }.groupingBy { it.type }.eachCount()
        assertEquals(85, rewards[RewardType.HEART_CONTAINER])
        assertEquals(8, rewards[RewardType.HEART_PIECE])
        assertEquals(20, map.squares.sumOf { it.skulltulas.size })
        assertEquals(84, map.squares.sumOf { it.searches.size })
        val harps = map.squares.flatMap { s -> s.rewards.filter { it.type == RewardType.WEAPON }.map { "${it.weapon} ${it.level} at ${s.label}" } }.sorted()
        assertEquals(listOf("rito_harp 2 at D-4", "rito_harp 3 at F-11", "rito_harp 4 at B-13"), harps, "Nintendo Life's squares")
        assertEquals("G-3", map.square(map.start!!)!!.label)
        assertEquals("G-4", map.square(map.finalBoss!!)!!.label)
        assertTrue("power_bracelet_2" in map.cards.map { it.id }, "the Great Sea's Power Bracelet, not the Adventure Map's")
    }

    /** The Second Quest's map: every square under a Master Quest Rule, and every weapon's level 3 (Nintendo Life's 35 squares). */
    @Test
    fun theMasterQuestMapHasItsCounts() = withMaps { maps ->
        val map = maps.getValue("master_quest_map")
        assertEquals(16 to 8, map.cols to map.rows)
        assertEquals(128, map.squares.size)
        assertTrue(map.squares.all { it.rule?.startsWith("Master Quest Rule") == true })
        val rewards = map.squares.flatMap { it.rewards }.groupingBy { it.type }.eachCount()
        assertEquals(92, rewards[RewardType.HEART_CONTAINER])
        assertEquals(11, rewards[RewardType.HEART_PIECE])
        assertEquals(35, rewards[RewardType.WEAPON])
        assertTrue(map.squares.flatMap { it.rewards }.filter { it.type == RewardType.WEAPON }.all { it.level == "3" })
        assertEquals(20, map.squares.sumOf { it.skulltulas.size })
        assertEquals(103, map.squares.sumOf { it.searches.size }, "the Helper's doubled Ladder searches removed")
        assertEquals("H-8", map.square(map.start!!)!!.label)
        assertEquals("A-1", map.square(map.finalBoss!!)!!.label)
        for (label in listOf("F-16", "G-16")) assertEquals(1, map.squares.single { it.label == label }.searches.count { it.card == "ladder" }, label)
    }

    /** The Rewards Map: 13 squares on the full 16×8 grid, named by their illustrations, with no art, start or searches. */
    @Test
    fun theRewardsMapHasItsIllustrations() = withMaps { maps ->
        val map = maps.getValue("rewards_map")
        assertEquals(16 to 8, map.cols to map.rows)
        assertNull(map.art)
        assertNull(map.start)
        assertEquals(
            setOf(
                "Scattered Seeds", "A Prayer Received", "The Nightmare Closes In", "Those with the Wind", "Guided by Courage",
                "True Partners", "The Giants Gather", "Chosen by Fate", "Rulers of Twilight", "Boss Rush", "Evil Power",
                "Running to the Rescue", "Unity",
            ),
            map.squares.map { it.title }.toSet(),
        )
        assertTrue(map.squares.all { it.searches.isEmpty() && it.skulltulas.isEmpty() })
        // Zelda Wiki's Costume page: the Boss costumes each illustration gives on an A rank.
        val outfits = map.squares.flatMap { s -> s.rewards.filter { it.type == RewardType.OUTFIT }.map { s.title!! to "${it.warrior} ${it.name}" } }.toMap()
        assertEquals(
            mapOf(
                "Scattered Seeds" to "lana Standard Outfit (Boss)", "True Partners" to "link Dark Link Costume",
                "The Giants Gather" to "link Hero's Clothes (Boss)", "Rulers of Twilight" to "ganondorf Standard Armor (Boss)",
                "Boss Rush" to "zelda Standard Robes (Boss)", "Evil Power" to "cia Masked / Unmasked / Hatless (Boss)",
            ),
            outfits,
        )
    }

    /** Twilight Princess's Hyrule: 15×8 with blank squares, Twilight clouds the Tears of Light clear, and level-4 weapons. */
    @Test
    fun theTwilightMapHasItsCounts() = withMaps { maps ->
        val map = maps.getValue("twilight_map")
        assertEquals(15 to 8, map.cols to map.rows)
        assertEquals(93, map.squares.size, "the rest of the grid is blank")
        assertEquals(21, map.squares.count { it.rule?.startsWith("Twilight Rule") == true })
        assertEquals(20, map.squares.sumOf { s -> s.searches.count { it.wholeSquare && it.card == "tears_of_light" } })
        val rewards = map.squares.flatMap { it.rewards }.groupingBy { it.type }.eachCount()
        assertEquals(67, rewards[RewardType.HEART_CONTAINER])
        assertEquals(1, rewards[RewardType.HEART_PIECE])
        assertEquals(17, rewards[RewardType.WEAPON])
        assertTrue(map.squares.flatMap { it.rewards }.filter { it.type == RewardType.WEAPON }.all { it.level == "4" })
        assertEquals(20, map.squares.sumOf { it.skulltulas.size })
        assertEquals(listOf(CardEffect.PATH), map.card("ooccoo")!!.effects, "warps between portals")
        val d12 = map.squares.single { it.label == "D-12" }
        assertTrue(d12.rewards.any { it.name == "Fairy Bottom - Twilight Skirt" } && d12.rewards.none { "Magic Skirt" in it.name }, "the Switch's skirt")
    }

    /** Majora's Mask's Termina: 16×8 with blank squares, the masks, the four Giants and level-4 weapons. */
    @Test
    fun theTerminaMapHasItsCounts() = withMaps { maps ->
        val map = maps.getValue("termina_map")
        assertEquals(16 to 8, map.cols to map.rows)
        assertEquals(85, map.squares.size)
        val rewards = map.squares.flatMap { it.rewards }.groupingBy { it.type }.eachCount()
        assertEquals(48, rewards[RewardType.HEART_CONTAINER])
        assertEquals(2, rewards[RewardType.HEART_PIECE])
        assertEquals(18, rewards[RewardType.WEAPON])
        assertEquals(20, map.squares.sumOf { it.skulltulas.size })
        assertEquals(66, map.squares.sumOf { it.searches.size })
        assertEquals(listOf(CardEffect.PATH), map.card("giant")!!.effects, "all four open a hidden part of the map")
        assertEquals(listOf(CardEffect.OTHER), map.card("song_of_time")!!.effects)
        assertTrue(map.squares.single { it.label == "C-6" }.rewards.any { it.id == "r3c6.fairy_food_ember_seeds" })
        assertEquals("B-2", map.square(map.finalBoss!!)!!.label)
    }

    @Test
    fun wallsAreDrawnFromBothSides() = withMaps { maps ->
        for (map in maps.values) for (s in map.squares) for (side in s.blockades) {
            val other = map.neighbour(s, side) ?: continue
            assertTrue(side.opposite in other.blockades, "${map.id} ${s.label} ${side.name}")
        }
        val map = maps.getValue("adventure_map")
        assertEquals(listOf(Side.SOUTH), map.square("r1c6")!!.blockades, "the Dark Ruler's wall")
    }
}
