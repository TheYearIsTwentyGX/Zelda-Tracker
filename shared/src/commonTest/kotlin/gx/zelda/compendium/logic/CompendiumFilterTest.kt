package gx.zelda.compendium.logic

import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.compendium.model.EntrySource
import gx.zelda.compendium.model.compendiumNumber
import gx.zelda.core.Games
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompendiumFilterTest {
    private val horse = CompendiumEntry("horse", 1, 1, "Horse", CompendiumCategory.CREATURES, locations = listOf("Hyrule Field"))
    private val bass = CompendiumEntry("hyrule_bass", 2, 2, "Hyrule Bass", CompendiumCategory.CREATURES, food = true, locations = listOf("West Necluda"))
    private val octorok = CompendiumEntry(
        "sky_octorok", null, 3, "Sky Octorok", CompendiumCategory.MONSTERS,
        source = EntrySource.MASTER_MODE, locations = listOf("Great Plateau"),
    )
    private val lynel = CompendiumEntry(
        "lynel", 3, 4, "Lynel", CompendiumCategory.MONSTERS,
        locations = listOf("Hyrule Field"), drops = listOf("Lynel Horn", "Lynel Hoof"),
    )
    private val apple = CompendiumEntry("apple", 4, 5, "Apple", CompendiumCategory.MATERIALS, locations = listOf("Hyrule Field"))
    private val catalog = CompendiumCatalog(listOf(horse, bass, octorok, lynel, apple))

    private val normal = catalog.entriesFor(masterMode = false)
    private val master = catalog.entriesFor(masterMode = true)

    private fun ids(sections: List<CompendiumSection>) = sections.flatMap { s -> s.entries.map { it.id } }

    @Test
    fun masterModeHasItsOwnEntriesAndNumbers() {
        assertEquals(listOf("horse", "hyrule_bass", "lynel", "apple"), normal.map { it.id })
        assertEquals(5, master.size)
        assertEquals(3, lynel.numberIn(masterMode = false))
        assertEquals(4, lynel.numberIn(masterMode = true))
        assertEquals(null, octorok.numberIn(masterMode = false))
        assertEquals("004", compendiumNumber(4))
        assertEquals(master, catalog.entriesFor(Games.BOTW.saveFile("master")))
        assertEquals(normal, catalog.entriesFor(Games.BOTW.saveFile("normal_2")))
    }

    @Test
    fun photographedCountIgnoresTheOtherModesEntries() {
        val photographed = setOf("horse", "sky_octorok", "not_an_entry")
        assertEquals(1, catalog.photographedCount(Games.BOTW.saveFile("normal_1"), photographed))
        assertEquals(2, catalog.photographedCount(Games.BOTW.saveFile("master"), photographed))
    }

    @Test
    fun searchMatchesNumbersInTheFilesOwnMode() {
        assertTrue(CompendiumFilter.matches(lynel, "3", masterMode = false))
        assertTrue(CompendiumFilter.matches(lynel, "#004", masterMode = true))
        assertFalse(CompendiumFilter.matches(lynel, "3", masterMode = true))
        assertFalse(CompendiumFilter.matches(horse, "11", masterMode = false), "numbers match exactly")
    }

    @Test
    fun searchMatchesNamesLocationsAndDrops() {
        assertTrue(CompendiumFilter.matches(lynel, "  lyn ", masterMode = false))
        assertTrue(CompendiumFilter.matches(bass, "necluda", masterMode = false))
        assertTrue(CompendiumFilter.matches(lynel, "hoof", masterMode = false))
        assertFalse(CompendiumFilter.matches(apple, "lynel", masterMode = false))
        assertTrue(CompendiumFilter.matches(apple, "", masterMode = false))
    }

    @Test
    fun allTabHeadsEachCategoryAndSplitsCreatures() {
        val sections = CompendiumFilter.sections(normal, null, "", missingOnly = false, photographed = emptySet(), masterMode = false)
        assertEquals(listOf("Creatures", "Creatures · Food", "Monsters", "Materials"), sections.map { it.title })
        assertEquals(listOf("horse", "hyrule_bass", "lynel", "apple"), ids(sections))
    }

    @Test
    fun categoryTabsOnlyHeadCreatures() {
        val monsters = CompendiumFilter.sections(master, CompendiumCategory.MONSTERS, "", false, emptySet(), masterMode = true)
        assertEquals(listOf<String?>(null), monsters.map { it.title })
        assertEquals(listOf("sky_octorok", "lynel"), ids(monsters))

        val creatures = CompendiumFilter.sections(normal, CompendiumCategory.CREATURES, "", false, emptySet(), masterMode = false)
        assertEquals(listOf("Creatures", "Creatures · Food"), creatures.map { it.title })
    }

    @Test
    fun missingOnlyHidesPhotographedButKeepsSectionTotals() {
        val sections = CompendiumFilter.sections(normal, null, "", missingOnly = true, photographed = setOf("horse", "lynel"), masterMode = false)
        assertEquals(listOf("hyrule_bass", "apple"), ids(sections))
        assertEquals(listOf("Creatures · Food", "Materials"), sections.map { it.title }, "emptied sections are dropped")

        val creatures = CompendiumFilter.sections(normal, null, "bass", false, setOf("horse"), masterMode = false).single()
        assertEquals(0 to 1, creatures.photographed to creatures.total, "counts cover the section, not the search")
    }

    @Test
    fun progressCountsEveryCategory() {
        val progress = CompendiumFilter.progress(master, setOf("horse", "hyrule_bass", "sky_octorok"))
        assertEquals(CompendiumCategory.entries, progress.map { it.category })
        assertEquals(listOf(2 to 2, 1 to 2, 0 to 1, 0 to 0, 0 to 0), progress.map { it.photographed to it.total })
    }
}
