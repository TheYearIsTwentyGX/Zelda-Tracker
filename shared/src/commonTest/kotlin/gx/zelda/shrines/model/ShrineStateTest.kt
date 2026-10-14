package gx.zelda.shrines.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ShrineStateTest {
    @Test
    fun undiscoveredByDefault() {
        assertEquals(ShrineState.UNDISCOVERED, ShrineProgress().state("oman_au"))
    }

    @Test
    fun completingImpliesFound() {
        val p = ShrineProgress().withState("oman_au", ShrineState.COMPLETED)
        assertEquals(ShrineState.COMPLETED, p.state("oman_au"))
        assertEquals(setOf("oman_au"), p.found)
        assertEquals(ShrineState.FOUND, p.withState("oman_au", ShrineState.FOUND).state("oman_au"))
    }

    @Test
    fun undiscoveringClearsBoth() {
        val p = ShrineProgress(completed = setOf("oman_au"), found = setOf("oman_au", "ja_baij"))
            .withState("oman_au", ShrineState.UNDISCOVERED)
        assertEquals(ShrineProgress(found = setOf("ja_baij")), p)
    }

    @Test
    fun spiritOrbsCountOnlyCompletedShrines() {
        val catalog = ShrineCatalog(
            regions = listOf("Great Plateau"),
            upgrades = OrbUpgrades(4, 3, 30, 1, 10, 4),
            shrines = listOf(
                Shrine("oman_au", "Oman Au", "Great Plateau", "Magnesis Trial", TrialType.PUZZLE, location = "Here"),
                Shrine("ja_baij", "Ja Baij", "Great Plateau", "Bomb Trial", TrialType.PUZZLE, location = "There"),
            ),
        )
        val p = ShrineProgress().withState("oman_au", ShrineState.COMPLETED).withState("ja_baij", ShrineState.FOUND)
        assertEquals(1, OrbPlan.of(catalog, p, includeDlc = true).earned)
    }
}
