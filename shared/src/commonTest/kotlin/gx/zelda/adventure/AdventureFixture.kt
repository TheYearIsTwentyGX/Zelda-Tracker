package gx.zelda.adventure

import gx.zelda.adventure.model.ARankLimits
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.BattleType
import gx.zelda.adventure.model.CardEffect
import gx.zelda.adventure.model.ItemCard
import gx.zelda.adventure.model.MapArt
import gx.zelda.adventure.model.MapRegion
import gx.zelda.adventure.model.Reward
import gx.zelda.adventure.model.RewardSlot
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Search
import gx.zelda.adventure.model.Side
import gx.zelda.adventure.model.Skulltula
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.Warrior

/**
 * A 3×2 map with one blank square, small enough to reason about:
 *
 *     r1c1 start  | wall | r1c2         r1c3 Dark Ruler
 *     r2c1        |        r2c2         (blank)
 *
 * r1c1 gives the Bomb card and has a Hookshot search (a path card) that opens its walled side;
 * r2c1 gives the Hookshot. r1c2 and r1c3 have Bomb searches.
 */
object AdventureFixture {
    private fun square(row: Int, col: Int, region: String, mission: String, block: Square.() -> Square = { this }) = Square(
        id = "r${row}c$col",
        label = "${'A' + (row - 1)}-$col",
        altLabel = "${'A' + (col - 1)}-$row",
        row = row,
        col = col,
        region = region,
        battle = BattleType.ADVENTURE,
        mission = mission,
        aRank = ARankLimits(kos = 1200, minutes = 15, damage = 149),
    ).block()

    private fun reward(square: String, id: String, slot: RewardSlot, type: RewardType, name: String, warrior: String? = null, keep: String? = null) =
        Reward(id = "$square.$id", slot = slot, type = type, warrior = warrior, name = name, keep = keep)

    val map = AdventureMap(
        id = "test_map",
        name = "Test Map",
        cols = 3,
        rows = 2,
        start = "r1c1",
        finalBoss = "r1c3",
        art = MapArt(squares = "hwde/test_map", preview = "hwde/test_map/preview.webp"),
        regions = listOf(MapRegion("green", "Green"), MapRegion("red", "Red")),
        cards = listOf(
            ItemCard("bomb", "Bomb", listOf(CardEffect.REWARD, CardEffect.ENEMIES), "Opens caves.", 10000),
            ItemCard("hookshot", "Hookshot", listOf(CardEffect.PATH), "Pulls you across.", 10000),
        ),
        warriors = listOf(Warrior("link", "Link"), Warrior("zelda", "Zelda")),
        squares = listOf(
            square(1, 1, "green", "Defeat 300 enemies in time!") {
                copy(
                    rewards = listOf(
                        reward(id, "hc_link", RewardSlot.A_RANK, RewardType.HEART_CONTAINER, "Heart Container", "link"),
                        reward(id, "poh_zelda", RewardSlot.TREASURE, RewardType.HEART_PIECE, "Piece of Heart", "zelda", keep = "North Keep"),
                    ),
                    givesCards = listOf("bomb"),
                    skulltulas = listOf(Skulltula("$id.gs1", "KO 1000 enemies", "By the gate"), Skulltula("$id.gs2", "Win without losing 40% health", "Same place as the first")),
                    searches = listOf(Search("$id.search_hookshot", "hookshot", row = 0, col = 8)),
                    blockades = listOf(Side.EAST),
                )
            },
            square(1, 2, "green", "Capture the keeps!") {
                copy(
                    rewards = listOf(reward(id, "weapon_spinner_1", RewardSlot.A_RANK, RewardType.WEAPON, "Spinner Lv. 1", "link")),
                    searches = listOf(Search("$id.search_bomb", "bomb", row = 1, col = 9)),
                    blockades = listOf(Side.WEST),
                )
            },
            square(1, 3, "red", "Defeat the Demon King!") {
                copy(
                    finalBoss = true,
                    rewards = listOf(reward(id, "hc_zelda", RewardSlot.CLEAR, RewardType.HEART_CONTAINER, "Heart Container", "zelda")),
                    searches = listOf(Search("$id.search_bomb", "bomb", row = 6, col = 5)),
                    quiz = listOf("Zant", "Impa", "Zelda"),
                )
            },
            square(2, 1, "red", "Stop the healers!") {
                copy(
                    rewards = listOf(reward(id, "outfit_zelda_skyloft_robes", RewardSlot.CLEAR, RewardType.OUTFIT, "Skyloft Robes", "zelda")),
                    givesCards = listOf("hookshot"),
                )
            },
            square(2, 2, "red", "Defend the allied keeps!") {
                copy(rewards = listOf(reward(id, "fairy_food_magic_beans", RewardSlot.TREASURE, RewardType.FAIRY_FOOD, "Fairy Food - Magic Beans", keep = "South Keep").copy(container = "pot")))
            },
        ),
    )

    fun square(id: String): Square = map.square(id)!!
}
