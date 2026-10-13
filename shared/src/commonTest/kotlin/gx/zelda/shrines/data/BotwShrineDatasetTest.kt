package gx.zelda.shrines.data

import gx.zelda.core.Games
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.TrialType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled BotW shrines.json, plus spot checks of well-known shrines. */
class BotwShrineDatasetTest {
    private fun withCatalog(block: (ShrineCatalog) -> Unit) = runTest { block(ShrineRepository.load(Games.BOTW)) }

    @Test
    fun hasEveryShrine() = withCatalog { c ->
        assertEquals(120, c.base.size)
        assertEquals(16, c.dlc.size)
    }

    @Test
    fun idsAndNamesAreUnique() = withCatalog { c ->
        listOf(c.shrines.map { it.id }, c.shrines.map { it.name }).forEach {
            assertEquals(it.size, it.toSet().size, "duplicates: ${it.groupBy { v -> v }.filter { e -> e.value.size > 1 }.keys}")
        }
        c.shrines.forEach { assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(it.id), "bad id ${it.id}") }
    }

    @Test
    fun everyRegionIsATowerRegion() = withCatalog { c ->
        assertEquals(
            listOf(
                "Great Plateau", "Dueling Peaks", "Hateno", "Lanayru", "Faron", "Lake", "Central", "Ridgeland",
                "Tabantha", "Hebra", "Woodland", "Eldin", "Akkala", "Wasteland", "Gerudo",
            ),
            c.regions,
        )
        c.shrines.forEach { assertTrue(it.region in c.regions, "${it.id}: unknown region ${it.region}") }
    }

    /** Base-game shrines per tower, as Game8 and Thonky group them. */
    @Test
    fun baseShrinesPerRegion() = withCatalog { c ->
        assertEquals(
            mapOf(
                "Great Plateau" to 4, "Dueling Peaks" to 9, "Hateno" to 7, "Lanayru" to 9, "Faron" to 8,
                "Lake" to 6, "Central" to 8, "Ridgeland" to 7, "Tabantha" to 6, "Hebra" to 13,
                "Woodland" to 8, "Eldin" to 9, "Akkala" to 8, "Wasteland" to 12, "Gerudo" to 6,
            ),
            c.base.groupingBy { it.region }.eachCount(),
        )
    }

    @Test
    fun spiritOrbsMatchTheGame() = withCatalog { c ->
        assertTrue(c.base.all { it.orb }, "every base-game shrine gives an orb")
        val dlcWithOrbs = c.dlc.filter { it.orb }
        assertEquals(listOf("Great Plateau"), dlcWithOrbs.map { it.region }.distinct())
        assertEquals(4, dlcWithOrbs.size, "only the DLC's Great Plateau shrines give orbs")
        assertEquals(124, c.shrines.count { it.orb })

        val up = c.upgrades
        assertEquals(4, up.orbsPerUpgrade)
        assertEquals(3, up.startingHearts)
        assertEquals(30, up.maxHearts)
        assertEquals(1, up.startingStaminaWheels)
        assertEquals(10, up.maxStaminaVessels)
        assertEquals(4, up.bossHearts)
    }

    @Test
    fun shrineQuestsMatchTheGame() = withCatalog { c ->
        val quests = c.shrines.mapNotNull { it.quest }
        assertEquals(42, quests.size, "BotW has 42 Shrine Quests, each leading to one shrine")
        assertEquals(quests.size, quests.toSet().size)
        c.dlc.forEach { assertNull(it.quest, "${it.id}: DLC shrines come from EX quests, not Shrine Quests") }
    }

    @Test
    fun trialTypesAgreeWithTrialNames() = withCatalog { c ->
        for (shrine in c.shrines) {
            assertEquals(shrine.type == TrialType.BLESSING, shrine.trial == "${shrine.name}'s Blessing", "${shrine.id}: ${shrine.trial}")
            if ("Test of Strength" in shrine.trial) assertEquals(TrialType.COMBAT, shrine.type, shrine.id)
        }
    }

    @Test
    fun everyShrineIsFullyDescribed() = withCatalog { c ->
        for (shrine in c.shrines) {
            listOf(shrine.name, shrine.trial, shrine.location).forEach {
                assertTrue(it.isNotBlank(), "${shrine.id} has a blank field")
            }
            assertTrue(!shrine.name.endsWith("Shrine"), "${shrine.id}: name includes \"Shrine\"")
            shrine.quest?.let { assertTrue(it.isNotBlank(), "${shrine.id} has a blank quest") }
        }
    }

    @Test
    fun spotChecks() = withCatalog { c ->
        fun shrine(id: String) = c.shrines.first { it.id == id }
        with(shrine("oman_au")) {
            assertEquals("Great Plateau", region)
            assertEquals("Magnesis Trial", trial)
        }
        assertEquals("The Stolen Heirloom", shrine("lakna_rokee").quest)
        assertEquals(TrialType.COMBAT, shrine("chaas_qeta").type)
        // Tower regions that differ from the wikis' geographic ones.
        assertEquals("Lake", shrine("ishto_soh").region)
        assertEquals("Lanayru", shrine("kah_mael").region)
        assertEquals("Woodland", shrine("rona_kachta").region)
        with(shrine("etsu_korima")) {
            assertTrue(dlc)
            assertTrue(orb)
        }
        with(shrine("kee_dafunia")) {
            assertTrue(dlc)
            assertTrue(!orb)
        }
    }
}
