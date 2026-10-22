package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.core.Game
import gx.zelda.core.Games
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every `sameAs` link in every game (docs/checklist-trackers.md, *Linked items*): each resolves, its
 * tier is in range, none is a chain or a loop, and the item without tiers in the later file is the
 * linked one. Then Skyward Sword's links one by one, as docs/ss.md lists them.
 */
class ChecklistLinksDatasetTest {
    private suspend fun catalogs(game: Game): Map<String, ChecklistCatalog> =
        ChecklistStorage.checklistFiles(game).associateWith { ChecklistRepository.load(it) }

    @Test
    fun everyLinkResolves() = runTest {
        Games.all.forEach { game -> assertEquals(emptyList(), ChecklistLinks.problems(game, catalogs(game)), game.id) }
    }

    /** The migration merges old saves of these games only, so a game with new links must be added. */
    @Test
    fun theMigrationKnowsEveryGameWithLinks() = runTest {
        val linked = Games.all.filter { game -> catalogs(game).values.any { c -> c.items.any { it.sameAs != null } } }
        assertEquals(linked.map { it.id }, ChecklistLinks.GAMES.map { it.id })
    }

    @Test
    fun skywardSwordsLinks() = runTest {
        val links = ChecklistLinks.aliases(Games.SS, catalogs(Games.SS))
            .map { "${ChecklistStorage.stemOf(it.dataFile)}:${it.item.id}" to it.link.toString() }
        assertEquals(SS_LINKS.sortedBy { it.first }, links.sortedBy { it.first })
    }

    /** Each link joins two rows about the same thing: what the linked row gives is what that tier, or item, is. */
    @Test
    fun skywardSwordsLinkedRowsAgree() = runTest {
        val catalogs = catalogs(Games.SS)
        val aliases = ChecklistLinks.aliases(Games.SS, catalogs)
        fun target(alias: ChecklistLinks.Alias): ChecklistItem = catalogs.getValue(alias.targetFile).item(alias.link.itemId)!!
        fun text(item: ChecklistItem, tier: Int?): String =
            listOfNotNull(item.name, item.reward, tier?.let { item.tiers[it - 1].name }, tier?.let { item.tiers[it - 1].reward }).joinToString(" | ")

        for (alias in aliases) {
            val what = "${alias.item.id} → ${alias.link}"
            val there = text(target(alias), alias.link.tier)
            when {
                alias.item.reward == "Piece of Heart" -> assertTrue("Piece of Heart" in there, "$what: $there")
                alias.item.id.startsWith("batreaux_") && alias.item.reward != null && "Wallet" in alias.item.reward ->
                    assertTrue(alias.item.reward.startsWith(target(alias).tiers[alias.link.tier!! - 1].name), "$what: $there")
            }
        }
        // The cubes whose chests hold a piece are the six Goddess Chest pieces, one each.
        val chestPieces = aliases.filter { it.targetFile == "ss/goddess_cubes.json" && it.dataFile == "ss/heart_pieces.json" }
        assertEquals(catalogs.getValue("ss/heart_pieces.json").items.filter { "Goddess Chest" in it.tags }.map { it.id }, chestPieces.map { it.item.id })
        assertEquals(6, chestPieces.map { it.link.itemId }.toSet().size)
        assertTrue(chestPieces.all { target(it).reward == "Piece of Heart" && it.link.tier == 2 })
        // The Main Quest's sword row holds the Goddess Sword step, the three flames and the Temple of Hylia, in order.
        assertEquals(
            listOf("statue_of_the_goddess", "farores_flame", "nayrus_flame", "dins_flame", "temple_of_hylia"),
            aliases.filter { it.link.itemId == "goddess_sword" }.sortedBy { it.link.tier }.map { it.item.id },
        )
    }

    private companion object {
        /** Linked row → what it's the same as. docs/ss.md, *Linked items*, lists each with why. */
        val SS_LINKS = listOf(
            "gratitude_crystals:batreaux_10" to "heart_pieces:batreaux_heart_piece",
            "gratitude_crystals:batreaux_30" to "items:wallet@2",
            "gratitude_crystals:batreaux_5" to "items:wallet@1",
            "gratitude_crystals:batreaux_50" to "items:wallet@3",
            "gratitude_crystals:batreaux_80" to "items:wallet@4",
            "heart_pieces:goddess_chest_beedles_island" to "goddess_cubes:cube_temple_of_time_minecart@2",
            "heart_pieces:goddess_chest_bug_rock" to "goddess_cubes:cube_summit_waterfall@2",
            "heart_pieces:goddess_chest_faron_pillar" to "goddess_cubes:cube_deep_woods_skyview_entrance@2",
            "heart_pieces:goddess_chest_volcanic_island" to "goddess_cubes:cube_great_tree_southeast@2",
            "heart_pieces:lightning_round_four_wins" to "side_quests:thunder_dragon_boss_rush@1",
            "heart_pieces:pumm_chores_reward" to "side_quests:pumm_chandelier_debt@3",
            "heart_pieces:pumpkin_pull_score" to "side_quests:pumpkin_pull@3",
            "heart_pieces:rickety_coaster_record" to "side_quests:rickety_coaster@2",
            "heart_pieces:skyloft_shed_goddess_chest" to "goddess_cubes:cube_eldin_sand_slide@2",
            "heart_pieces:skyloft_waterfall_goddess_chest" to "goddess_cubes:cube_pirate_stronghold@2",
            "items:bottle_thunderhead" to "goddess_cubes:cube_volcano_summit_eldin_lair@2",
            "items:clawshots" to "main_quest:trial_nayru",
            "items:fireshield_earrings" to "main_quest:trial_din",
            "items:goddess_harp" to "main_quest:temple_of_time",
            "items:pouch_goddess_chest" to "goddess_cubes:cube_deep_woods_gorko@2",
            "items:sailcloth" to "main_quest:wing_ceremony",
            "items:water_dragons_scale" to "main_quest:trial_farore",
            "main_quest:dins_flame" to "goddess_sword@4",
            "main_quest:farores_flame" to "goddess_sword@2",
            "main_quest:faron_woods" to "items:slingshot@1",
            "main_quest:nayrus_flame" to "goddess_sword@3",
            "main_quest:statue_of_the_goddess" to "goddess_sword@1",
            "main_quest:temple_of_hylia" to "goddess_sword@5",
            "shields:hylian_shield" to "side_quests:thunder_dragon_boss_rush@2",
            "side_quests:dodohs_high_dive" to "heart_pieces:fun_fun_island_dive",
            "side_quests:gorko_goddess_wall" to "heart_pieces:gorko_wall_riddle",
            "side_quests:harp_performance" to "pumm_chandelier_debt@3",
        )
    }
}
