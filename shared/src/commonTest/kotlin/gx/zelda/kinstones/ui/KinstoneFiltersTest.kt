package gx.zelda.kinstones.ui

import gx.zelda.core.Games
import gx.zelda.kinstones.model.FusionReward
import gx.zelda.kinstones.model.KinstoneCatalog
import gx.zelda.kinstones.model.KinstoneColor
import gx.zelda.kinstones.model.KinstoneFusion
import gx.zelda.kinstones.model.KinstonePrefs
import gx.zelda.kinstones.model.KinstoneProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KinstoneFiltersTest {
    private fun fusion(
        id: String,
        partner: String,
        region: String,
        color: KinstoneColor,
        result: String,
        rewards: List<FusionReward>,
        random: Boolean = false,
    ) = KinstoneFusion(
        id = id, number = 0, gameId = "0x00", partner = partner, random = random, region = region, location = "$partner's house",
        color = color, shape = if (color == KinstoneColor.GOLD) null else "slant", result = result, rewards = rewards, stage = 1,
    )

    private val catalog = KinstoneCatalog(
        regions = listOf("Random fusers", "Hyrule Town", "Castor Wilds"),
        stages = listOf("From the start"),
        fusions = listOf(
            fusion("random_chest", "Random fuser", "Random fusers", KinstoneColor.GREEN, "A chest appears with 200 Rupees", listOf(FusionReward.RUPEES), random = true),
            fusion("mayor_hagen", "Mayor Hagen", "Hyrule Town", KinstoneColor.RED, "A chest with the Big Wallet", listOf(FusionReward.ITEM)),
            fusion("hurdy_gurdy_man", "Hurdy-Gurdy Man", "Hyrule Town", KinstoneColor.BLUE, "A tree opens with a Piece of Heart", listOf(FusionReward.HEART)),
            fusion("statue_left", "Mysterious Statue (left)", "Castor Wilds", KinstoneColor.GOLD, "The boulder shatters", listOf(FusionReward.WAY)),
        ),
    )

    private fun state(filters: KinstoneFilters = KinstoneFilters(), fused: Set<String> = emptySet(), hideResults: Boolean = false) =
        KinstoneUiState(
            catalog = catalog,
            progress = KinstoneProgress(fused),
            prefs = KinstonePrefs(hideResults),
            saveFile = Games.MC.saveFiles.first(),
            saveFiles = emptyList(),
            filters = filters,
        )

    private fun KinstoneUiState.ids() = groups.flatMap { (_, fusions) -> fusions.map { it.id } }

    @Test
    fun groupsFollowTheRegionOrder() {
        assertEquals(listOf("Random fusers", "Hyrule Town", "Castor Wilds"), state().groups.map { it.first })
        assertEquals(listOf("random_chest", "mayor_hagen", "hurdy_gurdy_man", "statue_left"), state().ids())
    }

    @Test
    fun tallies() {
        val s = state(fused = setOf("statue_left", "hurdy_gurdy_man", "gone_from_the_dataset"))
        assertEquals(2, s.fused, "ids the dataset doesn't have aren't counted")
        assertEquals(FusionTally(1, 1), s.gold)
        assertEquals(FusionTally(1, 1), s.hearts)
        assertEquals(KinstoneRegionCount(fused = 1, total = 2), s.regionCounts["Hyrule Town"])
    }

    @Test
    fun notFusedOnlyHidesDoneFusions() {
        assertEquals(listOf("random_chest", "hurdy_gurdy_man", "statue_left"), state(KinstoneFilters(notFusedOnly = true), fused = setOf("mayor_hagen")).ids())
    }

    @Test
    fun regionColourAndRewardFilters() {
        assertEquals(listOf("mayor_hagen", "hurdy_gurdy_man"), state(KinstoneFilters(region = "Hyrule Town")).ids())
        assertEquals(listOf("mayor_hagen", "statue_left"), state(KinstoneFilters(colors = setOf(KinstoneColor.RED, KinstoneColor.GOLD))).ids())
        assertEquals(listOf("random_chest", "hurdy_gurdy_man"), state(KinstoneFilters(rewards = setOf(FusionReward.HEART, FusionReward.RUPEES))).ids())
        assertTrue(KinstoneFilters(region = "Hyrule Town").isFiltering)
        assertFalse(KinstoneFilters(query = "wallet").isFiltering, "search has its own clear button")
    }

    @Test
    fun searchMatchesPartnerPlacePieceAndResult() {
        assertEquals(listOf("mayor_hagen"), state(KinstoneFilters(query = "hagen")).ids())
        assertEquals(listOf("mayor_hagen"), state(KinstoneFilters(query = "wallet")).ids())
        assertEquals(listOf("mayor_hagen"), state(KinstoneFilters(query = "red slant")).ids())
        assertEquals(listOf("statue_left"), state(KinstoneFilters(query = "castor")).ids())
    }

    /** With results hidden, a fusion not done shows its partner and piece, so those still match, but not what it gives. */
    @Test
    fun hiddenResultsDontLeakThroughSearchOrRewardFilter() {
        val hidden = state(KinstoneFilters(query = "wallet"), hideResults = true)
        assertTrue(hidden.isHidden(catalog.fusion("mayor_hagen")!!))
        assertEquals(emptyList(), hidden.ids())
        assertEquals(listOf("mayor_hagen"), state(KinstoneFilters(query = "hagen"), hideResults = true).ids())
        assertEquals(emptyList(), state(KinstoneFilters(rewards = setOf(FusionReward.ITEM)), hideResults = true).ids())

        // Fusing it, or tapping to reveal it, shows the result again.
        assertEquals(listOf("mayor_hagen"), state(KinstoneFilters(query = "wallet"), fused = setOf("mayor_hagen"), hideResults = true).ids())
        val revealed = state(KinstoneFilters(query = "wallet", revealed = setOf("mayor_hagen")), hideResults = true)
        assertFalse(revealed.isHidden(catalog.fusion("mayor_hagen")!!))
        assertEquals(listOf("mayor_hagen"), revealed.ids())
    }

    @Test
    fun rewardChipsOnlyOfferWhatSomeFusionGives() {
        assertEquals(listOf(FusionReward.HEART, FusionReward.ITEM, FusionReward.WAY, FusionReward.RUPEES), state().rewardKinds)
    }
}
