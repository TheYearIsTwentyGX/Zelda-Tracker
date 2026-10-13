package gx.zelda.fairies.data

import gx.zelda.core.Games
import gx.zelda.fairies.model.FairyCatalog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled TotK fairies.json. */
class TotkFairyDatasetTest {
    private fun withCatalog(block: (FairyCatalog) -> Unit) = runTest { block(FairyRepository.load(Games.TOTK)) }

    @Test
    fun hasTheFourTotkGreatFairiesWithTeraFirst() = withCatalog { c ->
        // Tera's quest unlocks the other three, so she leads the list.
        assertEquals(listOf("tera", "mija", "kaysa", "cotera"), c.fairies.map { it.id })
    }

    @Test
    fun fountainsAreFreedByQuestNotBought() = withCatalog { c ->
        assertFalse(c.isPaid)
        assertTrue(c.unlockCosts.isEmpty())
        assertNull(c.nextCost(0))
        assertEquals(listOf(10, 50, 200, 500), c.upgradeFees)
        assertEquals(
            listOf("Serenade to a Great Fairy", "Serenade to Mija", "Serenade to Kaysa", "Serenade to Cotera"),
            c.fairies.map { it.unlockQuest },
        )
    }

    @Test
    fun everyFairyIsFullyDescribed() = withCatalog { c ->
        for (fairy in c.fairies) {
            listOf(fairy.name, fairy.region, fairy.location, fairy.directions).forEach {
                assertTrue(it.isNotBlank(), "${fairy.id} has a blank field")
            }
            assertTrue(fairy.nearby.isNotEmpty(), "${fairy.id} lists no nearby landmarks")
            assertTrue(!fairy.notes.isNullOrBlank(), "${fairy.id} has no notes on the band member to find")
        }
    }
}
