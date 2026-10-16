package gx.zelda.kinstones.data

import gx.zelda.core.Games
import gx.zelda.kinstones.model.FusionReward
import gx.zelda.kinstones.model.KinstoneCatalog
import gx.zelda.kinstones.model.KinstoneColor
import gx.zelda.kinstones.model.PartnerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural checks on the bundled Minish Cap kinstones.json, against the counts Zelda Wiki,
 * GameFAQs, Thonky and the game's own data agree on (see docs/mc-kinstones.md).
 */
class McKinstoneDatasetTest {
    private fun withCatalog(block: (KinstoneCatalog) -> Unit) = runTest { block(KinstoneRepository.load(Games.MC)) }

    @Test
    fun oneHundredFusions() = withCatalog { c ->
        assertEquals(100, c.fusions.size)
        assertEquals((1..100).toList(), c.fusions.map { it.number }.sorted(), "Thonky's numbers, once each")
    }

    @Test
    fun idsAreUnique() = withCatalog { c ->
        val ids = c.fusions.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicates: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
        assertTrue(ids.all { it.matches(Regex("[a-z0-9_]+")) }, "ids are lower_snake_case")
    }

    /** zeldaret/tmc numbers the fusions 0x01–0x64: one id each. */
    @Test
    fun gameIdsCoverTheGamesTable() = withCatalog { c ->
        assertEquals((1..100).map { "0x" + it.toString(16).uppercase().padStart(2, '0') }.toSet(), c.fusions.map { it.gameId }.toSet())
        // The decomp's order: five clouds, three statues, the Source of the Flow, then red, blue and green.
        val byId = c.fusions.associateBy { it.gameId.removePrefix("0x").toInt(16) }
        assertTrue((1..9).all { byId.getValue(it).color == KinstoneColor.GOLD })
        assertTrue((0x0A..0x21).all { byId.getValue(it).color == KinstoneColor.RED })
        assertTrue((0x22..0x33).all { byId.getValue(it).color == KinstoneColor.BLUE })
        assertTrue((0x34..0x64).all { byId.getValue(it).color == KinstoneColor.GREEN })
    }

    @Test
    fun countsByColour() = withCatalog { c ->
        assertEquals(
            mapOf(KinstoneColor.GREEN to 49, KinstoneColor.BLUE to 18, KinstoneColor.RED to 24, KinstoneColor.GOLD to 9),
            c.fusions.groupingBy { it.color }.eachCount(),
        )
    }

    /** Three statues in Castor Wilds, five clouds in the Cloud Tops and the Source of the Flow. */
    @Test
    fun theNineGoldFusionsAreTheStoryObjects() = withCatalog { c ->
        val gold = c.fusions.filter { it.isGold }
        assertEquals(mapOf("Castor Wilds" to 3, "Cloud Tops" to 5, "Veil Falls" to 1), gold.groupingBy { it.region }.eachCount())
        assertTrue(gold.all { it.partnerKind == PartnerKind.OBJECT && it.shape == null && FusionReward.WAY in it.rewards })
    }

    /** The 18 shared fusions any random fuser can offer: 16 green, the fairy tree (blue) and the garden heart (red). */
    @Test
    fun eighteenRandomFusions() = withCatalog { c ->
        val random = c.fusions.filter { it.random }
        assertEquals(18, random.size)
        assertEquals(mapOf(KinstoneColor.GREEN to 16, KinstoneColor.BLUE to 1, KinstoneColor.RED to 1), random.groupingBy { it.color }.eachCount())
        assertTrue(c.fusions.all { it.random == (it.region == c.randomRegion) }, "random fusions, and only they, are under ${c.randomRegion}")
        assertEquals((4..19).toSet() + 21 + 22, random.map { it.number }.toSet())
    }

    /** 86 characters; 14 objects: the nine Gold ones and the five Mysterious Walls. */
    @Test
    fun partners() = withCatalog { c ->
        val objects = c.fusions.filter { it.partnerKind == PartnerKind.OBJECT }
        assertEquals(14, objects.size)
        assertEquals(5, objects.count { it.partner.startsWith("Mysterious Wall") })
    }

    /** Thonky's three green, two blue and three red shapes; the Goron Cave six depend on the order they're done in. */
    @Test
    fun shapesPerColour() = withCatalog { c ->
        val order = "squarish or S-curve"
        assertEquals(
            mapOf(
                KinstoneColor.GREEN to setOf("triangle wedge", "square tooth", "slant"),
                KinstoneColor.BLUE to setOf("squarish", "S-curve", order),
                KinstoneColor.RED to setOf("jagged teeth", "long teeth", "triangle wedge"),
            ),
            c.fusions.filterNot { it.isGold }.groupBy { it.color }.mapValues { (_, fs) -> fs.map { it.shape!! }.toSet() },
        )
        assertEquals(setOf("Eenie") + (1..5).map { "Mysterious Wall" }, c.fusions.filter { it.shape == order }.map { it.partner.substringBefore(" (") }.toSet())
        assertEquals(6, c.fusions.count { it.shape == order })
    }

    /** Nine Pieces of Heart and Librari's Heart Container. */
    @Test
    fun heartsAndGoldenMonsters() = withCatalog { c ->
        assertEquals(10, c.fusions.count { FusionReward.HEART in it.rewards })
        assertEquals(1, c.fusions.count { "Heart Container" in it.result })
        val golden = c.fusions.filter { FusionReward.GOLDEN_MONSTER in it.rewards }
        assertEquals(9, golden.size)
        assertEquals(
            mapOf("Octorok" to 3, "Rope" to 3, "Tektite" to 3),
            golden.groupingBy { Regex("Golden (\\w+)").find(it.result)!!.groupValues[1] }.eachCount(),
        )
        assertEquals(3, c.fusions.count { FusionReward.TECHNIQUE in it.rewards }, "the three Blade Brothers' dojos")
    }

    @Test
    fun everyRegionAndStageIsKnown() = withCatalog { c ->
        assertEquals(c.regions.size, c.regions.toSet().size)
        assertEquals("Random fusers", c.randomRegion)
        c.fusions.forEach { f ->
            assertTrue(f.region in c.regions, "${f.id}: ${f.region}")
            f.resultRegion?.let { assertTrue(it in c.regions && it != c.randomRegion, "${f.id}: $it") }
            assertTrue(f.stage in 1..c.stages.size, "${f.id}: stage ${f.stage}")
            assertTrue(f.rewards.isNotEmpty(), "${f.id} gives something")
        }
        assertEquals(6, c.stages.size)
        // Every region but the random pseudo-region has a fusion, so none is an empty heading.
        assertEquals(c.regions.toSet(), c.fusions.map { it.region }.toSet())
    }

    @Test
    fun textFieldsAreFilledIn() = withCatalog { c ->
        c.fusions.forEach { f ->
            listOf(f.partner, f.location, f.result).forEach { assertTrue(it.isNotBlank() && it == it.trim(), "${f.id}: '$it'") }
            listOfNotNull(f.requires, f.missable, f.note).forEach { assertTrue(it.isNotBlank(), f.id) }
        }
    }

    /** Only Eenie's European-version bug can lose a fusion for good. */
    @Test
    fun missableFusions() = withCatalog { c ->
        assertEquals(setOf("strato", "eenie"), c.fusions.filter { it.missable != null }.map { it.id }.toSet())
        assertNull(c.fusion("librari")!!.missable, "Librari moves to Lake Hylia when the Library closes")
    }

    /** The list order: regions in catalog order, earliest stage first within each. */
    @Test
    fun listOrder() = withCatalog { c ->
        val keys = c.fusions.map { c.regions.indexOf(it.region) to it.stage }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)
    }
}
