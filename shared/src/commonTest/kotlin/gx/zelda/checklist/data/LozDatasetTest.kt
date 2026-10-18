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
 * The Legend of Zelda's checklists against the counts in docs/loz.md, where Zelda Wiki, Zelda
 * Dungeon and StrategyWiki agree, plus the links between files. Format and in-file references are
 * checked for every game by [ChecklistDatasetsTest].
 */
class LozDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.LOZ.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("loz/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private val quests = listOf("first_quest", "second_quest")

    /** "Items: Bow; Boomerang (dropped by the Goriyas)." in a level's notes, as names. */
    private fun ChecklistItem.levelItems(): List<String> =
        Regex("""Items: (.+?)\.(?: |$)""").find(notes.orEmpty())!!.groupValues[1].split("; ").map { it.substringBefore(" (").substringBefore(",") }

    private fun ChecklistItem.boss(): String = Regex("""^Boss: (.+?)\.""").find(notes.orEmpty())!!.groupValues[1]

    @Test
    fun threeSaveFilesAndAnOverviewFirst() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.LOZ.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.LOZ.trackers.first().kind)
        assertEquals(
            listOf("dungeons", "items", "heart_containers"),
            Games.LOZ.trackers.drop(1).map { it.dataFile!!.removePrefix("loz/").removeSuffix(".json") },
        )
    }

    /** Each file has a tab per quest, since the Second Quest carries on in the same save file. */
    @Test
    fun everyFileHasATabPerQuest() = withCatalogs { all ->
        all.forEach { (name, c) -> assertEquals(quests, c.sections.map { it.id }, name) }
    }

    @Test
    fun nineLevelsPerQuestAndTheNinthNeedsEveryTriforcePiece() = withCatalogs { all ->
        val c = all.getValue("dungeons")
        assertEquals(mapOf("first_quest" to 9, "second_quest" to 9), c.sizes())
        listOf("q1", "q2").forEach { q ->
            assertEquals((1..9).map { "${q}_level_$it" }, c.items.filter { it.id.startsWith(q) }.map { it.id })
            (1..8).forEach { assertTrue("Triforce piece" in c.item("${q}_level_$it")!!.reward!!, "$q level $it") }
            val nine = c.item("${q}_level_9")!!
            assertEquals((1..8).map { "${q}_level_$it" }, nine.requires)
            assertEquals("Ganon", nine.boss())
            assertTrue("Silver Arrow" in nine.levelItems())
        }
    }

    @Test
    fun twentySevenItemsInEachQuestWithTheSameNames() = withCatalogs { all ->
        val c = all.getValue("items")
        assertEquals(mapOf("first_quest" to 27, "second_quest" to 27), c.sizes())
        val (first, second) = quests.map { c.section(it)!!.items }
        assertEquals(first.map { it.name }, second.map { it.name })
        assertEquals(first.map { it.id.removePrefix("q1_") }, second.map { it.id.removePrefix("q2_") })
    }

    /** An item found in a level is listed in that level's notes, and every item a level lists is in the items file. */
    @Test
    fun levelItemsMatchTheDungeons() = withCatalogs { all ->
        val dungeons = all.getValue("dungeons")
        val items = all.getValue("items")
        listOf("q1" to "first_quest", "q2" to "second_quest").forEach { (q, quest) ->
            val inLevels = items.section(quest)!!.items.filter { it.area.orEmpty().startsWith("Level ") }
            assertEquals(14, inLevels.size, quest)
            inLevels.forEach { item ->
                val level = dungeons.item("${q}_level_${item.area!!.removePrefix("Level ")}")!!
                val named = item.name.substringBefore(" (")
                assertTrue(named in level.levelItems(), "${item.id} isn't in ${level.id}: ${level.levelItems()}")
            }
            dungeons.section(quest)!!.items.forEach { level ->
                level.levelItems().forEach { named ->
                    val match = inLevels.filter { it.name.substringBefore(" (") == named && it.area == "Level ${level.id.substringAfterLast('_')}" }
                    assertTrue(match.isNotEmpty(), "${level.id} lists $named, which the items file doesn't put there")
                }
            }
        }
    }

    @Test
    fun heartsGoFromThreeToSixteenInEachQuest() = withCatalogs { all ->
        val c = all.getValue("heart_containers")
        assertEquals(mapOf("first_quest" to 13, "second_quest" to 13), c.sizes())
        quests.forEach { quest ->
            assertEquals(mapOf("Levels" to 8, "Overworld" to 5), c.section(quest)!!.items.groupingBy { it.region!! }.eachCount(), quest)
        }
        c.summaries.forEach { hearts ->
            assertEquals(3.0, c.evaluate(hearts, ChecklistProgress()).value)
            assertEquals(16.0, c.evaluate(hearts, c.allDone()).max)
        }
    }

    /** Levels 1–8 each give a container, from the boss the Dungeons file names. */
    @Test
    fun bossContainersMatchTheDungeons() = withCatalogs { all ->
        val dungeons = all.getValue("dungeons")
        all.getValue("heart_containers").items.filter { it.region == "Levels" }.forEach { container ->
            val level = dungeons.item(container.id.replace("_hc_", "_"))!!
            assertEquals(level.name.substringBefore(":"), container.name.substringBefore(":"))
            assertEquals(level.boss().substringBefore(" ("), container.name.substringAfter(": ").substringBefore(" ("), container.id)
        }
    }

    /** Only a "Take any one you want" cave can be missed: take the potion and its container is gone. */
    @Test
    fun onlyTheTakeAnyCavesAreMissable() = withCatalogs { all ->
        val missable = all.values.flatMap { c -> c.items.filter { it.missable } }
        assertEquals(8, missable.size)
        missable.forEach { assertTrue("Take any one you want" in it.howTo!!, it.id) }
    }

    @Test
    fun everyItemSaysWhereOrHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            val vague = c.items.filter { it.howTo.isNullOrBlank() || it.area.isNullOrBlank() }.map(ChecklistItem::id)
            assertEquals(emptyList(), vague, name)
        }
    }
}
