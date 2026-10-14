package gx.zelda.koroks.ui

import gx.zelda.core.Games
import gx.zelda.koroks.model.HestuCosts
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokData
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
import gx.zelda.koroks.model.KorokPuzzleType
import gx.zelda.koroks.model.KorokPuzzleTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class KorokFiltersTest {
    private fun korok(id: String, region: String, place: String, type: String, internal: String = "Rock Lift") =
        Korok(id, 0, "A-1", region, 0.0, 0.0, 0.0, place, internal, puzzleType = type, typeSource = "internal")

    private fun type(id: String, name: String) = KorokPuzzleType(id, name, "Rocks", "", listOf("rule"), emptyList(), emptyList())

    private val data = KorokData(
        KorokCatalog(
            coordinates = "",
            regions = listOf("Great Plateau", "Lake"),
            hestu = HestuCosts(emptyList()),
            koroks = listOf(
                korok("P01", "Great Plateau", "Oman Au Shrine", "lift_rock"),
                korok("P02", "Great Plateau", "Mount Hylia", "race", "Goal Ring (Race)"),
                korok("L26", "Lake", "Bridge of Hylia", "other", "Take the Stick"),
                korok("L08", "Lake", "Lake Hylia", "race", "Goal Ring (Race)"),
            ),
        ),
        KorokPuzzleTypes(
            groups = listOf("Rocks"),
            types = listOf(type("lift_rock", "Lone rock"), type("race", "Race to the ring"), type("other", "Other")),
        ),
    )

    private fun state(
        filters: KorokFilters = KorokFilters(),
        completed: Set<String> = emptySet(),
        found: Set<String> = emptySet(),
        hideUndiscovered: Boolean = false,
    ) = KorokUiState(
        data = data,
        progress = KorokProgress(completed = completed, found = found),
        prefs = KorokPrefs(hideUndiscovered = hideUndiscovered),
        saveFile = Games.BOTW.saveFiles.first(),
        saveFiles = emptyList(),
        filters = filters,
    )

    private fun KorokUiState.ids() = matching.map { it.id }

    @Test
    fun everythingShowsByDefaultInRegionOrder() {
        assertEquals(listOf("P01", "P02", "L26", "L08"), state().ids())
        assertEquals(listOf("Great Plateau", "Lake"), state().groups.map { it.first })
    }

    @Test
    fun incompleteOnlyDropsCollectedSeeds() {
        assertEquals(listOf("P02", "L08"), state(KorokFilters(incompleteOnly = true), completed = setOf("P01", "L26")).ids())
    }

    @Test
    fun puzzleTypesFilterTogether() {
        assertEquals(listOf("P02", "L08"), state(KorokFilters(types = setOf("race"))).ids())
        assertEquals(listOf("P01", "L26"), state(KorokFilters(types = setOf("lift_rock", "other"))).ids())
    }

    @Test
    fun searchMatchesPlaceTypeNameObjmapNameAndId() {
        assertEquals(listOf("P01"), state(KorokFilters(query = "oman")).ids())
        assertEquals(listOf("P02", "L08"), state(KorokFilters(query = "race to")).ids())
        assertEquals(listOf("L26"), state(KorokFilters(query = "stick")).ids())
        assertEquals(listOf("L08"), state(KorokFilters(query = " l08 ")).ids())
        assertEquals(listOf("P02", "L26", "L08"), state(KorokFilters(query = "hylia")).ids())
    }

    @Test
    fun hiddenKoroksOnlyMatchTheirRegion() {
        val hidden = state(KorokFilters(query = "hylia"), found = setOf("L08"), hideUndiscovered = true)
        // P02 (Mount Hylia) and L26 (Bridge of Hylia) are hidden, so their places don't match; L08 is found.
        assertEquals(listOf("L08"), hidden.ids())
        assertEquals(listOf("P01", "P02"), state(KorokFilters(query = "plateau"), hideUndiscovered = true).ids())
    }

    @Test
    fun puzzleTypeFilterNeverShowsHiddenKoroks() {
        val s = state(KorokFilters(types = setOf("race")), found = setOf("L08"), hideUndiscovered = true)
        assertEquals(listOf("L08"), s.ids())
        val revealed = state(KorokFilters(types = setOf("race"), revealed = setOf("P02")), found = setOf("L08"), hideUndiscovered = true)
        assertEquals(listOf("P02", "L08"), revealed.ids())
    }

    @Test
    fun regionCountsIgnoreTheFilters() {
        val s = state(KorokFilters(types = setOf("race")), completed = setOf("P01"), found = setOf("L26"))
        assertEquals(RegionCount(found = 1, collected = 1, total = 2), s.regionCounts["Great Plateau"])
        assertEquals(RegionCount(found = 1, collected = 0, total = 2), s.regionCounts["Lake"])
        assertEquals(1, s.collected)
        assertEquals(2, s.found)
    }
}
