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
 * A Link to the Past's checklists against the counts in docs/alttp.md, where Zelda Wiki, Zelda
 * Dungeon and StrategyWiki agree, plus the links between files. Format and in-file references
 * (ids, regions, `requires`, formulas) are checked for every game by [ChecklistDatasetsTest].
 */
class AlttpDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.ALTTP.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("alttp/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.summary(label: String) = summaries.single { it.label == label }

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        val checklists = Games.ALTTP.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(
            listOf("main_quest", "items", "heart_pieces"),
            checklists.map { it.dataFile!!.removePrefix("alttp/").removeSuffix(".json") },
        )
    }

    /** Zelda Wiki's top-level locations: the Light World and the Dark World, in that order. */
    @Test
    fun filesWithRegionsUseTheTwoWorlds() = withCatalogs { all ->
        all.forEach { (name, c) ->
            if (c.regions.isNotEmpty()) assertEquals(listOf("Light World", "Dark World"), c.regions, name)
        }
    }

    @Test
    fun mainQuestHasThreePendantsAndSevenCrystals() = withCatalogs { all ->
        val c = all.getValue("main_quest")
        assertEquals(mapOf("light_world" to 9, "dark_world" to 9), c.sizes())
        assertEquals(3.0, c.evaluate(c.summary("Pendants"), c.allDone()).max)
        assertEquals(7.0, c.evaluate(c.summary("Crystals"), c.allDone()).max)
        assertEquals(0.0, c.evaluate(c.summary("Crystals"), ChecklistProgress()).value)
        // Only the dungeons that give one count toward the totals, and each is tagged.
        assertEquals(3, c.section("light_world")!!.items.count { "Pendant" in it.tags && it.value == 1.0 })
        assertEquals(7, c.section("dark_world")!!.items.count { "Crystal" in it.tags && it.value == 1.0 })
        assertEquals(c.items.filter { it.value > 0 }, c.items.filter { it.tags.isNotEmpty() })
        (1..7).forEach { n -> assertEquals(1, c.items.count { "Crystal $n;" in it.reward.orEmpty() }, "Crystal $n") }
        // Ganon's Tower opens with every Crystal, and Ganon is last.
        assertEquals(c.items.filter { "Crystal" in it.tags }.map { it.id }, c.item("ganons_tower")!!.requires)
        assertEquals("ganon", c.items.last().id)
    }

    @Test
    fun heartsAddUpToTwenty() = withCatalogs { all ->
        val c = all.getValue("heart_pieces")
        assertEquals(mapOf("pieces" to 24, "containers" to 11), c.sizes())
        assertEquals(mapOf("Light World" to 18, "Dark World" to 6), c.section("pieces")!!.items.groupingBy { it.region!! }.eachCount())
        val hearts = c.summaries.single()
        assertEquals(3.0, c.evaluate(hearts, ChecklistProgress()).value)
        assertEquals(20.0, c.evaluate(hearts, c.allDone()).value)
    }

    /** The Sanctuary's container, and one from each boss whose Main Quest step lists one. */
    @Test
    fun heartContainersMatchTheMainQuest() = withCatalogs { all ->
        val steps = all.getValue("main_quest").items.filter { "Heart Container" in it.reward.orEmpty() }
        assertEquals(11, steps.size)
        val containers = all.getValue("heart_pieces").section("containers")!!.items
        assertEquals(steps.map { it.area }, containers.map { it.area })
    }

    @Test
    fun itemsAndUpgrades() = withCatalogs { all ->
        val c = all.getValue("items")
        assertEquals(mapOf("equipment" to 7, "items" to 18, "bottles" to 4, "upgrades" to 3), c.sizes())
        assertEquals(listOf(4, 3, 2, 2), listOf("sword", "shield", "mail", "gloves").map { c.item(it)!!.steps })
        assertEquals(listOf("Fighter's Sword", "Master Sword", "Tempered Sword", "Golden Sword"), c.item("sword")!!.tiers.map { it.name })
        // Bombs 10 to 50 and arrows 30 to 70 at the Pond of Happiness.
        val bombs = c.evaluate(c.summary("Bombs"), ChecklistProgress())
        assertEquals(10.0 to 50.0, bombs.value to bombs.max)
        val arrows = c.evaluate(c.summary("Arrows"), ChecklistProgress())
        assertEquals(30.0 to 70.0, arrows.value to arrows.max)
    }

    /** Each dungeon's "Treasure: X" in the Main Quest is an item, or an item's tier, in Items & Equipment. */
    @Test
    fun dungeonTreasuresAreItems() = withCatalogs { all ->
        val names = all.getValue("items").items.flatMap { listOf(it.name) + it.tiers.map { t -> t.name } }.toSet()
        val treasures = all.getValue("main_quest").items.mapNotNull { Regex("""Treasure: ([^.]+)\.""").find(it.notes.orEmpty())?.groupValues?.get(1) }
        assertEquals(11, treasures.size)
        treasures.forEach { assertTrue(it in names, "$it isn't in Items & Equipment") }
    }

    /** Nothing in ALttP can be missed for good (docs/alttp.md). */
    @Test
    fun nothingIsMissable() = withCatalogs { all ->
        all.forEach { (name, c) -> assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id }, name) }
    }

    @Test
    fun everyItemSaysWhereOrHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            val vague = c.items.filter { it.howTo.isNullOrBlank() && it.tiers.any { t -> t.howTo.isNullOrBlank() } }.map(ChecklistItem::id)
            assertEquals(emptyList(), vague, name)
            c.items.filter { it.tiers.isEmpty() }.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.id} in $name") }
        }
    }
    /**
     * Story links (docs/alttp.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand. Only the Lamp, in Link's House, is had before the uncle.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensIt() = withCatalogs { all ->
        val links = all.filterKeys { it != "main_quest" }.values.flatMap { it.items }.associate { it.id to it.unlockedBy }
        assertEquals(setOf("lamp"), links.filterValues { it.isEmpty() }.keys)
        assertEquals(listOf("sanctuary"), links["poh_kakariko_well"], "the Light World, once Zelda is safe")
        assertEquals(listOf("eastern_palace"), links["poh_desert_ledge"], "the desert, with the Pegasus Shoes for the Book of Mudora")
        assertEquals(listOf("agahnim"), links["poh_village_chest_game"], "the Dark World")
        assertEquals(listOf("thieves_town"), links["poh_stake_garden"], "the Titan's Mitt, from Thieves' Town")
    }
}
