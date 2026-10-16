package gx.zelda.hearts.ui

import gx.zelda.core.Games
import gx.zelda.hearts.data.HeartProgressProvider
import gx.zelda.hearts.model.HeartCatalog
import gx.zelda.hearts.model.HeartEntry
import gx.zelda.hearts.model.HeartKind
import gx.zelda.hearts.model.HeartPrefs
import gx.zelda.hearts.model.HeartProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeartFiltersTest {
    private fun piece(id: String, region: String, name: String, kinstone: Boolean = false, minish: Boolean = false, items: List<String> = emptyList()) =
        HeartEntry(id, HeartKind.PIECE, name, region, "$name area", "How to get $name", kinstone, if (kinstone) "Fuse with Gale" else null, minish, items)

    private val catalog = HeartCatalog(
        startingHearts = 3,
        regions = listOf("Hyrule Town", "Mount Crenel", "Lake Hylia"),
        entries = listOf(
            piece("bell", "Hyrule Town", "The town bell", items = listOf("Roc's Cape")),
            piece("school", "Hyrule Town", "Funday School yard", minish = true, items = listOf("Four Sword")),
            piece("melari", "Mount Crenel", "Melari's beanstalk", kinstone = true),
            piece("dig", "Mount Crenel", "Hermit dig cave", items = listOf("Mole Mitts")),
            HeartEntry("octorok", HeartKind.CONTAINER, "Big Octorok", "Lake Hylia", "Temple of Droplets", "Beat it", minish = true),
        ),
    )

    private fun state(filters: HeartFilters = HeartFilters(), collected: Set<String> = emptySet(), hide: Boolean = false) = HeartUiState(
        catalog = catalog,
        progress = HeartProgress(collected),
        prefs = HeartPrefs(hideUncollected = hide),
        saveFile = Games.MC.saveFiles.first(),
        saveFiles = emptyList(),
        filters = filters,
    )

    private fun HeartUiState.shown(): List<String> = groups.flatMap { (_, entries) -> entries.map { it.id } }

    @Test
    fun everythingShowsByRegionInOrder() {
        val s = state()
        assertEquals(listOf("Hyrule Town", "Mount Crenel", "Lake Hylia"), s.groups.map { it.first })
        assertEquals(listOf("bell", "school", "melari", "dig", "octorok"), s.shown())
    }

    @Test
    fun searchReadsNamesItemsAndHowTo() {
        assertEquals(listOf("dig"), state(HeartFilters(query = "mole mitts")).shown())
        assertEquals(listOf("melari"), state(HeartFilters(query = "gale")).shown())
        assertEquals(listOf("octorok"), state(HeartFilters(query = "heart container")).shown())
        assertEquals(listOf("melari", "dig"), state(HeartFilters(query = "crenel")).shown())
    }

    @Test
    fun requirementChipsMustAllMatch() {
        assertEquals(listOf("school", "octorok"), state(HeartFilters(requirements = setOf(HeartRequirement.MINISH))).shown())
        assertEquals(listOf("octorok"), state(HeartFilters(requirements = setOf(HeartRequirement.MINISH, HeartRequirement.CONTAINER))).shown())
        assertEquals(listOf("melari"), state(HeartFilters(requirements = setOf(HeartRequirement.KINSTONE))).shown())
    }

    @Test
    fun notCollectedHidesCollectedOnesButRegionCountsStayWhole() {
        val s = state(HeartFilters(missingOnly = true), collected = setOf("bell", "school"))
        assertEquals(listOf("melari", "dig", "octorok"), s.shown())
        assertEquals(HeartRegionCount(collected = 2, total = 2), s.regionCounts["Hyrule Town"])
    }

    /** A hidden entry only matches a search by region, and never a requirement chip. */
    @Test
    fun hiddenEntriesDontLeakThroughSearchOrChips() {
        val s = state(HeartFilters(query = "mole"), collected = setOf("bell"), hide = true)
        assertTrue(s.shown().isEmpty())
        assertEquals(listOf("melari", "dig"), state(HeartFilters(query = "crenel"), hide = true).shown())
        assertTrue(state(HeartFilters(requirements = setOf(HeartRequirement.KINSTONE)), hide = true).shown().isEmpty())

        val hidden = state(collected = setOf("bell"), hide = true)
        assertFalse(hidden.isHidden(catalog.entries.first { it.id == "bell" }), "collected entries always show")
        assertTrue(hidden.isHidden(catalog.entries.first { it.id == "dig" }))
        assertFalse(state(HeartFilters(revealed = setOf("dig")), hide = true).isHidden(catalog.entries.first { it.id == "dig" }))
    }

    @Test
    fun heartsAddUpByQuarters() {
        val none = catalog.total(HeartProgress())
        assertEquals(3, none.hearts)
        assertEquals(5, none.maxHearts, "3 + one container + one heart from four pieces")
        with(catalog.total(HeartProgress(setOf("bell", "school", "melari")))) {
            assertEquals(3, hearts)
            assertEquals(3, quarters)
            assertEquals("3 hearts + 3/4", heartsLabel)
        }
        with(catalog.total(HeartProgress(setOf("bell", "school", "melari", "dig", "octorok")))) {
            assertEquals(5, hearts)
            assertEquals(0, quarters)
            assertEquals(5, collected)
            assertEquals(5, total)
        }
    }

    @Test
    fun overviewRowFollowsTheSpoilerSetting() {
        val progress = HeartProgress(setOf("bell", "school"))
        val open = HeartProgressProvider.summarize(catalog, progress, HeartPrefs())
        assertEquals(2, open.done)
        assertEquals(5, open.total)
        assertEquals("3 hearts + 2/4 of 5 · Pieces 2 / 4 · Containers 0 / 1", open.secondary)
        assertEquals("Melari's beanstalk, in Mount Crenel", open.next)

        val hidden = HeartProgressProvider.summarize(catalog, progress, HeartPrefs(hideUncollected = true))
        assertEquals("2 hearts still to find in Mount Crenel", hidden.next)

        val all = HeartProgress(catalog.entries.map { it.id }.toSet())
        assertEquals("Every heart collected", HeartProgressProvider.summarize(catalog, all, HeartPrefs()).next)
    }
}
