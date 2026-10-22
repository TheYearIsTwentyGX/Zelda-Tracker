package gx.zelda.upgrades.data

import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.Game
import gx.zelda.core.Games

/**
 * An upgrade item whose first copy *is* a checklist item, so the two trackers share one progress.
 * The checklist's tiers are the base item then each upgrade (Beetle, Hook, Quick, Tough Beetle),
 * so its level is 0 without the item and 1 + the upgrade level with it. The checklist's level is
 * the one stored; the upgrade tracker reads and writes through it.
 */
data class ChecklistLink(
    /** The [gx.zelda.upgrades.model.UpgradeItem] id; its copy 1 is stored under that key. */
    val itemId: String,
    /** The checklist's `dataFile`, e.g. `ss/items.json`. */
    val dataFile: String,
    val checklistItemId: String,
) {
    /** The copy's state for a checklist level: none at 0, else owned at one level fewer. */
    fun pieceOf(checklistLevel: Int): PieceProgress? =
        if (checklistLevel > 0) PieceProgress(owned = true, level = checklistLevel - 1) else null

    /** The checklist level for a copy's state. Uncollected is 0, whatever level it had. */
    fun checklistLevelOf(piece: PieceProgress?): Int = if (piece?.owned == true) piece.level.coerceAtLeast(0) + 1 else 0
}

object UpgradeLinks {
    fun of(game: Game): List<ChecklistLink> = if (game.id == Games.SS.id) SS else emptyList()

    /**
     * Every Skyward Sword upgrade item outside the potions: the Items & Equipment checklist's items
     * and capacity rows, and the Shields checklist. The pouch Bomb Bag is the checklist's
     * `extra_bomb_bag`: its `bomb_bag` is the Earth Temple's, which takes no slot and can't be
     * upgraded. `SsUpgradeDatasetTest` checks each pair has the same forms.
     */
    val SS: List<ChecklistLink> = listOf(
        ChecklistLink("slingshot", "ss/items.json", "slingshot"),
        ChecklistLink("beetle", "ss/items.json", "beetle"),
        ChecklistLink("bug_net", "ss/items.json", "bug_net"),
        ChecklistLink("bow", "ss/items.json", "bow"),
        ChecklistLink("wooden_shield", "ss/shields.json", "wooden_shield"),
        ChecklistLink("iron_shield", "ss/shields.json", "iron_shield"),
        ChecklistLink("sacred_shield", "ss/shields.json", "sacred_shield"),
        ChecklistLink("hylian_shield", "ss/shields.json", "hylian_shield"),
        ChecklistLink("seed_satchel", "ss/items.json", "seed_satchel"),
        ChecklistLink("quiver", "ss/items.json", "quiver"),
        ChecklistLink("bomb_bag", "ss/items.json", "extra_bomb_bag"),
    )
}
