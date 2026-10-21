package gx.zelda.upgrades.model

import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
import kotlinx.serialization.Serializable

/**
 * One tracked copy of an item. Most items have just copy 1; a [UpgradeItem.multiple] pouch item
 * can have more ("Seed Satchel #2"). Copy 1 is stored under the item's id, later ones under
 * `<id>#<n>`, so adding copies never moves the first one's progress.
 */
data class ItemCopy(val item: UpgradeItem, val number: Int) {
    val key: String get() = keyOf(item.id, number)

    /** "Seed Satchel", "Seed Satchel #2". */
    val label: String get() = if (number == 1) item.name else "${item.name} #$number"

    companion object {
        fun keyOf(itemId: String, number: Int): String = if (number == 1) itemId else "$itemId#$number"
    }
}

/** One save file's upgrades: each copy's state, and how many copies of each pouch item are tracked. */
@Serializable
data class UpgradeProgress(
    /** Keyed by [ItemCopy.key]. `level` counts upgrades done, 0 for the base item. */
    val items: Map<String, PieceProgress> = emptyMap(),
    /** How many copies of a [UpgradeItem.multiple] item are tracked, when more than one. */
    val copies: Map<String, Int> = emptyMap(),
) {
    fun of(key: String): PieceProgress = items[key] ?: PieceProgress()

    fun copyCount(item: UpgradeItem): Int = if (item.multiple) (copies[item.id] ?: 1).coerceAtLeast(1) else 1

    fun copiesOf(item: UpgradeItem): List<ItemCopy> = (1..copyCount(item)).map { ItemCopy(item, it) }

    /** The level that counts: 0 when not owned, clamped to what the item supports. */
    fun level(copy: ItemCopy): Int = of(copy.key).let { if (it.owned) it.level.coerceIn(0, copy.item.maxLevel) else 0 }

    fun owned(copy: ItemCopy): Boolean = of(copy.key).owned

    fun withCopy(key: String, transform: (PieceProgress) -> PieceProgress): UpgradeProgress =
        copy(items = items + (key to transform(of(key))))

    /** One more copy of [item], untouched, numbered after the last. */
    fun withCopyAdded(item: UpgradeItem): UpgradeProgress {
        require(item.multiple) { "${item.id} isn't a pouch item you can carry several of" }
        return copy(copies = copies + (item.id to copyCount(item) + 1))
    }

    /**
     * Without copy [number] of [item]: the copies after it move down one, so the numbers stay
     * 1, 2, 3… Copy 1 can't be removed; untick it instead.
     */
    fun withCopyRemoved(item: UpgradeItem, number: Int): UpgradeProgress {
        val count = copyCount(item)
        if (number < 2 || number > count) return this
        val moved = items.toMutableMap()
        for (n in number until count) {
            val next = items[ItemCopy.keyOf(item.id, n + 1)]
            if (next == null) moved.remove(ItemCopy.keyOf(item.id, n)) else moved[ItemCopy.keyOf(item.id, n)] = next
        }
        moved.remove(ItemCopy.keyOf(item.id, count))
        val left = count - 1
        return UpgradeProgress(moved, if (left == 1) copies - item.id else copies + (item.id to left))
    }
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class UpgradePrefs(
    /** [UpgradeSection.optional] sections turned off, e.g. the potions; all are on by default. */
    val hiddenSections: Set<String> = emptySet(),
    val materialSort: MaterialSort = MaterialSort.MOST_NEEDED,
) {
    fun shows(section: String): Boolean = section !in hiddenSections
}
