package gx.zelda.armor.logic

import gx.zelda.armor.model.ArmorPiece
import gx.zelda.armor.model.ArmorProgress
import gx.zelda.armor.model.MaterialCost
import gx.zelda.armor.model.PieceProgress
import gx.zelda.armor.model.Slot
import gx.zelda.armor.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class SetUpgradeTest {
    private fun piece(id: String, slot: Slot, upgradable: Boolean = true) = ArmorPiece(
        id = id, name = id, setId = "s", slot = slot, source = Source.BASE,
        defense = if (upgradable) listOf(1, 2, 3, 4, 5) else listOf(1),
        upgrades = if (upgradable) List(4) { listOf(MaterialCost("gem", 1)) } else emptyList(),
    )

    private val hood = piece("hood", Slot.HEAD)
    private val tunic = piece("tunic", Slot.BODY)
    private val trousers = piece("trousers", Slot.LEGS)
    private val hylian = listOf(hood, tunic, trousers)

    private fun progress(vararg levels: Pair<String, Int?>) =
        ArmorProgress(levels.associate { (id, lvl) -> id to PieceProgress(owned = lvl != null, level = lvl ?: 0) })

    /** Applies one tap's result the way the view model does: setting a level also collects the piece. */
    private fun ArmorProgress.tap(targets: List<ArmorPiece>): ArmorProgress =
        SetUpgrade.nextLevels(targets, this).entries.fold(this) { p, (id, lvl) ->
            p.withPiece(id) { it.copy(owned = true, level = lvl) }
        }

    private fun ArmorProgress.levels() = hylian.map { ArmorCalculator.level(it, this) }

    @Test
    fun averageOfMixedLevels() {
        val p = progress("hood" to 0, "tunic" to 1, "trousers" to 3)
        assertEquals(4f / 3f, SetUpgrade.averageLevel(hylian, p))
    }

    @Test
    fun tapsRaiseTheLeastUpgradedPiecesThenWrap() {
        var p = progress("hood" to 0, "tunic" to 1, "trousers" to 3)
        val seen = mutableListOf<List<Int>>()
        repeat(5) {
            p = p.tap(hylian)
            seen += p.levels()
        }
        assertEquals(
            listOf(listOf(1, 1, 3), listOf(2, 2, 3), listOf(3, 3, 3), listOf(4, 4, 4), listOf(0, 0, 0)),
            seen,
        )
    }

    @Test
    fun uncollectedPiecesCountAsZeroAndGetCollected() {
        val p = progress("tunic" to 1, "trousers" to 1).tap(hylian)
        assertEquals(PieceProgress(owned = true, level = 1), p.of("hood"))
    }

    @Test
    fun sharedSlotUsesOwnedPiecesOrOnlyTheFirstOption() {
        val helm = piece("ancient_helm", Slot.HEAD)
        val divine = piece("divine_helm", Slot.HEAD)
        val set = listOf(helm, divine, piece("cuirass", Slot.BODY), piece("greaves", Slot.LEGS))

        assertEquals(listOf("ancient_helm", "cuirass", "greaves"), SetUpgrade.targets(set, progress()).map { it.id })
        assertEquals(
            listOf("divine_helm", "cuirass", "greaves"),
            SetUpgrade.targets(set, progress("divine_helm" to 2)).map { it.id },
        )
    }

    @Test
    fun piecesThatCannotBeUpgradedAreIgnored() {
        val set = listOf(piece("a", Slot.HEAD, upgradable = false), piece("b", Slot.BODY, upgradable = false))
        assertEquals(emptyList(), SetUpgrade.targets(set, progress()))
    }
}
