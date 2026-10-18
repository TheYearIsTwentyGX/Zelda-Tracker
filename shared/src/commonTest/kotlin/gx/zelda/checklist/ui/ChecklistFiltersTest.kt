package gx.zelda.checklist.ui

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.model.ChecklistCount
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.SaveFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecklistFiltersTest {
    private val catalog = ChecklistFixture.catalog

    private fun state(
        filters: ChecklistFilters = ChecklistFilters(),
        done: Set<String> = emptySet(),
        hide: Boolean = false,
    ): ChecklistUiState {
        val progress = done.fold(ChecklistProgress()) { p, id -> p.withDone(catalog.item(id)!!, true) }
        val file = SaveFile("file_1", "File 1")
        return ChecklistUiState(catalog, progress, ChecklistPrefs(hideUnchecked = hide), file, emptyList(), filters)
    }

    private fun ChecklistUiState.ids(): List<String> = groups.flatMap { (_, items) -> items.map { it.id } }

    @Test
    fun allShowsEverySectionGroupedByRegion() {
        val s = state()
        assertTrue(s.showTabs)
        assertNull(s.section)
        assertEquals(listOf("Hyrule Field", "Jabul Waters", "Upgrades"), s.groups.map { it.first })
        assertEquals(7, s.ids().size)
    }

    @Test
    fun aSectionTabShowsOnlyItsItemsAndCounts() {
        val s = state(ChecklistFilters(section = "pieces"), done = setOf("field_cave", "boss_jabul"))
        assertEquals(listOf("field_cave", "field_race", "field_tree", "zora_cove", "river_zora"), s.ids())
        assertEquals(ChecklistCount(1, 3), s.groupCounts["Hyrule Field"])
        assertEquals(ChecklistCount(0, 2), s.groupCounts["Jabul Waters"], "the container is on another tab")
        assertEquals(listOf("Hearts"), s.summaries.map { it.label })

        val upgrades = state(ChecklistFilters(section = "upgrades"))
        assertEquals(listOf("Hearts", "Crystals spent"), upgrades.summaries.map { it.label })
    }

    @Test
    fun aSeparateSectionIsOnlyOnItsOwnTab() {
        val v = ChecklistFixture.versions
        val file = SaveFile("file_1", "File 1")
        fun state(section: String?) = ChecklistUiState(v, ChecklistProgress(), ChecklistPrefs(), file, emptyList(), ChecklistFilters(section = section))
        assertEquals(listOf("well", "maze"), state(null).ids())
        assertEquals(ChecklistCount(0, 2), state(null).count)
        assertEquals(listOf("remake_well", "remake_pond", "remake_tree"), state("remake_pieces").ids())
        assertEquals(ChecklistCount(0, 3), state("remake_pieces").groupCounts.getValue("Mabe Village"))
    }

    @Test
    fun aStaleSectionFallsBackToAll() = assertEquals(7, state(ChecklistFilters(section = "gone")).ids().size)

    @Test
    fun incompleteOnlyHidesDoneItems() {
        val s = state(ChecklistFilters(incompleteOnly = true), done = setOf("field_cave", "field_race"))
        assertFalse("field_cave" in s.ids())
        assertEquals(5, s.ids().size)
        assertEquals(ChecklistCount(2, 3), s.groupCounts["Hyrule Field"], "headers count the whole region")
    }

    @Test
    fun missableOnly() = assertEquals(listOf("field_race"), state(ChecklistFilters(missableOnly = true)).ids())

    @Test
    fun searchReadsNamesPlacesRewardsAndTags() {
        assertEquals(listOf("field_cave"), state(ChecklistFilters(query = " rock ")).ids())
        assertEquals(listOf("river_zora"), state(ChecklistFilters(query = "minigame")).ids())
        assertEquals(listOf("field_race"), state(ChecklistFilters(query = "rupees")).ids())
        assertEquals(listOf("boss_jabul"), state(ChecklistFilters(query = "heart containers")).ids(), "the section title")
        assertEquals(listOf("sword"), state(ChecklistFilters(query = "level 3")).ids())
        assertEquals(listOf("zora_cove", "river_zora", "boss_jabul"), state(ChecklistFilters(query = "jabul")).ids())
    }

    @Test
    fun hiddenRowsOnlyMatchTheirHeading() {
        val hidden = state(ChecklistFilters(query = "rock"), hide = true)
        assertEquals(emptyList(), hidden.ids(), "a hidden row's name can't be searched")
        assertEquals(listOf("field_cave", "field_race", "field_tree"), state(ChecklistFilters(query = "hyrule field"), hide = true).ids())
        assertEquals(listOf("field_cave"), state(ChecklistFilters(query = "rock"), done = setOf("field_cave"), hide = true).ids())
        assertEquals(listOf("field_cave"), state(ChecklistFilters(query = "rock", revealed = setOf("field_cave")), hide = true).ids())
        assertEquals(emptyList(), state(ChecklistFilters(missableOnly = true), hide = true).ids(), "the chip can't give away which are missable")
    }

    @Test
    fun aStartedTieredItemIsNoLongerHidden() {
        val sword = catalog.item("sword")!!
        val s = state(hide = true)
        assertTrue(s.isHidden(sword))
        assertFalse(s.copy(progress = s.progress.withLevel(sword, 1)).isHidden(sword))
    }

    @Test
    fun requirementsAreNamedUnlessHidden() {
        val cove = catalog.item("zora_cove")!!
        assertEquals(listOf("Jabul Ruins boss" to false), state().requirements(cove))
        assertEquals(listOf("Unchecked container" to false), state(hide = true).requirements(cove))
        assertEquals(listOf("Jabul Ruins boss" to true), state(done = setOf("boss_jabul"), hide = true).requirements(cove))
    }
}
