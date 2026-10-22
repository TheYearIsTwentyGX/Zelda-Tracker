package gx.zelda.checklist.ui

import gx.zelda.checklist.StoryFixture
import gx.zelda.checklist.StoryFixture.gate
import gx.zelda.checklist.StoryFixture.progress
import gx.zelda.checklist.model.ChecklistCount
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.SaveFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Story gating in the list: what it hides, and what it leaves alone. */
class ChecklistStoryGateTest {
    private val catalog = StoryFixture.gated

    private fun state(
        gate: StoryGate = gate(),
        progress: ChecklistProgress = progress(),
        filters: ChecklistFilters = ChecklistFilters(),
        hide: Boolean = false,
    ) = ChecklistUiState(catalog, progress, ChecklistPrefs(hideUnchecked = hide), SaveFile("file_1", "File 1"), emptyList(), filters, gate)

    private fun ChecklistUiState.ids(): List<String> = groups.flatMap { (_, items) -> items.map { it.id } }

    @Test
    fun lockedRowsAreLeftOutOnlyWithGatingOn() {
        assertEquals(listOf("start", "next_to_it", "typo"), state().ids())
        assertEquals(catalog.counted.map { it.id }.sorted(), state(gate(enabled = false)).ids().sorted())
        assertEquals(listOf("start", "forest_piece", "next_to_it", "typo"), state(gate("forest")).ids())
    }

    @Test
    fun rowsTickedAheadOfTheStoryStayShown() {
        val s = state(progress = progress("mountain_piece" to 1, "quiver" to 1))
        assertEquals(listOf("start", "next_to_it", "typo", "mountain_piece", "quiver"), s.ids())
    }

    /** Counts keep their true totals; the locked are counted separately, by heading. */
    @Test
    fun countsAreUnchangedAndTheLockedAreCountedByHeading() {
        val s = state(progress = progress("start" to 1))
        assertEquals(ChecklistCount(1, 8), s.count)
        assertEquals(ChecklistCount(1, 4), s.groupCounts["Field"])
        assertEquals(ChecklistCount(0, 2), s.groupCounts["Mountain"])
        assertEquals(mapOf("Field" to 1, "Mountain" to 2, "Upgrades" to 1), s.lockedCounts)
        assertEquals(4, s.lockedOnTab)
        assertEquals(3, s.lockedWithoutRows, "Mountain and Upgrades have no rows to show")
        assertEquals(mapOf("Field" to 1, "Mountain" to 2), state(filters = ChecklistFilters(section = "pieces")).lockedCounts)
    }

    @Test
    fun searchAndTheMissableChipNeverFindALockedRow() {
        assertTrue(state(filters = ChecklistFilters(query = "Mountain")).groups.isEmpty())
        assertTrue(state(filters = ChecklistFilters(query = "Mountain"), hide = true).groups.isEmpty(), "not even by its heading")
        assertTrue(state(filters = ChecklistFilters(missableOnly = true)).groups.isEmpty())
        assertEquals(listOf("forest_piece"), state(gate("forest"), filters = ChecklistFilters(missableOnly = true)).ids())
    }

    @Test
    fun markAllLeavesLockedItemsAloneAndSaysSo() {
        val s = state()
        assertEquals(listOf("start", "next_to_it", "typo"), s.bulkItems().map { it.id })
        assertEquals("4 hearts the story hasn't unlocked yet aren't changed.", s.bulkScope().note)
        assertEquals("1 piece the story hasn't unlocked yet isn't changed.", s.bulkScope("Field").note)
        assertEquals(null, state(gate("forest", "fire", "water")).bulkScope("Field").note)
    }

    @Test
    fun aLockedRequirementIsNotNamed() {
        val needs = catalog.item("next_to_it")!!
        assertEquals(listOf("Uncollected piece" to false), state().requirements(needs))
        assertEquals(listOf("Forest piece" to false), state(gate("forest")).requirements(needs))
    }

    @Test
    fun thePageNamesTheStepsWhenThereIsNoAfterText() {
        assertEquals("After Fire Temple and Water Temple", state(gate(enabled = false)).after(catalog.item("both")!!))
        assertEquals(null, state().after(catalog.item("start")!!))
    }
}
