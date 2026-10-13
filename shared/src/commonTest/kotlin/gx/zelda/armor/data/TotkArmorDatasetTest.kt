package gx.zelda.armor.data

import gx.zelda.armor.model.AcquireMethod
import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.armor.model.MaterialCost
import gx.zelda.armor.model.Slot
import gx.zelda.armor.model.Source
import gx.zelda.core.Games
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Structural checks on the bundled TotK armor.json, plus spot checks of well-known pieces. */
class TotkArmorDatasetTest {
    private fun withCatalog(block: (ArmorCatalog) -> Unit) = runTest { block(ArmorRepository.load(Games.TOTK)) }

    @Test
    fun hasEveryPiece() = withCatalog { c ->
        assertEquals(135, c.pieces.size)
        assertEquals(59, c.pieces.count { it.slot == Slot.HEAD })
        assertEquals(38, c.pieces.count { it.slot == Slot.BODY })
        assertEquals(38, c.pieces.count { it.slot == Slot.LEGS })
        assertEquals(103, c.pieces.count { it.isUpgradable })
    }

    @Test
    fun hasNoDlcAndEveryPieceCanBeFoundWithoutAmiibo() = withCatalog { c ->
        assertTrue(c.pieces.all { it.source == Source.BASE })
        assertFalse(c.hasDlc)
        assertFalse(c.hasAmiibo, "amiibo drops are extra ways to get base-game pieces")
        assertFalse(c.hasGameOrder, "no verified in-game sort data yet, so only A–Z is offered")
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
        c.sets.forEach { assertEquals(3, c.piecesIn(it.id).size, "set ${it.id} should have three pieces") }
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
    fun upgradeMaterialsHaveAPouchPosition() = withCatalog { c ->
        val used = c.pieces.flatMap { p -> p.upgrades.flatten().map { it.material } }.toSet()
        val orders = c.materials.filter { it.id in used }.map { it.gameOrder }
        assertTrue(orders.all { it != null }, "upgrade material missing a game order")
        assertEquals(orders.size, orders.toSet().size, "game order positions must be unique")
    }

    @Test
    fun everyPieceSaysHowToGetIt() = withCatalog { c ->
        val materialIds = c.materials.map { it.id }.toSet()
        for (piece in c.pieces) {
            assertTrue(piece.acquire.isNotEmpty(), "${piece.id} has no acquisition info")
            for (way in piece.acquire) {
                assertTrue(way.title.isNotBlank() && way.detail.isNotBlank(), "${piece.id}: blank acquisition text")
                way.cost?.materials?.forEach {
                    assertTrue(it.material in materialIds, "${piece.id}: unknown cost material ${it.material}")
                }
            }
        }
    }

    @Test
    fun spotChecks() = withCatalog { c ->
        val bandanna = c.piece("climbers_bandanna")!!
        assertEquals("Climb Speed Up", bandanna.effect)
        assertEquals("Climb Jump Stamina Up", c.set(bandanna.setId)?.bonus)
        assertEquals(listOf(3, 5, 8, 12, 20), bandanna.defense)

        assertEquals(listOf(MaterialCost("bokoblin_horn", 5)), c.piece("hylian_hood")!!.upgrades.first())
        assertEquals("Energy Recharge Up", c.set("zonaite_set")?.bonus)

        // Koltin trades masks for Bubbul Gems; Bargainer Statues sell for Poes.
        val mask = c.piece("bokoblin_mask")!!.acquire.first { it.method == AcquireMethod.SHOP }.cost!!
        assertEquals(null, mask.rupees)
        assertEquals("bubbul_gem", mask.materials.single().material)
        val dark = c.piece("dark_hood")!!
        assertFalse(dark.isUpgradable)
        assertEquals("poe", dark.acquire.first().cost!!.materials.single().material)

        assertEquals(listOf(12, 21, 36, 54, 84), c.piece("ancient_heros_aspect")!!.defense)
    }
}
