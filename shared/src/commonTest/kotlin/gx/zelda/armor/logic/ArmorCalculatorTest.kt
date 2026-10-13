package gx.zelda.armor.logic

import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.armor.model.ArmorPiece
import gx.zelda.armor.model.ArmorProgress
import gx.zelda.armor.model.ArmorSet
import gx.zelda.armor.model.Material
import gx.zelda.armor.model.MaterialCost
import gx.zelda.armor.model.PieceProgress
import gx.zelda.armor.model.Slot
import gx.zelda.armor.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmorCalculatorTest {
    private fun tiers(material: String) = (1..4).map { listOf(MaterialCost(material, it)) }

    private fun piece(id: String, set: String?, slot: Slot, source: Source = Source.BASE, upgradable: Boolean = true) =
        ArmorPiece(
            id = id, name = id, setId = set, slot = slot, source = source,
            defense = if (upgradable) listOf(1, 2, 3, 4, 5) else listOf(1),
            upgrades = if (upgradable) tiers("gem") else emptyList(),
        )

    private val catalog = ArmorCatalog(
        materials = listOf(Material("gem", "Gem")),
        sets = listOf(
            ArmorSet("climb", "Climbing", "Climb Jump Stamina Up", Source.BASE),
            ArmorSet("ancient", "Ancient", "Ancient Proficiency", Source.BASE),
            ArmorSet("royal", "Royal Guard", "Charge Atk. Stamina Up", Source.DLC1),
        ),
        pieces = listOf(
            piece("c_head", "climb", Slot.HEAD),
            piece("c_body", "climb", Slot.BODY),
            piece("c_legs", "climb", Slot.LEGS),
            piece("a_head", "ancient", Slot.HEAD),
            piece("a_divine", "ancient", Slot.HEAD, Source.AMIIBO),
            piece("a_body", "ancient", Slot.BODY),
            piece("a_legs", "ancient", Slot.LEGS),
            piece("r_head", "royal", Slot.HEAD, Source.DLC1, upgradable = false),
            piece("r_body", "royal", Slot.BODY, Source.DLC1, upgradable = false),
            piece("r_legs", "royal", Slot.LEGS, Source.DLC1, upgradable = false),
            piece("amiibo_cap", null, Slot.HEAD, Source.AMIIBO),
        ),
    )

    private fun progress(vararg entries: Pair<String, Int?>, showDlc: Boolean = true, showAmiibo: Boolean = true) =
        ArmorProgress(
            pieces = entries.associate { (id, level) -> id to PieceProgress(owned = level != null, level = level ?: 0) },
            showDlc = showDlc,
            showAmiibo = showAmiibo,
        )

    private fun set(id: String) = catalog.sets.first { it.id == id }

    @Test
    fun remainingUpgradesStartAfterCurrentLevel() {
        val head = catalog.piece("c_head")!!
        val remaining = ArmorCalculator.remainingUpgrades(head, progress("c_head" to 1))
        assertEquals(listOf(2, 3, 4), remaining.map { it.toLevel })
        assertEquals(2, remaining.first().costs.single().qty)
    }

    @Test
    fun unownedPieceShowsFullUpgradePathAndBaseDefense() {
        val head = catalog.piece("c_head")!!
        val p = progress(showDlc = true)
        assertEquals(4, ArmorCalculator.remainingUpgrades(head, p).size)
        assertEquals(1, ArmorCalculator.defenseAt(head, ArmorCalculator.level(head, p)))
    }

    @Test
    fun levelIsClampedToWhatThePieceSupports() {
        val head = catalog.piece("c_head")!!
        assertEquals(4, ArmorCalculator.level(head, progress("c_head" to 9)))
        assertTrue(ArmorCalculator.remainingUpgrades(head, progress("c_head" to 9)).isEmpty())
    }

    @Test
    fun setBonusNeedsEveryPieceAtTwoStars() {
        val climb = set("climb")
        assertFalse(ArmorCalculator.setBonusStatus(catalog, climb, progress("c_head" to 4, "c_body" to 4)).isActive)
        assertFalse(
            ArmorCalculator.setBonusStatus(catalog, climb, progress("c_head" to 2, "c_body" to 2, "c_legs" to 1)).isActive,
        )
        assertTrue(
            ArmorCalculator.setBonusStatus(catalog, climb, progress("c_head" to 2, "c_body" to 3, "c_legs" to 2)).isActive,
        )
    }

    @Test
    fun anyPieceInASharedSlotSatisfiesIt() {
        val ancient = set("ancient")
        val withDivineHelm = progress("a_divine" to 2, "a_body" to 2, "a_legs" to 2)
        assertTrue(ArmorCalculator.setBonusStatus(catalog, ancient, withDivineHelm).isActive)
        val lowHelms = progress("a_head" to 1, "a_divine" to 1, "a_body" to 2, "a_legs" to 2)
        assertFalse(ArmorCalculator.setBonusStatus(catalog, ancient, lowHelms).isActive)
    }

    @Test
    fun nonUpgradableSetIsActiveOnceOwned() {
        val royal = set("royal")
        assertFalse(ArmorCalculator.setBonusStatus(catalog, royal, progress("r_head" to 0, "r_body" to 0)).isActive)
        assertTrue(
            ArmorCalculator.setBonusStatus(catalog, royal, progress("r_head" to 0, "r_body" to 0, "r_legs" to 0)).isActive,
        )
    }

    @Test
    fun collectedTotalsOnlyCountOwnedPieces() {
        // c_head at ★2 still needs tiers 3 and 4 (3 + 4 gems); c_body at ★4 needs nothing.
        val totals = ArmorCalculator.totals(catalog, progress("c_head" to 2, "c_body" to 4), TotalsMode.COLLECTED)
        assertEquals(7, totals.lines.single().qty)
        assertEquals(1, totals.piecesCounted)
        assertEquals(2, totals.tiersRemaining)
    }

    @Test
    fun allTotalsAddFullPathForUncollectedPieces() {
        val p = progress("c_head" to 2, "c_body" to 4)
        val totals = ArmorCalculator.totals(catalog, p, TotalsMode.ALL)
        // 7 for c_head, plus 10 for each of the other 6 upgradable, unowned pieces.
        val upgradableUnowned = catalog.pieces.count { it.isUpgradable && !p.of(it.id).owned }
        assertEquals(6, upgradableUnowned)
        assertEquals(7 + 10 * upgradableUnowned, totals.lines.single().qty)
        assertEquals(7, totals.piecesCounted)
    }

    @Test
    fun sourceFiltersApplyToTotals() {
        val all = ArmorCalculator.totals(catalog, progress(showAmiibo = false), TotalsMode.ALL)
        assertEquals(6, all.piecesCounted) // climbing ×3 + ancient head/body/legs
        assertTrue(all.lines.single().uses.none { it.piece.source == Source.AMIIBO })
    }
}
