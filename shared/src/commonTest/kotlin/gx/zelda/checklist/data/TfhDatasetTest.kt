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
 * Tri Force Heroes' checklists against the counts in docs/tfh.md, where Zelda Wiki, Prima's guide,
 * IGN and Zelda Dungeon agree, plus the links between files. Format and in-file references (ids,
 * regions, `requires`, formulas) are checked for every game by [ChecklistDatasetsTest].
 */
class TfhDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.TFH.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("tfh/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.range(label: String): Pair<Double, Double> {
        val summary = summaries.single { it.label == label }
        return evaluate(summary, ChecklistProgress()).value to evaluate(summary, allDone()).value
    }

    private val areas = listOf("Woodlands", "Riverside", "Volcano", "Ice Cavern", "Fortress", "The Dunes", "The Ruins", "Sky Realm")

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        val checklists = Games.TFH.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(listOf("adventure", "outfits", "den_of_trials"), checklists.map { it.dataFile!!.removePrefix("tfh/").removeSuffix(".json") })
    }

    /** Eight areas of four levels, three challenges a level: the in-game Completion List's 128 marks. */
    @Test
    fun adventureHas32LevelsAnd96Challenges() = withCatalogs { all ->
        val c = all.getValue("adventure")
        assertEquals(areas, c.regions)
        assertEquals(mapOf("levels" to 32, "challenges" to 96, "king_rewards" to 9), c.sizes())
        val levels = c.section("levels")!!.items
        val challenges = c.section("challenges")!!.items
        assertEquals(areas.associateWith { 4 }, levels.groupingBy { it.region!! }.eachCount())
        assertEquals(areas.associateWith { 12 }, challenges.groupingBy { it.region!! }.eachCount())
        // Each challenge names its level, in the same area, and every level has three.
        val levelsByName = levels.associateBy { it.name }
        challenges.forEach { assertEquals(it.region, levelsByName.getValue(it.area!!).region, it.id) }
        assertEquals(levels.map { it.name }.associateWith { 3 }, challenges.groupingBy { it.area!! }.eachCount())
        assertEquals(0.0 to 128.0, c.range("Completion List"))
        assertEquals(0.0 to 8.0, c.range("Areas cleared"))
    }

    /** All 12 of an area's challenges open together once its fourth level is cleared (Zelda Wiki, Prima). */
    @Test
    fun challengesOpenWithTheirAreasFourthLevel() = withCatalogs { all ->
        val c = all.getValue("adventure")
        val fourth = c.section("levels")!!.items.groupBy { it.region!! }.mapValues { (_, levels) -> levels.last().id }
        c.section("challenges")!!.items.forEach { assertEquals(listOf(fourth.getValue(it.region!!)), it.requires, it.id) }
        // King Tuft's gold material for an area needs its 12 challenges.
        val byArea = c.section("challenges")!!.items.groupBy { it.region!! }
        c.section("king_rewards")!!.items.filter { it.region != null }.forEach { reward ->
            assertEquals(byArea.getValue(reward.region!!).map { it.id }.toSet(), reward.requires.toSet(), reward.id)
        }
        assertEquals(8, c.section("king_rewards")!!.items.count { it.region != null })
    }

    @Test
    fun outfits() = withCatalogs { all ->
        val c = all.getValue("outfits")
        assertEquals(mapOf("outfits" to 38), c.sizes())
        assertEquals(38, c.items.map { it.name }.toSet().size)
        assertEquals(listOf("linebecks_uniform", "fierce_deity_armor"), c.items.filter { "Ver. 2.0.0" in it.tags }.map { it.id })
        assertEquals(listOf("ladys_ensemble"), c.item("cursed_tights")!!.requires)
    }

    /** Eight zones of five floors, in order; the last clear gives the Fierce Deity Armor, an outfit. */
    @Test
    fun denOfTrialsHasEightZones() = withCatalogs { all ->
        val den = all.getValue("den_of_trials").items
        assertEquals(8, den.size)
        den.zipWithNext().forEach { (before, zone) -> assertEquals(listOf(before.id), zone.requires) }
        assertTrue("Fierce Deity Armor" in den.last().reward.orEmpty())
        assertTrue(all.getValue("outfits").items.any { it.name == "Fierce Deity Armor" })
    }

    /** Levels and challenges can be replayed, so nothing is missable (docs/tfh.md). */
    @Test
    fun nothingIsMissable() = withCatalogs { all ->
        all.forEach { (name, c) -> assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id }, name) }
    }

    @Test
    fun everyItemSaysWhereOrHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            val vague = c.items.filter { it.howTo.isNullOrBlank() }.map(ChecklistItem::id)
            assertEquals(emptyList(), vague, name)
        }
    }
}
