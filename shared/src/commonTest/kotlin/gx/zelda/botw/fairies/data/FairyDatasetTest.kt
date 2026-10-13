package gx.zelda.botw.fairies.data

import gx.zelda.botw.fairies.model.FairyCatalog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Structural checks on the bundled fairies.json, which covers Breath of the Wild only. */
class FairyDatasetTest {
    private fun withCatalog(block: (FairyCatalog) -> Unit) = runTest { block(FairyRepository.load()) }

    @Test
    fun hasTheFourBotwGreatFairies() = withCatalog { c ->
        assertEquals(listOf("cotera", "kaysa", "mija", "tera"), c.fairies.map { it.id })
        assertEquals(listOf("Cotera", "Kaysa", "Mija", "Tera"), c.fairies.map { it.name })
        assertEquals(
            listOf("West Necluda", "Tabantha Frontier", "Akkala Highlands", "Gerudo Desert"),
            c.fairies.map { it.region },
        )
    }

    @Test
    fun unlockCostsMatchTheGame() = withCatalog { c ->
        assertEquals(listOf(100, 500, 1000, 10000), c.unlockCosts)
        assertEquals(c.fairies.size, c.unlockCosts.size, "one price per fountain")
    }

    @Test
    fun everyFairyIsFullyDescribed() = withCatalog { c ->
        for (fairy in c.fairies) {
            listOf(fairy.name, fairy.region, fairy.location, fairy.directions).forEach {
                assertTrue(it.isNotBlank(), "${fairy.id} has a blank field")
            }
            assertTrue(fairy.nearby.isNotEmpty(), "${fairy.id} lists no nearby landmarks")
            assertTrue(fairy.nearby.all { it.isNotBlank() }, "${fairy.id} has a blank landmark")
            fairy.notes?.let { assertTrue(it.isNotBlank(), "${fairy.id} has blank notes") }
        }
    }
}
