package gx.zelda.checklist.ui

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.BulkAction
import gx.zelda.core.SaveFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each "mark all complete" toggle on a checklist covers: the rows a tab or region shows. */
class ChecklistBulkScopeTest {
    private val catalog = ChecklistFixture.catalog

    private fun state(
        filters: ChecklistFilters = ChecklistFilters(),
        levels: Map<String, Int> = emptyMap(),
        hide: Boolean = false,
        catalog: ChecklistCatalog = this.catalog,
    ) = ChecklistUiState(catalog, ChecklistProgress(levels), ChecklistPrefs(hideUnchecked = hide), SaveFile("file_1", "File 1"), emptyList(), filters)

    private fun ChecklistUiState.bulkIds(group: String? = null) = bulkItems(group).map { it.id }

    @Test
    fun aTabCoversItsRowsAndARegionItsCard() {
        val pieces = state(ChecklistFilters(section = "pieces"), levels = mapOf("field_cave" to 1))
        assertEquals(listOf("field_cave", "field_race", "field_tree", "zora_cove", "river_zora"), pieces.bulkIds())
        assertEquals(listOf("zora_cove", "river_zora"), pieces.bulkIds("Jabul Waters"), "the container is on another tab")
        val scope = pieces.bulkScope()
        assertEquals("Pieces of Heart", scope.label)
        assertEquals("piece" to "pieces", scope.noun to scope.plural)
        assertEquals(1 to 5, scope.done to scope.total)
        assertEquals("Mark 4 pieces complete?", scope.confirmTitle)

        val region = state().bulkScope("Jabul Waters")
        assertEquals("Jabul Waters", region.label)
        assertEquals(listOf("zora_cove", "river_zora", "boss_jabul"), state().bulkIds("Jabul Waters"), "on All, every section's rows there")
        assertEquals("heart" to "hearts", region.noun to region.plural, "rows from two sections use the file's noun")
        assertTrue(state().bulkIds("Nowhere").isEmpty())
    }

    @Test
    fun allLeavesOutSeparateSectionsAndSaysSo() {
        val versions = ChecklistFixture.versions
        val all = state(catalog = versions)
        assertEquals(listOf("well", "maze"), all.bulkIds())
        assertEquals("Everything", all.bulkScope().label)
        assertEquals("Remake isn't changed: it counts on its own tab.", all.bulkScope().note)
        assertEquals(listOf("well", "maze"), all.bulkIds("Mabe Village"), "a region card on All leaves it out too")

        val remake = state(ChecklistFilters(section = "remake_pieces"), catalog = versions)
        assertEquals(listOf("remake_well", "remake_pond", "remake_tree"), remake.bulkIds())
        assertNull(remake.bulkScope().note)
    }

    @Test
    fun tieredItemsCountDoneOnlyAtTheirTopLevel() {
        val upgrades = { level: Int -> state(ChecklistFilters(section = "upgrades"), levels = mapOf("sword" to level)) }
        assertEquals(false, upgrades(0).bulkScope().checked)
        assertNull(upgrades(2).bulkScope().checked, "started but not done")
        assertEquals(BulkAction.MARK, upgrades(2).bulkScope().action)
        assertEquals("Anything with levels goes to its top level.", upgrades(2).bulkScope().note)
        assertEquals(BulkAction.CLEAR, upgrades(3).bulkScope().action)
        assertEquals("Anything with levels goes back to none.", upgrades(3).bulkScope().note)
    }

    @Test
    fun filtersNarrowTheScopeToTheRowsShown() {
        val search = state(ChecklistFilters(query = "jabul"))
        assertTrue(search.filtered)
        assertEquals(listOf("zora_cove", "river_zora", "boss_jabul"), search.bulkIds())
        assertEquals("Mark 3 shown complete", search.bulkScope().actionLabel)

        val notDone = state(ChecklistFilters(section = "pieces", incompleteOnly = true), levels = mapOf("field_cave" to 1))
        assertFalse("field_cave" in notDone.bulkIds())
        assertFalse(state().filtered)
        assertTrue(state(ChecklistFilters(query = "nothing like this")).bulkIds().isEmpty())
    }

    @Test
    fun hiddenRowsAreCoveredButNeverNamed() {
        val hidden = state(ChecklistFilters(section = "pieces"), hide = true)
        assertEquals(5, hidden.bulkIds().size, "marking hidden rows is the user's choice")
        val scope = hidden.bulkScope()
        val text = scope.confirmTitle + scope.confirmText("File 1") + scope.actionLabel + scope.contentDescription
        catalog.items.forEach { assertFalse(it.name in text, "names ${it.name}") }
    }
}
