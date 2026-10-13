package gx.zelda.botw.armor.logic

import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.botw.armor.model.ArmorSet
import gx.zelda.botw.armor.model.ArmorSort
import gx.zelda.botw.armor.model.Slot
import gx.zelda.botw.armor.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class ArmorGroupingTest {
    private fun piece(id: String, set: String?, slot: Slot, typeOrder: Int, setOrder: Int) = ArmorPiece(
        id = id, name = id, setId = set, slot = slot, source = Source.BASE,
        defense = listOf(1), typeOrder = typeOrder, setOrder = setOrder,
    )

    // Game order deliberately differs from alphabetical: Zora before Barbarian, a single piece between them.
    private val pieces = listOf(
        piece("barbarian_helm", "barbarian", Slot.HEAD, typeOrder = 2, setOrder = 4),
        piece("barbarian_armor", "barbarian", Slot.BODY, typeOrder = 2, setOrder = 5),
        piece("zora_helm", "zora", Slot.HEAD, typeOrder = 1, setOrder = 1),
        piece("zora_armor", "zora", Slot.BODY, typeOrder = 1, setOrder = 2),
        piece("bokoblin_mask", null, Slot.HEAD, typeOrder = 3, setOrder = 3),
    )
    private val catalog = ArmorCatalog(
        materials = emptyList(),
        sets = listOf(
            ArmorSet("zora", "Zora Set", null, Source.BASE),
            ArmorSet("barbarian", "Barbarian Set", null, Source.BASE),
        ),
        pieces = pieces,
    )

    private fun layout(sort: ArmorSort) =
        ArmorGrouping.group(catalog, pieces, sort).map { it.title to it.pieces.map(ArmorPiece::id) }

    @Test
    fun alphabeticalGroupsSetsByNameWithSinglesLast() {
        assertEquals(
            listOf(
                "Barbarian Set" to listOf("barbarian_helm", "barbarian_armor"),
                "Zora Set" to listOf("zora_helm", "zora_armor"),
                "Other armor" to listOf("bokoblin_mask"),
            ),
            layout(ArmorSort.ALPHABETICAL),
        )
    }

    @Test
    fun bodyPartGroupsBySlotInGameOrder() {
        assertEquals(
            listOf(
                "Head" to listOf("zora_helm", "barbarian_helm", "bokoblin_mask"),
                "Body" to listOf("zora_armor", "barbarian_armor"),
            ),
            layout(ArmorSort.BODY_PART),
        )
    }

    @Test
    fun setOrderKeepsStandalonePiecesWhereTheGamePutsThem() {
        assertEquals(
            listOf(
                "Zora Set" to listOf("zora_helm", "zora_armor"),
                null to listOf("bokoblin_mask"),
                "Barbarian Set" to listOf("barbarian_helm", "barbarian_armor"),
            ),
            layout(ArmorSort.SET),
        )
    }

    @Test
    fun groupKeysAreUnique() {
        ArmorSort.entries.forEach { sort ->
            val keys = ArmorGrouping.group(catalog, pieces, sort).map { it.key }
            assertEquals(keys.size, keys.toSet().size, "duplicate keys for $sort")
        }
    }
}
