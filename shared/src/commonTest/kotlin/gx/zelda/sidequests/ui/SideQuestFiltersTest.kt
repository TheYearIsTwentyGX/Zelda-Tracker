package gx.zelda.sidequests.ui

import gx.zelda.core.Games
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
import gx.zelda.sidequests.data.SideQuestData
import gx.zelda.sidequests.model.QuestKind
import gx.zelda.sidequests.model.SideQuest
import gx.zelda.sidequests.model.SideQuestCatalog
import gx.zelda.sidequests.model.SideQuestPrefs
import gx.zelda.sidequests.model.SideQuestProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SideQuestFiltersTest {
    private fun quest(id: String, kind: QuestKind, region: String, giver: String, reward: String, shrineId: String? = null) =
        SideQuest(id, id.replace('_', ' '), kind, region, giver, "$giver's house", objective = "Do it", reward = reward, shrineId = shrineId)

    private val catalog = SideQuestCatalog(
        regions = listOf("Hateno", "Dueling Peaks"),
        quests = listOf(
            quest("hylian_homeowner", QuestKind.SIDE, "Hateno", "Bolson", "Silver Rupee"),
            quest("the_jewel_trade", QuestKind.SIDE, "Dueling Peaks", "Ollie", "Topaz Earrings"),
            quest("the_stolen_heirloom", QuestKind.SHRINE, "Dueling Peaks", "Impa", "Access to the shrine", shrineId = "lakna_rokee"),
            quest("ex_treasure_ancient_mask", QuestKind.DLC, "Hateno", "Diary", "Majora's Mask"),
        ),
    )

    private fun state(
        filters: SideQuestFilters = SideQuestFilters(),
        completed: Set<String> = emptySet(),
        started: Set<String> = emptySet(),
        hideUndiscovered: Boolean = false,
        shrines: ShrineProgress? = null,
    ) = SideQuestUiState(
        data = SideQuestData(catalog, shrineNames = mapOf("lakna_rokee" to "Lakna Rokee")),
        progress = SideQuestProgress(completed = completed, started = started),
        prefs = SideQuestPrefs(hideUndiscovered = hideUndiscovered),
        saveFile = Games.BOTW.saveFiles.first(),
        saveFiles = emptyList(),
        filters = filters,
        shrineProgress = shrines,
    )

    private fun SideQuestUiState.ids() = matching.map { it.id }

    private fun SideQuestUiState.ids(kind: QuestKind) = groups(kind).flatMap { (_, quests) -> quests.map { it.id } }

    @Test
    fun eachTabHasItsOwnKind() {
        val s = state()
        assertEquals(listOf("hylian_homeowner", "the_jewel_trade"), s.ids(QuestKind.SIDE))
        assertEquals(listOf("the_stolen_heirloom"), s.ids(QuestKind.SHRINE))
        assertEquals(listOf("ex_treasure_ancient_mask"), s.ids(QuestKind.DLC))
        assertEquals(QuestKind.entries, s.kinds)
    }

    @Test
    fun incompleteOnlyHidesCompletedQuests() {
        val s = state(SideQuestFilters(incompleteOnly = true), completed = setOf("hylian_homeowner"), started = setOf("the_jewel_trade"))
        assertEquals(listOf("the_jewel_trade", "the_stolen_heirloom", "ex_treasure_ancient_mask"), s.ids(), "in progress still shows")
    }

    @Test
    fun searchMatchesNameGiverPlaceRewardRegionAndShrine() {
        assertEquals(listOf("hylian_homeowner"), state(SideQuestFilters(query = " homeowner ")).ids())
        assertEquals(listOf("the_jewel_trade"), state(SideQuestFilters(query = "ollie")).ids())
        assertEquals(listOf("hylian_homeowner"), state(SideQuestFilters(query = "bolson's house")).ids())
        assertEquals(listOf("ex_treasure_ancient_mask"), state(SideQuestFilters(query = "majora")).ids())
        assertEquals(listOf("hylian_homeowner", "ex_treasure_ancient_mask"), state(SideQuestFilters(query = "hateno")).ids())
        assertEquals(listOf("the_stolen_heirloom"), state(SideQuestFilters(query = "lakna")).ids())
    }

    @Test
    fun searchDoesNotMatchWhatHiddenQuestsHide() {
        fun hidden(query: String, started: Set<String> = emptySet(), revealed: Set<String> = emptySet()) =
            state(SideQuestFilters(query = query, revealed = revealed), started = started, hideUndiscovered = true).ids()

        assertEquals(emptyList(), hidden("homeowner"), "name")
        assertEquals(emptyList(), hidden("bolson"), "giver")
        assertEquals(emptyList(), hidden("silver"), "reward")
        assertEquals(emptyList(), hidden("lakna"), "linked shrine")
        assertEquals(listOf("hylian_homeowner", "ex_treasure_ancient_mask"), hidden("hateno"), "the region isn't hidden")

        assertEquals(listOf("hylian_homeowner"), hidden("bolson", started = setOf("hylian_homeowner")))
        assertEquals(listOf("hylian_homeowner"), hidden("bolson", revealed = setOf("hylian_homeowner")))
    }

    @Test
    fun hidingOnlyAffectsQuestsNotStarted() {
        val s = state(completed = setOf("hylian_homeowner"), started = setOf("the_jewel_trade"), hideUndiscovered = true)
        assertEquals(listOf("the_stolen_heirloom", "ex_treasure_ancient_mask"), catalog.quests.filter(s::isHidden).map { it.id })
        assertEquals(emptyList(), catalog.quests.filter(state()::isHidden))
    }

    @Test
    fun regionCountsIgnoreFiltersAndSplitByTab() {
        val s = state(SideQuestFilters(query = "zzz"), completed = setOf("hylian_homeowner"), started = setOf("the_jewel_trade"))
        assertEquals(QuestRegionCount(started = 1, completed = 1, total = 1), s.regionCounts.getValue(QuestKind.SIDE).getValue("Hateno"))
        assertEquals(QuestRegionCount(started = 1, completed = 0, total = 1), s.regionCounts.getValue(QuestKind.SIDE).getValue("Dueling Peaks"))
        assertEquals(QuestRegionCount(0, 0, 1), s.regionCounts.getValue(QuestKind.SHRINE).getValue("Dueling Peaks"))
        assertEquals(1, s.completedCount(QuestKind.SIDE))
        assertEquals(2, s.total(QuestKind.SIDE))
    }

    @Test
    fun groupsFollowRegionOrder() {
        assertEquals(listOf("Hateno", "Dueling Peaks"), state().groups(QuestKind.SIDE).map { it.first })
    }

    @Test
    fun shrineQuestsReadTheirShrineFromTheShrineTracker() {
        val heirloom = catalog.quests.first { it.id == "the_stolen_heirloom" }
        val homeowner = catalog.quests.first { it.id == "hylian_homeowner" }
        assertNull(state().shrineState(heirloom), "no shrine tracker")
        assertEquals(ShrineState.UNDISCOVERED, state(shrines = ShrineProgress()).shrineState(heirloom))
        assertEquals(ShrineState.FOUND, state(shrines = ShrineProgress(found = setOf("lakna_rokee"))).shrineState(heirloom))
        assertEquals(ShrineState.COMPLETED, state(shrines = ShrineProgress(completed = setOf("lakna_rokee"))).shrineState(heirloom))
        assertNull(state(shrines = ShrineProgress(completed = setOf("lakna_rokee"))).shrineState(homeowner))
        assertEquals("Lakna Rokee", state().shrineName(heirloom))
    }
}
