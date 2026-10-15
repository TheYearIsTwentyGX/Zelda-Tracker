package gx.zelda.bosses.ui

import gx.zelda.bosses.model.BossCatalog
import gx.zelda.bosses.model.BossData
import gx.zelda.bosses.model.BossKind
import gx.zelda.bosses.model.BossPrefs
import gx.zelda.bosses.model.BossProgress
import gx.zelda.bosses.model.BossSpawn
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.core.Games
import gx.zelda.core.SaveFile
import kotlin.test.Test
import kotlin.test.assertEquals

class BossFiltersTest {
    private fun spawn(
        id: Long,
        kind: String,
        variant: String,
        region: String,
        place: String,
        dlc: Boolean = false,
        masterModeOnly: Boolean = false,
    ) = BossSpawn(id, "A-1", kind, variant, region, 0.0, 0.0, 0.0, place, dlc = dlc, masterModeOnly = masterModeOnly)

    private fun entry(id: String, name: String) = CompendiumEntry(id, 1, 1, name, CompendiumCategory.MONSTERS)

    private val data = BossData(
        BossCatalog(
            coordinates = "",
            regions = listOf("Great Plateau", "Faron", "Wasteland"),
            kinds = listOf(
                BossKind("hinox", "Hinox", "Hinoxes", "Medal of Honor: Hinox", 3, listOf("hinox", "blue_hinox", "stalnox")),
                BossKind("molduga", "Molduga", "Moldugas", "Medal of Honor: Molduga", 1, listOf("molduga", "molduking")),
                BossKind("lynel", "Lynel", "Lynels", variants = listOf("lynel", "white_maned_lynel")),
            ),
            spawns = listOf(
                spawn(1, "hinox", "hinox", "Faron", "Eventide Island"),
                spawn(2, "hinox", "blue_hinox", "Faron", "Lurelin Village"),
                spawn(3, "hinox", "stalnox", "Great Plateau", "Mount Hylia"),
                spawn(4, "molduga", "molduga", "Wasteland", "Southern Oasis"),
                spawn(5, "molduga", "molduking", "Wasteland", "East Barrens", dlc = true),
                spawn(6, "lynel", "lynel", "Faron", "Keelay Plain"),
                spawn(7, "lynel", "white_maned_lynel", "Great Plateau", "Temple of Time", masterModeOnly = true),
            ),
        ),
        CompendiumCatalog(
            listOf(
                entry("hinox", "Hinox"), entry("blue_hinox", "Blue Hinox"), entry("stalnox", "Stalnox"),
                entry("molduga", "Molduga"), entry("molduking", "Molduking"),
                entry("lynel", "Lynel"), entry("white_maned_lynel", "White-Maned Lynel"),
            ),
        ),
    )

    private fun state(
        filters: BossFilters = BossFilters(),
        defeated: Set<String> = emptySet(),
        found: Set<String> = emptySet(),
        hideUndiscovered: Boolean = false,
        file: SaveFile = Games.BOTW.saveFiles.first(),
    ) = BossUiState(
        data = data,
        progress = BossProgress(defeated = defeated, found = found),
        prefs = BossPrefs(hideUndiscovered = hideUndiscovered),
        saveFile = file,
        saveFiles = emptyList(),
        filters = filters,
    )

    private fun BossUiState.ids(kind: String) =
        pages.first { it.kind.id == kind }.groups.flatMap { (_, spawns) -> spawns.map { it.id } }

    @Test
    fun eachKindIsATabGroupedByRegionInTowerOrder() {
        val s = state()
        assertEquals(listOf("hinox", "molduga", "lynel"), s.pages.map { it.kind.id })
        assertEquals(listOf("3", "1", "2"), s.ids("hinox"))
        assertEquals(listOf("Great Plateau", "Faron"), s.pages.first().groups.map { it.first })
    }

    @Test
    fun masterModeOnlyBossesAreOnlyOnTheMasterModeFile() {
        assertEquals(listOf("6"), state().ids("lynel"))
        assertEquals(listOf("7", "6"), state(file = Games.BOTW.saveFiles.first { it.masterMode }).ids("lynel"))
    }

    @Test
    fun incompleteOnlyDropsDefeatedBosses() {
        assertEquals(listOf("3", "2"), state(BossFilters(incompleteOnly = true), defeated = setOf("1")).ids("hinox"))
    }

    @Test
    fun variantFilterOnlyNarrowsItsOwnKind() {
        val s = state(BossFilters(variants = setOf("blue_hinox", "stalnox")))
        assertEquals(listOf("3", "2"), s.ids("hinox"))
        assertEquals(listOf("4", "5"), s.ids("molduga"), "no Molduga variant is picked, so every Molduga shows")
    }

    @Test
    fun searchMatchesPlaceVariantRegionAndId() {
        assertEquals(listOf("1"), state(BossFilters(query = "eventide")).ids("hinox"))
        assertEquals(listOf("2"), state(BossFilters(query = "blue")).ids("hinox"))
        assertEquals(listOf("1", "2"), state(BossFilters(query = " faron ")).ids("hinox"))
        assertEquals(listOf("5"), state(BossFilters(query = "5")).ids("molduga"))
    }

    @Test
    fun hiddenBossesOnlyMatchByRegionAndNeverByVariant() {
        val hidden = state(BossFilters(query = "eventide"), hideUndiscovered = true)
        assertEquals(emptyList(), hidden.ids("hinox"))
        assertEquals(listOf("1", "2"), state(BossFilters(query = "faron"), hideUndiscovered = true).ids("hinox"))
        assertEquals(emptyList(), state(BossFilters(variants = setOf("blue_hinox")), hideUndiscovered = true).ids("hinox"))
        // Found or revealed, it matches like any other row.
        assertEquals(listOf("1"), state(BossFilters(query = "eventide"), found = setOf("1"), hideUndiscovered = true).ids("hinox"))
        assertEquals(listOf("2"), state(BossFilters(query = "blue", revealed = setOf("2")), hideUndiscovered = true).ids("hinox"))
    }

    @Test
    fun countsIgnoreFiltersAndTheMedalSkipsDlc() {
        val s = state(BossFilters(query = "southern"), defeated = setOf("4", "5"), found = setOf("4", "5"))
        val molduga = s.pages.first { it.kind.id == "molduga" }
        assertEquals(2 to 2, molduga.tally.defeated to molduga.tally.total)
        assertEquals(1 to 1, molduga.tally.medalDefeated to molduga.tally.medalTotal)
        assertEquals(listOf("5"), molduga.uncounted.map { it.id })
        assertEquals("Molduga 1 / 1 · Medal of Honor: Molduga earned (from Kilton)", molduga.tally.headline)
        assertEquals("Hinox 0 / 3 · Medal of Honor: Hinox", s.pages.first().tally.headline)
        assertEquals("Lynel 0 / 1 · no Medal of Honor for Lynels", s.pages.last().tally.headline)
        assertEquals(2, s.pages.first { it.kind.id == "molduga" }.regionCounts.getValue("Wasteland").defeated)
    }

    @Test
    fun variantChipsOnlyOfferVariantsOnThisFile() {
        assertEquals(listOf("lynel"), state().pages.last().variants)
        assertEquals(listOf("hinox", "blue_hinox", "stalnox"), state().pages.first().variants)
    }
}
