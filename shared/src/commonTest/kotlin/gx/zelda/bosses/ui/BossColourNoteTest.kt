package gx.zelda.bosses.ui

import gx.zelda.bosses.model.BossSpawn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The detail screen's "Colour" note: world-level scaling and what the save file's mode does. */
class BossColourNoteTest {
    private val names = mapOf(
        "stone_talus_luminous" to "Stone Talus (Luminous)",
        "stone_talus_rare" to "Stone Talus (Rare)",
        "lynel" to "Lynel",
        "blue_maned_lynel" to "Blue-Maned Lynel",
    )

    private fun note(spawn: BossSpawn, masterMode: Boolean) =
        colourNote(spawn.onFile(masterMode), masterMode) { names[it] ?: it }

    private fun spawn(
        kind: String,
        variant: String,
        masterModeVariant: String? = null,
        scales: Boolean = false,
        exempt: Boolean = false,
    ) = BossSpawn(
        1, "A-6", kind, variant, "Gerudo", 0.0, 0.0, 0.0, "Rutimala Hill",
        scalesWithWorldLevel = scales, noMasterModeRankUp = exempt, masterModeVariant = masterModeVariant,
    )

    @Test
    fun aRankedUpSpawnSaysWhatItIsInTheOtherMode() {
        val talus = spawn("talus", "stone_talus_luminous", masterModeVariant = "stone_talus_rare")
        assertEquals("On a Master Mode file it's a Stone Talus (Rare): Master Mode ranks it up a tier.", note(talus, false))
        assertEquals("Master Mode ranks it up a tier: on a Normal Mode file it's a Stone Talus (Luminous).", note(talus, true))
    }

    @Test
    fun aFixedSpawnHasNoNoteOnNormalMode() {
        val rare = spawn("talus", "stone_talus_rare")
        assertNull(note(rare, false))
        assertEquals("Master Mode's rank-up doesn't change this one.", note(rare, true))
        assertEquals("The game exempts this one from Master Mode's rank-up.", note(spawn("talus", "stone_talus", exempt = true), true))
    }

    @Test
    fun aScalingLynelStartsFromItsModesWeakestColour() {
        val lynel = spawn("lynel", "lynel", masterModeVariant = "blue_maned_lynel", scales = true)
        assertEquals(
            "Shown as Blue-Maned Lynel, the weakest it can be. Its colour rises with the world level as you defeat " +
                "enemies, so later in a playthrough it can be a stronger Lynel. Master Mode ranks it up a tier: on a " +
                "Normal Mode file it's a Lynel.",
            note(lynel, true),
        )
    }
}
