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
import kotlin.test.assertTrue
import kotlin.test.fail

/** Structural checks on the bundled BotW armor.json, plus spot checks of well-known pieces. */
class BotwArmorDatasetTest {
    private fun withCatalog(block: (ArmorCatalog) -> Unit) = runTest { block(ArmorRepository.load(Games.BOTW)) }

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
    fun inGameMaterialOrderCoversEveryMaterialOnce() = withCatalog { c ->
        val orders = c.materials.map { it.gameOrder }
        assertTrue(orders.all { it != null }, "material missing a game order")
        assertEquals(c.materials.size, orders.toSet().size, "game order positions must be unique")

        // Anchors from the game's Materials pouch sort: cooking group first (fruit, mushrooms, plants…),
        // with Star Fragment and dragon parts grouped as spices ahead of fish; Ancient parts last.
        val ordered = c.materials.sortedBy { it.gameOrder }.map { it.id }
        assertEquals(listOf("voltfruit", "sunshroom", "zapshroom", "rushroom", "silent_shroom", "swift_carrot"), ordered.take(6))
        assertTrue(ordered.indexOf("star_fragment") < ordered.indexOf("hearty_bass"))
        assertEquals("giant_ancient_core", ordered.last())
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
    fun purchaseCostsThatAreMoreThanRupees() = withCatalog { c ->
        fun shopCost(id: String) = c.piece(id)!!.acquire.first { it.method == AcquireMethod.SHOP }.cost!!

        val radiant = shopCost("radiant_mask")
        assertEquals(800, radiant.rupees)
        assertEquals(listOf(MaterialCost("luminous_stone", 3)), radiant.materials)

        val ancient = shopCost("ancient_helm")
        assertEquals(2000, ancient.rupees)
        assertTrue(MaterialCost("ancient_core", 3) in ancient.materials)

        val lynel = shopCost("lynel_mask")
        assertEquals(999, lynel.mon)
        assertEquals(null, lynel.rupees)
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
