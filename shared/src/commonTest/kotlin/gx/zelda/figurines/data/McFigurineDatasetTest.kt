package gx.zelda.figurines.data

import gx.zelda.core.Games
import gx.zelda.figurines.model.FigurineCatalog
import gx.zelda.figurines.model.FigurineProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural checks on the bundled files/mc/figurines.json, against Zelda Wiki's counts and the
 * zeldaret/tmc decompilation (see docs/mc-figurines.md).
 */
class McFigurineDatasetTest {
    private fun withCatalog(block: (FigurineCatalog) -> Unit) = runTest { block(FigurineRepository.load(Games.MC)) }

    @Test
    fun all136AreNumberedInOrder() = withCatalog { c ->
        assertEquals((1..136).toList(), c.figurines.map { it.number })
        assertEquals(c.figurines.size, c.figurines.map { it.name }.toSet().size, "names are unique")
    }

    /** Egg colours from the game's table: Zelda Wiki's Figurine Gallery lists the same split. */
    @Test
    fun categoryCounts() = withCatalog { c ->
        assertEquals(listOf("characters", "scenes", "enemies", "bosses"), c.categories.map { it.id })
        assertEquals(listOf("Blue", "Red", "Green", "Gold"), c.categories.map { it.egg })
        assertEquals(
            mapOf("characters" to 49, "scenes" to 21, "enemies" to 49, "bosses" to 17),
            c.figurines.groupingBy { it.category }.eachCount(),
        )
    }

    @Test
    fun everyFigurineHasAKnownCheckpointAndCategory() = withCatalog { c ->
        val checkpoints = c.checkpoints.map { it.id }.toSet()
        c.figurines.forEach { f ->
            assertTrue(f.checkpoint in checkpoints, "${f.label} ${f.checkpoint}")
            assertTrue(c.category(f.category) != null, "${f.label} ${f.category}")
        }
    }

    /** 130 before the final battle; Carlov's "final series" is the last six, made after the credits. */
    @Test
    fun finalSeriesIsTheLastSix() = withCatalog { c ->
        assertEquals(130, c.beforeFinalSeries.size)
        assertEquals((131..136).toList(), (c.figurines - c.beforeFinalSeries.toSet()).map { it.number })
        assertEquals(listOf("vaati_defeated"), c.checkpoints.filter { it.finalSeries }.map { it.id })
    }

    /** Zelda Wiki, Figurine Gallery: the "U" each story event adds, side tasks left out. */
    @Test
    fun storyCheckpointsAddWhatZeldaWikiSays() = withCatalog { c ->
        val added = c.figurines.filter { it.requirement == null }.groupingBy { it.checkpoint }.eachCount()
        assertEquals(listOf(48, 3, 9, 25, 1, 12, 5, 6, 10, 6), c.checkpoints.map { added[it.id] ?: 0 })
        assertEquals(11, c.figurines.count { it.requirement != null }, "the wiki's optional figurines")
    }

    @Test
    fun poolGrowsWithTheStory() = withCatalog { c ->
        val pools = c.checkpoints.map { cp -> c.figurines.count { c.isAvailable(it, FigurineProgress(checkpoint = cp.id)) } }
        assertEquals(listOf(48, 51, 60, 85, 86, 98, 103, 109, 119, 125), pools)
        val everything = FigurineProgress(checkpoint = "vaati_defeated", requirementsMet = c.figurines.map { it.number }.toSet())
        assertEquals(136, c.figurines.count { c.isAvailable(it, everything) })
    }

    @Test
    fun spotChecks() = withCatalog { c ->
        fun n(number: Int) = c.figurines.single { it.number == number }
        assertEquals(Triple("Capless Link", "characters", "gallery_opens"), n(1).let { Triple(it.name, it.category, it.checkpoint) })
        assertEquals(Triple("Big Blue Chuchu", "bosses", "big_blue_chuchu"), n(130).let { Triple(it.name, it.category, it.checkpoint) })
        assertEquals(Triple("Vaati's Wrath", "bosses", "vaati_defeated"), n(136).let { Triple(it.name, it.category, it.checkpoint) })
        // Beedle needs the dusty stall and, in the US version, Vaati's takeover of the king.
        assertEquals("king_possessed", n(22).checkpoint)
        assertTrue(n(22).requirement!!.contains("Gust Jar"))
        // Spookter & Spekter also needs the Royal Crypt.
        assertEquals("royal_crypt", n(67).checkpoint)
        assertNull(n(14).requirement)
        assertEquals("Carlov", n(14).name)
        assertEquals("palace_of_winds", n(14).checkpoint)
    }

    /** North American text, cleaned of wiki markup. */
    @Test
    fun descriptionsArePlainText() = withCatalog { c ->
        c.figurines.forEach { f ->
            assertTrue(f.description.isNotBlank(), f.label)
            assertTrue(f.pictured.isNotBlank(), f.label)
            listOf("{{", "}}", "[[", "<br", "Player Name").forEach { assertTrue(it !in f.description && it !in f.name, "${f.label}: $it") }
        }
        assertTrue(c.figurines.single { it.number == 16 }.description.endsWith("his dog, Fifi."), "NA text, not the EU's Borken")
    }
}
