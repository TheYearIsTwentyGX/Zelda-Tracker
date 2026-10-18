package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Echoes of Wisdom's checklists against the counts in docs/eow.md, where Zelda Wiki, Zelda Dungeon
 * and Game8 agree, plus the links between files. Format and in-file references (ids, regions,
 * `requires`, formulas) are checked for every game by [ChecklistDatasetsTest].
 */
class EowDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.EOW.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("eow/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.names(section: String? = null): Set<String> =
        (section?.let { section(it)!!.items } ?: items).map { it.name }.toSet()

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    /** The Zelda Wiki regions every file uses (docs/eow.md, Regions), in map order. */
    private val regions = listOf(
        "Suthorn Prairie", "Hyrule Castle", "Hyrule Field", "Eastern Hyrule Field", "Lake Hylia", "Gerudo Desert",
        "Jabul Waters", "Eldin Volcano", "Hebra Mountain", "Faron Wetlands", "Eternal Forest",
    )

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        val checklists = Games.EOW.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(
            listOf(
                "main_quest", "echoes", "heart_pieces", "might_crystals", "side_quests", "accessories",
                "smoothies", "slumber_dojo", "stamp_stands", "collectibles",
            ),
            checklists.map { it.dataFile!!.removePrefix("eow/").removeSuffix(".json") },
        )
    }

    @Test
    fun everyFileUsesTheSameRegionsInTheSameOrder() = withCatalogs { all ->
        all.forEach { (name, c) ->
            assertTrue(regions.containsAll(c.regions), "$name: ${c.regions - regions.toSet()}")
            assertEquals(regions.filter { it in c.regions }, c.regions, "$name is out of order")
        }
    }

    @Test
    fun echoes() = withCatalogs { all ->
        val c = all.getValue("echoes")
        assertEquals(127, c.items.size)
        assertEquals(mapOf("Object" to 34, "Monster" to 93), c.items.groupingBy { it.tags.single() }.eachCount())
        // Notebook order: "No. 1" … "No. 127".
        c.items.forEachIndexed { i, echo -> assertTrue(echo.notes!!.startsWith("No. ${i + 1}"), "${echo.id}: ${echo.notes}") }
        assertTrue(c.items.none { it.missable })
    }

    @Test
    fun heartsAddUpToTwenty() = withCatalogs { all ->
        val c = all.getValue("heart_pieces")
        assertEquals(mapOf("pieces" to 40, "containers" to 7), c.sizes())
        val hearts = c.summaries.single()
        assertEquals(3.0, c.evaluate(hearts, ChecklistProgress()).value)
        assertEquals(20.0, c.evaluate(hearts, c.allDone()).value)
    }

    /** One Heart Container per boss, each from a Main Quest dungeon that lists it as a reward. */
    @Test
    fun heartContainersMatchTheMainQuestDungeons() = withCatalogs { all ->
        val dungeons = all.getValue("main_quest").section("story")!!.items.filter { "Heart Container" in it.reward.orEmpty() }
        assertEquals(7, dungeons.size)
        val containers = all.getValue("heart_pieces").section("containers")!!.items
        assertEquals(dungeons.map { it.region }.toSet(), containers.map { it.region }.toSet())
    }

    @Test
    fun mightCrystalsAreOneHundredAndFiftyAndLueburrySpendsThemAll() = withCatalogs { all ->
        val c = all.getValue("might_crystals")
        assertEquals(mapOf("crystals" to 85, "upgrades" to 5), c.sizes())
        assertEquals(150.0, c.evaluate(c.summaries.single(), c.allDone()).value)
        // Sword, Bow and Bombs 10 + 20 each, Energy 5 + 10 + 20, and the last 25.
        assertEquals(150.0, c.evaluate(c.section("upgrades")!!.summary!!, c.allDone()).max)
        assertEquals(listOf(2, 2, 2, 3, 1), c.section("upgrades")!!.items.map { it.steps })
    }

    @Test
    fun mainQuest() = withCatalogs { all ->
        val c = all.getValue("main_quest")
        assertEquals(mapOf("story" to 35, "tri" to 4, "swordfighter" to 3), c.sizes())
        // Tri's Power Lv. 2-11.
        assertEquals(10, c.item("tri_level")!!.steps)
        assertEquals("final_boss_null", c.section("story")!!.items.last().id)
    }

    @Test
    fun theOtherCounts() = withCatalogs { all ->
        assertEquals(mapOf("side_quests" to 50), all.getValue("side_quests").sizes())
        assertEquals(mapOf("accessories" to 28, "slots" to 4), all.getValue("accessories").sizes())
        assertEquals(mapOf("smoothies" to 59, "potions" to 10), all.getValue("smoothies").sizes())
        assertEquals(mapOf("challenges" to 15, "fast_clears" to 15, "milestones" to 6), all.getValue("slumber_dojo").sizes())
        assertEquals(mapOf("stands" to 25, "rewards" to 5), all.getValue("stamp_stands").sizes())
        assertEquals(mapOf("bottles" to 4, "automatons" to 6, "outfits" to 11, "minigames" to 7), all.getValue("collectibles").sizes())
        assertEquals(3, all.getValue("collectibles").section("outfits")!!.items.count { "amiibo" in it.tags })
    }

    /** Nothing in EoW can be missed for good (Game8's missables page; docs/eow.md). */
    @Test
    fun nothingIsMissable() = withCatalogs { all ->
        all.forEach { (name, c) -> assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id }, name) }
    }

    /** A piece or crystal from a side quest names one that's in the side quest list. */
    @Test
    fun sideQuestRewardsNameRealQuests() = withCatalogs { all ->
        val quests = all.getValue("side_quests").names()
        val quoted = Regex("""[Ss]ide quest "([^"]+)"""")
        listOf("heart_pieces", "might_crystals").forEach { file ->
            val fromQuests = all.getValue(file).items.filter { "Side quest" in it.tags }
            assertTrue(fromQuests.isNotEmpty(), file)
            fromQuests.forEach { item ->
                val quest = quoted.find(item.howTo.orEmpty())?.groupValues?.get(1)
                assertTrue(quest in quests, "${item.id} names \"$quest\", which isn't a side quest")
            }
        }
        // Each Automaton is built in one of Dampé's quests, named in quotes.
        all.getValue("collectibles").section("automatons")!!.items.forEach { automaton ->
            val quest = Regex("^'(.+?)':").find(automaton.howTo.orEmpty())?.groupValues?.get(1)
            assertTrue(quest in quests, "${automaton.id} names \"$quest\"")
        }
    }

    /** The dojo's milestone rewards are the accessories and pieces the other files say come from it. */
    @Test
    fun slumberDojoRewardsMatchTheOtherFiles() = withCatalogs { all ->
        val milestones = all.getValue("slumber_dojo").section("milestones")!!.items
        val accessories = all.getValue("accessories").names("accessories")
        val masteries = milestones.mapNotNull { it.reward?.removeSuffix(" (accessory)")?.takeIf { r -> r.endsWith("Mastery") } }
        assertEquals(listOf("First Mastery", "Second Mastery", "Final Mastery"), masteries)
        assertTrue(accessories.containsAll(masteries))
        val dojoPieces = all.getValue("heart_pieces").items.filter { "Slumber Dojo" in it.area.orEmpty() }
        assertEquals(milestones.count { it.reward == "Piece of Heart" }, dojoPieces.size)
    }

    @Test
    fun everyItemSaysWhereOrHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            val vague = c.items.filter { it.howTo.isNullOrBlank() && it.tiers.isEmpty() }.map(ChecklistItem::id)
            assertEquals(emptyList(), vague, name)
        }
    }
}
