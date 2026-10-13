package gx.zelda.botw.armor.data

import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.Slot
import gx.zelda.botw.armor.model.Source
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Structural checks on the bundled armor.json, plus spot checks of well-known pieces. */
class ArmorDatasetTest {
    private fun withCatalog(block: (ArmorCatalog) -> Unit) = runTest { block(ArmorRepository.load()) }

    @Test
    fun hasEveryPiece() = withCatalog { c ->
        assertEquals(107, c.pieces.size)
        assertEquals(47, c.pieces.count { it.slot == Slot.HEAD })
        assertEquals(31, c.pieces.count { it.slot == Slot.BODY })
        assertEquals(29, c.pieces.count { it.slot == Slot.LEGS })
        assertTrue(Source.entries.all { s -> c.pieces.any { it.source == s } })
    }

    @Test
    fun idsAreUniqueAndReferencesResolve() = withCatalog { c ->
        listOf(c.pieces.map { it.id }, c.sets.map { it.id }, c.materials.map { it.id }).forEach {
            assertEquals(it.size, it.toSet().size, "duplicate ids: ${it.groupBy { id -> id }.filter { e -> e.value.size > 1 }.keys}")
        }
        val materialIds = c.materials.map { it.id }.toSet()
        val setIds = c.sets.map { it.id }.toSet()
        for (piece in c.pieces) {
            piece.setId?.let { assertTrue(it in setIds, "${piece.id}: unknown set $it") }
            piece.upgrades.flatten().forEach {
                assertTrue(it.material in materialIds, "${piece.id}: unknown material ${it.material}")
                assertTrue(it.qty > 0, "${piece.id}: non-positive qty for ${it.material}")
            }
        }
        c.sets.forEach { assertTrue(c.piecesIn(it.id).isNotEmpty(), "set ${it.id} has no pieces") }
    }

    @Test
    fun upgradeShapesAreConsistent() = withCatalog { c ->
        for (piece in c.pieces) {
            if (piece.isUpgradable) {
                assertEquals(4, piece.upgrades.size, "${piece.id} tiers")
                assertEquals(5, piece.defense.size, "${piece.id} defense levels")
                piece.upgrades.forEach { assertTrue(it.isNotEmpty(), "${piece.id} has an empty tier") }
                piece.defense.zipWithNext().forEach { (a, b) ->
                    if (b < a) fail("${piece.id}: defense decreases ${piece.defense}")
                }
            } else {
                assertEquals(1, piece.defense.size, "${piece.id} should have only base defense")
            }
        }
    }

    @Test
    fun inGameSortOrdersCoverEveryPieceOnce() = withCatalog { c ->
        listOf(c.pieces.map { it.typeOrder }, c.pieces.map { it.setOrder }).forEach { orders ->
            assertTrue(orders.all { it != null }, "piece missing a sort position")
            assertEquals(c.pieces.size, orders.toSet().size, "sort positions must be unique")
        }
        // Anchors from the game's sort keys: Old Shirt and Well-Worn Trousers come first by set,
        // then Champion's Tunic, then the Hylian set; Hylian Hood leads the head slot by type.
        assertEquals(
            listOf("old_shirt", "well_worn_trousers", "champions_tunic", "hylian_hood", "hylian_tunic", "hylian_trousers"),
            c.pieces.sortedBy { it.setOrder }.take(6).map { it.id },
        )
        assertEquals(
            listOf("hylian_hood", "soldiers_helm", "amber_earrings"),
            c.pieces.filter { it.slot == Slot.HEAD }.sortedBy { it.typeOrder }.take(3).map { it.id },
        )
    }

    @Test
    fun spotChecks() = withCatalog { c ->
        val climbingBandanna = c.pieces.first { it.name == "Climber's Bandanna" }
        assertEquals("Climb Speed Up", climbingBandanna.effect)
        assertEquals("Climb Jump Stamina Up", c.set(climbingBandanna.setId)?.bonus)

        val ancientHelm = c.piece("ancient_helm")!!
        assertEquals(listOf(4, 7, 12, 18, 28), ancientHelm.defense)

        val champion = c.pieces.first { it.name == "Champion's Tunic" }
        assertEquals(null, champion.setId)
        assertTrue(champion.isUpgradable)

        assertTrue(c.pieces.none { it.name == "Thunder Helm" && it.isUpgradable })
    }
}
