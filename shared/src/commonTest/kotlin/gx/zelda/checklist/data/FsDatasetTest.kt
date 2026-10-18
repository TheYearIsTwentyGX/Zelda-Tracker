package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Four Swords' checklist against the counts in docs/fs.md, where Zelda Wiki, Zelda Dungeon and the
 * game's own text agree. Format and in-file references (ids, regions, `requires`, formulas) are
 * checked for every game by [ChecklistDatasetsTest].
 */
class FsDatasetTest {
    private fun withCatalog(block: (ChecklistCatalog) -> Unit) = runTest { block(ChecklistRepository.load("fs/stages.json", strict = true)) }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.summary(label: String) = summaries.single { it.label == label }

    private val stages = listOf("Sea of Trees", "Talus Cave", "Death Mountain")

    @Test
    fun oneChecklistAfterTheOverview() {
        assertEquals(listOf(TrackerKind.OVERVIEW, TrackerKind.CHECKLIST), Games.FS.trackers.map { it.kind })
        assertEquals("fs/stages.json", Games.FS.trackers.last().dataFile)
    }

    /**
     * The three key tiers count toward the total; the Anniversary Edition's stages and the GBA's
     * link-ups are separate, since a player has only one of the two versions.
     */
    @Test
    fun versionOnlySectionsAreSeparate() = withCatalog { c ->
        assertEquals(
            listOf("silver" to 5, "golden" to 4, "heros" to 4, "realm_of_memories" to 9, "heros_trial" to 10, "alttp_links" to 4),
            c.sections.map { it.id to it.items.size },
        )
        assertEquals(listOf("realm_of_memories", "heros_trial", "alttp_links"), c.sections.filter { it.separate }.map { it.id })
        assertEquals(13, c.count(c.allDone(), c.counted).total)
        // The separate sections have no regions: the stages' regions are the main tiers'.
        assertTrue(c.sections.filter { it.separate }.all { s -> s.items.all { it.region == null } })
    }

    /** Three Great Fairies × Silver, Golden and Hero's Keys, and a Vaati's Palace door for each tier. */
    @Test
    fun nineKeysAndThreePalaceDoors() = withCatalog { c ->
        assertEquals(listOf("Chambers of Insight") + stages + "Vaati's Palace", c.regions)
        assertEquals(0.0 to 9.0, c.evaluate(c.summary("Keys"), ChecklistProgress()).let { it.value to it.max })
        assertEquals(3.0, c.evaluate(c.summary("Palace doors"), c.allDone()).max)

        listOf("silver" to "Silver Key", "golden" to "Golden Key", "heros" to "Hero's Key").forEach { (id, key) ->
            val items = c.section(id)!!.items
            val keys = items.filter { it.reward.orEmpty().startsWith(key) }
            assertEquals(stages, keys.map { it.region }, id)
            assertTrue(keys.all { it.value == 1.0 }, id)
            // Each tier's door needs that tier's three keys, and is the tab's last row.
            val door = items.last()
            assertEquals("Vaati's Palace", door.region)
            assertEquals(keys.map { it.id }, door.requires)
            assertEquals(0.0, door.value)
        }
        // A tier's keys only come once the previous tier's door is cleared.
        assertTrue(c.section("golden")!!.items.dropLast(1).all { it.requires == listOf("vaati_silver_door") })
        assertTrue(c.section("heros")!!.items.dropLast(1).all { it.requires == listOf("vaati_golden_door") })
        assertEquals(0.0, c.item("chambers_of_insight")!!.value)
    }

    /** The Realm of Memories and the Hero's Trial: 3 doors × 3 levels each, every level after the one before. */
    @Test
    fun anniversaryEditionHasEighteenLevels() = withCatalog { c ->
        listOf("realm_of_memories" to "vaati_silver_door", "heros_trial" to "heros_trial_unlock").forEach { (id, opener) ->
            val levels = c.section(id)!!.items.filter { it.id != "heros_trial_unlock" }
            assertEquals(9, levels.size, id)
            assertEquals(listOf(opener), levels.first().requires, id)
            levels.zipWithNext().forEach { (before, level) -> assertEquals(listOf(before.id), level.requires, level.id) }
            // The Realm names the door and level in `area`, the Hero's Trial in `name`.
            listOf("Silver door", "Golden door", "Hero's door").forEachIndexed { d, door ->
                (1..3).forEach { n ->
                    val level = levels[d * 3 + n - 1]
                    assertTrue("$door, level $n" in "${level.name} ${level.area}", level.id)
                }
            }
        }
        assertTrue("Master Sword" in c.item("realm_heros_3")!!.reward!!)
        assertTrue("Hurricane Spin" in c.item("trial_heros_3")!!.reward!!)
    }

    @Test
    fun alttpLinkUps() = withCatalog { c ->
        assertEquals(
            listOf("sword_beams", "ten_medals", "hurricane_spin", "palace_of_the_four_sword"),
            c.section("alttp_links")!!.items.map { it.id },
        )
        assertEquals(listOf("ten_medals"), c.item("hurricane_spin")!!.requires)
        assertEquals(listOf("vaati_silver_door"), c.item("palace_of_the_four_sword")!!.requires)
    }

    /** Every key can be earned again on a later run, so nothing is missable (docs/fs.md). */
    @Test
    fun nothingIsMissableAndEveryRowSaysHow() = withCatalog { c ->
        assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id })
        c.items.forEach { assertTrue(!it.howTo.isNullOrBlank(), it.id) }
    }
}
