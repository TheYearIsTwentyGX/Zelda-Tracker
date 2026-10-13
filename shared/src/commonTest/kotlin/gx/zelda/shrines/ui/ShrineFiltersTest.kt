package gx.zelda.shrines.ui

import gx.zelda.core.Games
import gx.zelda.shrines.model.OrbUpgrades
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.TrialType
import kotlin.test.Test
import kotlin.test.assertEquals

class ShrineFiltersTest {
    private val catalog = ShrineCatalog(
        regions = listOf("Great Plateau", "Hateno"),
        upgrades = OrbUpgrades(4, 3, 30, 1, 10, 4),
        shrines = listOf(
            Shrine("oman_au", "Oman Au", "Great Plateau", "Magnesis Trial", TrialType.PUZZLE, location = "Here"),
            Shrine("myahm_agana", "Myahm Agana", "Hateno", "Myahm Agana Apparatus", TrialType.PUZZLE, location = "There"),
            Shrine("chaas_qeta", "Chaas Qeta", "Hateno", "Blessing", TrialType.BLESSING, quest = "The Cursed Statue", location = "Island"),
            Shrine("dlc_one", "Dlc One", "Great Plateau", "Major Test of Strength", TrialType.COMBAT, dlc = true, location = "Plateau"),
        ),
    )

    private fun state(
        filters: ShrineFilters = ShrineFilters(),
        showDlc: Boolean = true,
        completed: Set<String> = emptySet(),
        found: Set<String> = emptySet(),
        hideUndiscovered: Boolean = false,
    ) = ShrineUiState(
        catalog = catalog,
        progress = ShrineProgress(completed = completed, found = found),
        prefs = ShrinePrefs(showDlc = showDlc, hideUndiscovered = hideUndiscovered),
        saveFile = Games.BOTW.saveFiles.first(),
        saveFiles = emptyList(),
        filters = filters,
    )

    private fun ShrineUiState.ids() = matching.map { it.id }

    @Test
    fun everythingShowsByDefault() {
        assertEquals(listOf("oman_au", "myahm_agana", "chaas_qeta", "dlc_one"), state().ids())
    }

    @Test
    fun dlcCanBeHidden() {
        assertEquals(listOf("oman_au", "myahm_agana", "chaas_qeta"), state(showDlc = false).ids())
    }

    @Test
    fun incompleteOnlyHidesCompletedShrines() {
        assertEquals(
            listOf("myahm_agana", "dlc_one"),
            state(ShrineFilters(incompleteOnly = true), completed = setOf("oman_au", "chaas_qeta")).ids(),
        )
    }

    @Test
    fun questsOnlyKeepsShrineQuestShrines() {
        assertEquals(listOf("chaas_qeta"), state(ShrineFilters(questsOnly = true)).ids())
    }

    @Test
    fun searchMatchesNameTrialQuestAndRegion() {
        assertEquals(listOf("oman_au"), state(ShrineFilters(query = " magnesis ")).ids())
        assertEquals(listOf("chaas_qeta"), state(ShrineFilters(query = "cursed")).ids())
        assertEquals(listOf("myahm_agana", "chaas_qeta"), state(ShrineFilters(query = "hateno")).ids())
        assertEquals(listOf("myahm_agana"), state(ShrineFilters(query = "myahm")).ids())
    }

    @Test
    fun searchDoesNotMatchWhatHiddenShrinesHide() {
        fun hidden(query: String, found: Set<String> = emptySet(), revealed: Set<String> = emptySet()) =
            state(ShrineFilters(query = query, revealed = revealed), found = found, hideUndiscovered = true).ids()

        assertEquals(emptyList(), hidden("myahm"), "name")
        assertEquals(emptyList(), hidden("magnesis"), "trial")
        assertEquals(emptyList(), hidden("cursed"), "Shrine Quest")
        assertEquals(listOf("myahm_agana", "chaas_qeta"), hidden("hateno"), "the region isn't hidden")

        assertEquals(listOf("myahm_agana"), hidden("myahm", found = setOf("myahm_agana")))
        assertEquals(listOf("myahm_agana"), hidden("myahm", revealed = setOf("myahm_agana")))
        assertEquals(listOf("oman_au"), state(ShrineFilters(query = "magnesis"), completed = setOf("oman_au"), hideUndiscovered = true).ids())
    }

    @Test
    fun questsOnlySkipsHiddenShrines() {
        assertEquals(emptyList(), state(ShrineFilters(questsOnly = true), hideUndiscovered = true).ids())
        assertEquals(
            listOf("chaas_qeta"),
            state(ShrineFilters(questsOnly = true), found = setOf("chaas_qeta"), hideUndiscovered = true).ids(),
        )
    }

    @Test
    fun hidingOnlyAffectsUndiscoveredShrines() {
        val s = state(completed = setOf("oman_au"), found = setOf("myahm_agana"), hideUndiscovered = true)
        assertEquals(listOf("chaas_qeta", "dlc_one"), catalog.shrines.filter(s::isHidden).map { it.id })
        assertEquals(emptyList(), catalog.shrines.filter(state(hideUndiscovered = false)::isHidden))
    }

    @Test
    fun groupsFollowRegionOrder() {
        val groups = catalog.byRegion(state(ShrineFilters(query = "a")).matching)
        assertEquals(listOf("Great Plateau", "Hateno"), groups.map { it.first })
        assertEquals(listOf("Hateno"), catalog.byRegion(state(ShrineFilters(questsOnly = true)).matching).map { it.first })
    }
}
