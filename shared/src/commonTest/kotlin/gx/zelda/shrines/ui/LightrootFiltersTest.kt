package gx.zelda.shrines.ui

import gx.zelda.core.Discoveries
import gx.zelda.core.Games
import gx.zelda.shrines.model.BlessingCurrency
import gx.zelda.shrines.model.Lightroot
import gx.zelda.shrines.model.OrbUpgrades
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrineLayer
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.TrialType
import kotlin.test.Test
import kotlin.test.assertEquals

/** The TotK tracker's search, incomplete filter and spoiler hiding, on both tabs. */
class LightrootFiltersTest {
    private val catalog = ShrineCatalog(
        regions = listOf("Hebra", "Necluda"),
        upgrades = OrbUpgrades(4, 3, 40, 1, 10, 4, BlessingCurrency.LIGHT_OF_BLESSING),
        shrines = listOf(
            Shrine("oromuwak", "Oromuwak", "Necluda", "Rauru's Blessing", TrialType.BLESSING, location = "A cave"),
            Shrine("joniu", "Joniu", "Hebra", "A Balancing Act", TrialType.PUZZLE, quest = "The North Hebra Crystal", location = "North Hebra"),
            Shrine("ukouh", "Ukouh", "Hebra", "The Ability to Combine", TrialType.PUZZLE, layer = ShrineLayer.SKY, location = "Sky"),
        ),
        lightroots = listOf(
            Lightroot("kawumoro", "Kawumoro", "oromuwak"),
            Lightroot("uinoj", "Uinoj", "joniu"),
        ),
    )

    private fun state(
        filters: ShrineFilters = ShrineFilters(),
        progress: ShrineProgress = ShrineProgress(),
        hideUndiscovered: Boolean = false,
    ) = ShrineUiState(
        catalog = catalog,
        progress = progress,
        prefs = ShrinePrefs(hideUndiscovered = hideUndiscovered),
        saveFile = Games.TOTK.saveFiles.first(),
        saveFiles = emptyList(),
        filters = filters,
        beastsFreed = null,
    )

    private fun ShrineUiState.roots() = matchingLightroots.map { it.id }

    private fun ShrineUiState.shrines() = matching.map { it.id }

    @Test
    fun lightrootsAreListedUnderTheirShrinesRegions() {
        assertEquals(
            listOf("Hebra" to listOf("uinoj"), "Necluda" to listOf("kawumoro")),
            catalog.lightrootsByRegion(catalog.lightroots).map { (region, roots) -> region to roots.map { it.id } },
        )
    }

    @Test
    fun theLinkWorksBothWays() {
        val oromuwak = catalog.shrine("oromuwak")!!
        assertEquals("kawumoro", catalog.lightrootOf(oromuwak)?.id)
        assertEquals(oromuwak, catalog.shrineOf(catalog.lightroots.first()))
        assertEquals(null, catalog.lightrootOf(catalog.shrine("ukouh")!!), "sky shrines have no Lightroot")
    }

    @Test
    fun incompleteMeansUnlitOnTheLightrootTab() {
        val progress = ShrineProgress(completed = setOf("joniu"), lightroots = Discoveries(completed = setOf("kawumoro")))
        val s = state(ShrineFilters(incompleteOnly = true), progress)
        assertEquals(listOf("uinoj"), s.roots(), "a completed shrine doesn't light its root")
        assertEquals(listOf("oromuwak", "ukouh"), s.shrines(), "a lit root doesn't complete its shrine")
    }

    @Test
    fun searchMatchesALightrootByItsOwnNameItsShrineOrRegion() {
        assertEquals(listOf("kawumoro"), state(ShrineFilters(query = "kawum")).roots())
        assertEquals(listOf("kawumoro"), state(ShrineFilters(query = "oromuwak")).roots())
        assertEquals(listOf("uinoj"), state(ShrineFilters(query = "hebra")).roots())
        assertEquals(emptyList(), state(ShrineFilters(query = "cave")).roots(), "not the shrine's location")
        assertEquals(listOf("oromuwak"), state(ShrineFilters(query = "kawumoro")).shrines(), "a shrine matches its root's name")
    }

    @Test
    fun hiddenLightrootsOnlyMatchTheirRegion() {
        fun hidden(query: String, progress: ShrineProgress = ShrineProgress(), revealed: Set<String> = emptySet()) =
            state(ShrineFilters(query = query, revealedLightroots = revealed), progress, hideUndiscovered = true).roots()

        assertEquals(emptyList(), hidden("kawumoro"), "name")
        assertEquals(emptyList(), hidden("oromuwak"), "shrine name")
        assertEquals(listOf("kawumoro"), hidden("necluda"), "the region isn't hidden")

        assertEquals(listOf("kawumoro"), hidden("kawumoro", ShrineProgress(lightroots = Discoveries(found = setOf("kawumoro")))))
        assertEquals(listOf("kawumoro"), hidden("kawumoro", revealed = setOf("kawumoro")))
        assertEquals(
            emptyList(),
            hidden("kawumoro", ShrineProgress(found = setOf("oromuwak"))),
            "finding the shrine doesn't reveal the root below it",
        )
    }

    @Test
    fun aHiddenShrineDoesNotMatchItsLightrootsName() {
        val s = state(ShrineFilters(query = "kawumoro"), hideUndiscovered = true)
        assertEquals(emptyList(), s.shrines())
        val revealedRoot = state(ShrineFilters(query = "kawumoro"), ShrineProgress(lightroots = Discoveries(found = setOf("kawumoro"))), hideUndiscovered = true)
        assertEquals(emptyList(), revealedRoot.shrines(), "the shrine stays hidden until it's found itself")
        assertEquals(listOf("kawumoro"), revealedRoot.roots())
    }

    @Test
    fun revealingIsPerList() {
        val s = state(ShrineFilters(revealed = setOf("oromuwak")), hideUndiscovered = true)
        assertEquals(false, s.isHidden(catalog.shrine("oromuwak")!!))
        assertEquals(true, s.isHidden(catalog.lightroots.first()), "revealing a shrine leaves its root hidden")
    }
}
