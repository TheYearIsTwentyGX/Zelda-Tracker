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
 * Four Swords Adventures' checklists against the counts in docs/fsa.md, where Zelda Wiki, Zelda
 * Dungeon and the manual agree, plus the links between files. Format and in-file references (ids,
 * regions, `requires`, formulas) are checked for every game by [ChecklistDatasetsTest].
 */
class FsaDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.FSA.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("fsa/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.summary(label: String) = summaries.single { it.label == label }

    /** Zelda Wiki's level names, as the game shows them, in order. */
    private val levels = listOf(
        "Whereabouts of the Wind", "Eastern Hyrule", "Death Mountain", "Near the Fields",
        "The Dark World", "The Desert of Doubt", "Frozen Hyrule", "Realm of the Heavens",
    )

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        val checklists = Games.FSA.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(listOf("hyrulean_adventure", "tingle_and_shadow_battle"), checklists.map { it.dataFile!!.removePrefix("fsa/").removeSuffix(".json") })
    }

    /** 8 levels × 3 stages, each needing the one before, numbered 1-1 to 8-3. */
    @Test
    fun twentyFourStagesInOrder() = withCatalogs { all ->
        val c = all.getValue("hyrulean_adventure")
        assertEquals(levels, c.regions)
        val stages = c.section("stages")!!.items
        assertEquals(24, stages.size)
        assertEquals(levels.flatMap { l -> List(3) { l } }, stages.map { it.region })
        assertEquals(levels.indices.flatMap { l -> (1..3).map { "Stage ${l + 1}-$it" } }, stages.map { it.area })
        assertEquals(emptyList(), stages.first().requires)
        stages.zipWithNext().forEach { (before, stage) -> assertEquals(listOf(before.id), stage.requires, stage.id) }
        assertTrue(stages.all { it.tags.isNotEmpty() }, "every stage names its boss")
        assertEquals("palace_of_winds", stages.last().id)
    }

    /** Six Maidens and Zelda, each freed at the end of a level's third stage, and four Royal Jewels. */
    @Test
    fun maidensAndRoyalJewels() = withCatalogs { all ->
        val c = all.getValue("hyrulean_adventure")
        assertEquals(7.0, c.evaluate(c.summary("Maidens rescued"), c.allDone()).max)
        assertEquals(4.0, c.evaluate(c.summary("Royal Jewels"), c.allDone()).max)
        val freed = c.items.filter { it.reward.orEmpty().endsWith("rescued") }
        assertEquals(
            listOf("Blue", "Yellow", "Green", "White", "Red", "Purple").map { "$it Maiden rescued" } + "Princess Zelda rescued",
            freed.map { it.reward },
        )
        assertTrue(freed.all { it.area!!.endsWith("-3") && "Maiden" in it.tags })
        val jewels = c.items.filter { "Royal Jewel" in it.tags }
        assertEquals(listOf("Blue", "Green", "Red", "Purple").map { "$it Royal Jewel" }, jewels.map { it.reward })
        assertTrue(jewels.all { "Big Dark Stalfos" in it.tags })
    }

    /** One mini-game per level, opened by its second stage, plus the Dark Shadow Battle arenas. */
    @Test
    fun tinglesTowerAndShadowBattle() = withCatalogs { all ->
        val c = all.getValue("tingle_and_shadow_battle")
        assertEquals(mapOf("tingles_tower" to 8, "shadow_battle" to 1), c.sections.associate { it.id to it.items.size })
        val games = c.section("tingles_tower")!!.items
        assertEquals(levels, games.map { it.region })
        val secondStages = all.getValue("hyrulean_adventure").items.filter { it.area!!.endsWith("-2") }
        assertEquals(secondStages.map { "${it.name} (stage ${it.area!!.removePrefix("Stage ")})" }, games.map { it.after })
        assertTrue(c.items.all { "Multiplayer" in it.tags })
    }

    /** Every stage can be replayed, so nothing is missable (docs/fsa.md). */
    @Test
    fun nothingIsMissableAndEveryRowSaysHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id }, name)
            c.items.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.id} in $name") }
        }
    }
}
