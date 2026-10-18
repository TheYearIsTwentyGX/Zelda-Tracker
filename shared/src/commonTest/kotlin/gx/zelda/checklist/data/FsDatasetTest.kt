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
 * Four Swords' checklists against the counts in docs/fs.md, where Zelda Wiki, Zelda Dungeon and the
 * game's own text agree. Format and in-file references (ids, regions, `requires`, formulas) are
 * checked for every game by [ChecklistDatasetsTest].
 */
class FsDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.FS.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("fs/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.summary(label: String) = summaries.single { it.label == label }

    private val stages = listOf("Sea of Trees", "Talus Cave", "Death Mountain")

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        val checklists = Games.FS.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(listOf("stages", "anniversary", "alttp_links"), checklists.map { it.dataFile!!.removePrefix("fs/").removeSuffix(".json") })
    }

    /** Three Great Fairies × Silver, Golden and Hero's Keys, and a Vaati's Palace door for each tier. */
    @Test
    fun nineKeysAndThreePalaceDoors() = withCatalogs { all ->
        val c = all.getValue("stages")
        assertEquals(listOf("Chambers of Insight") + stages + "Vaati's Palace", c.regions)
        assertEquals(mapOf("silver" to 5, "golden" to 4, "heros" to 4), c.sizes())
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
    fun anniversaryEditionHasEighteenLevels() = withCatalogs { all ->
        val c = all.getValue("anniversary")
        assertEquals(listOf("Silver Door", "Golden Door", "Hero's Door"), c.regions)
        assertEquals(mapOf("realm_of_memories" to 9, "heros_trial" to 10), c.sizes())
        c.sections.forEach { s ->
            val levels = s.items.filter { it.region != null }
            assertEquals(9, levels.size, s.id)
            assertEquals(c.regions.flatMap { r -> List(3) { r } }, levels.map { it.region }, s.id)
            levels.zipWithNext().forEach { (before, level) -> assertEquals(listOf(before.id), level.requires, level.id) }
        }
        assertTrue("Master Sword" in c.item("realm_heros_3")!!.reward!!)
        assertTrue("Hurricane Spin" in c.item("trial_heros_3")!!.reward!!)
        assertEquals(listOf("heros_trial_unlock"), c.item("trial_silver_1")!!.requires)
        // 18 levels, plus opening the Hero's Trial.
        assertEquals(19, c.count(c.allDone()).total)
    }

    @Test
    fun alttpLinkUps() = withCatalogs { all ->
        val c = all.getValue("alttp_links")
        assertEquals(listOf("sword_beams", "ten_medals", "hurricane_spin", "palace_of_the_four_sword"), c.items.map { it.id })
        assertEquals(listOf("ten_medals"), c.item("hurricane_spin")!!.requires)
    }

    /** Every key can be earned again on a later run, so nothing is missable (docs/fs.md). */
    @Test
    fun nothingIsMissableAndEveryRowSaysHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id }, name)
            c.items.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.id} in $name") }
        }
    }
}
