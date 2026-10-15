package gx.zelda.shrines.data

import gx.zelda.core.Games
import gx.zelda.shrines.model.BlessingCurrency
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrineLayer
import gx.zelda.shrines.model.TrialType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled TotK shrines.json (shrines and Lightroots), plus spot checks. */
class TotkShrineDatasetTest {
    private fun withCatalog(block: (ShrineCatalog) -> Unit) = runTest { block(ShrineRepository.load(Games.TOTK)) }

    @Test
    fun hasEveryShrineAndLightroot() = withCatalog { c ->
        assertEquals(152, c.shrines.size)
        assertEquals(120, c.lightroots.size)
        assertTrue(c.shrines.none { it.dlc } && c.shrines.all { it.orb }, "no DLC; every shrine gives a Light of Blessing")
        assertEquals(152, c.base.size)
    }

    @Test
    fun idsAndNamesAreUnique() = withCatalog { c ->
        val lists = listOf(
            c.shrines.map { it.id }, c.shrines.map { it.name },
            c.lightroots.map { it.id }, c.lightroots.map { it.name },
            c.lightroots.map { it.shrine },
        )
        lists.forEach {
            assertEquals(it.size, it.toSet().size, "duplicates: ${it.groupBy { v -> v }.filter { e -> e.value.size > 1 }.keys}")
        }
        (c.shrines.map { it.id } + c.lightroots.map { it.id }).forEach {
            assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(it), "bad id $it")
        }
    }

    @Test
    fun everyLightrootIsItsShrinesNameReversed() = withCatalog { c ->
        c.lightroots.forEach { root ->
            val shrine = c.shrine(root.shrine)
            assertNotNull(shrine, "${root.id} points at unknown shrine ${root.shrine}")
            // Only the capitals move: Jochi-iu → Ui-ihcoj.
            assertEquals(shrine.name.reversed().lowercase().replaceFirstChar { it.uppercase() }, root.name)
            assertEquals(root, c.lightrootOf(shrine))
        }
        assertEquals("Kawumoro", c.lightrootOf(c.shrine("oromuwak")!!)?.name, "Kawumoro grows under Oromuwak Shrine")
    }

    @Test
    fun onlySkyShrinesHaveNoLightroot() = withCatalog { c ->
        val without = c.shrines.filter { c.lightrootOf(it) == null }
        assertEquals(32, without.size)
        assertEquals(c.shrines.filter { it.layer == ShrineLayer.SKY }, without)
        assertEquals(mapOf(ShrineLayer.SKY to 32, ShrineLayer.SURFACE to 91, ShrineLayer.CAVE to 29), c.shrines.groupingBy { it.layer }.eachCount())
    }

    @Test
    fun lightrootsLieBeneathTheirShrines() = withCatalog { c ->
        c.lightroots.forEach { root ->
            val shrine = c.shrineOf(root)
            val (sx, sz, rx, rz) = listOf(shrine.x, shrine.z, root.x, root.z).map { assertNotNull(it, "${root.id} position") }
            val dx = sx - rx
            val dz = sz - rz
            // Most share the shrine's x/z exactly; Mayaotaki's is the furthest off, 24 units east.
            assertTrue(dx * dx + dz * dz <= 25.0 * 25.0, "${root.name} is ${kotlin.math.sqrt(dx * dx + dz * dz)} from ${shrine.name}")
            assertTrue(root.y!! < 0, "${root.name} is in the Depths")
        }
        c.shrines.forEach { assertTrue(it.x != null && it.y != null && it.z != null, "${it.id} has a position") }
        assertTrue(c.shrines.filter { it.layer == ShrineLayer.SKY }.all { it.y!! > 500 }, "sky shrines are high up")
    }

    @Test
    fun everyRegionIsASkyviewTowerRegion() = withCatalog { c ->
        assertEquals(15, c.regions.size)
        assertEquals("Lookout Landing", c.regions.first())
        c.shrines.forEach { assertTrue(it.region in c.regions, "${it.id}: unknown region ${it.region}") }
        assertEquals(c.regions.toSet(), c.shrines.map { it.region }.toSet(), "every tower has a shrine")
        assertEquals(c.lightroots.size, c.lightrootsByRegion(c.lightroots).sumOf { it.second.size })
    }

    @Test
    fun fieldsAreFilledIn() = withCatalog { c ->
        c.shrines.forEach {
            assertTrue(it.trial.isNotBlank() && it.location.isNotBlank() && !it.area.isNullOrBlank(), it.id)
            assertTrue(it.location.length <= 110, "${it.id}: location hint too long")
        }
        assertEquals(31, c.shrines.count { it.quest != null }, "Zelda Wiki lists 31 Shrine Quests")
        assertEquals(51, c.shrines.count { it.trial == "Rauru's Blessing" })
        assertTrue(c.shrines.filter { it.trial == "Rauru's Blessing" }.all { it.type == TrialType.BLESSING })
        assertTrue(c.shrines.filter { it.trial.startsWith("Proving Grounds") || it.trial.startsWith("Combat Training") }.all { it.type == TrialType.COMBAT })
    }

    @Test
    fun spotChecks() = withCatalog { c ->
        val ukouh = c.shrine("ukouh")!!
        assertEquals("The Ability to Create", ukouh.trial)
        assertEquals(ShrineLayer.SKY, ukouh.layer)
        assertNull(c.lightrootOf(ukouh))
        assertEquals("The Ability to Combine", c.shrine("in_isa")!!.trial)
        assertEquals("Ui-ihcoj", c.lightrootOf(c.shrine("jochi_iu")!!)?.name)
        assertEquals("Legend of the Soaring Spear", c.shrine("utojis")!!.quest)
    }

    @Test
    fun blessingsMatchTheGame() = withCatalog { c ->
        val up = c.upgrades
        assertEquals(BlessingCurrency.LIGHT_OF_BLESSING, up.currency)
        assertEquals(4, up.orbsPerUpgrade)
        assertEquals(3, up.startingHearts)
        assertEquals(40, up.maxHearts)
        assertEquals(1, up.startingStaminaWheels)
        assertEquals(10, up.maxStaminaVessels)
        assertEquals(6, up.bossHearts, "five temple bosses and Phantom Ganon at Hyrule Castle")
        assertEquals("A Call from the Depths", up.freeUpgrade)
    }
}
