package gx.zelda.upgrades.model

import gx.zelda.armor.model.PieceProgress
import gx.zelda.upgrades.UpgradeFixture.beetle
import gx.zelda.upgrades.UpgradeFixture.satchel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Copies of a pouch item: their keys, adding one, and removing one without leaving a gap. */
class UpgradeProgressTest {
    @Test
    fun copyOneIsTheItemsOwnKey() {
        assertEquals(listOf("seed_satchel"), UpgradeProgress().copiesOf(satchel).map { it.key })
        val three = UpgradeProgress(copies = mapOf("seed_satchel" to 3))
        assertEquals(listOf("seed_satchel", "seed_satchel#2", "seed_satchel#3"), three.copiesOf(satchel).map { it.key })
        assertEquals(listOf("Seed Satchel", "Seed Satchel #2", "Seed Satchel #3"), three.copiesOf(satchel).map { it.label })
        // Only pouch items have copies, whatever is saved.
        assertEquals(1, UpgradeProgress(copies = mapOf("beetle" to 2)).copyCount(beetle))
    }

    @Test
    fun addingACopyLeavesTheOthersAlone() {
        val p = UpgradeProgress(mapOf("seed_satchel" to PieceProgress(true, 2)))
        val added = p.withCopyAdded(satchel).withCopyAdded(satchel)
        assertEquals(3, added.copyCount(satchel))
        assertEquals(p.items, added.items)
        assertFailsWith<IllegalArgumentException> { p.withCopyAdded(beetle) }
    }

    @Test
    fun removingACopyMovesTheLaterOnesDown() {
        val p = UpgradeProgress(
            items = mapOf(
                "seed_satchel" to PieceProgress(true, 0),
                "seed_satchel#2" to PieceProgress(true, 1),
                "seed_satchel#4" to PieceProgress(true, 2),
                "beetle" to PieceProgress(true, 1),
            ),
            copies = mapOf("seed_satchel" to 4),
        )
        val removed = p.withCopyRemoved(satchel, 2)
        assertEquals(3, removed.copyCount(satchel))
        assertEquals(
            mapOf("seed_satchel" to PieceProgress(true, 0), "seed_satchel#3" to PieceProgress(true, 2), "beetle" to PieceProgress(true, 1)),
            removed.items,
            "#3 (untouched) became #2, #4 became #3",
        )
        // Down to one copy, the count goes too.
        val one = removed.withCopyRemoved(satchel, 3).withCopyRemoved(satchel, 2)
        assertEquals(emptyMap(), one.copies)
        assertEquals(mapOf("seed_satchel" to PieceProgress(true, 0), "beetle" to PieceProgress(true, 1)), one.items)
        // Copy 1 stays; so does a copy that isn't there.
        assertEquals(p, p.withCopyRemoved(satchel, 1))
        assertEquals(p, p.withCopyRemoved(satchel, 5))
    }
}
