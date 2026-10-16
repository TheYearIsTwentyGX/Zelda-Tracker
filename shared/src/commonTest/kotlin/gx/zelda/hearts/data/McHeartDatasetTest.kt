package gx.zelda.hearts.data

import gx.zelda.core.Games
import gx.zelda.hearts.model.HeartCatalog
import gx.zelda.hearts.model.HeartKind
import gx.zelda.hearts.model.HeartProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled MC heart_pieces.json, against Zelda Wiki's counts (see docs/mc-heart-pieces.md). */
class McHeartDatasetTest {
    private fun withCatalog(block: (HeartCatalog) -> Unit) = runTest { block(HeartRepository.load(Games.MC)) }

    /** Zelda Wiki, Piece of Heart and Heart Container: 44 pieces, six containers, 3 hearts to start and 20 at most. */
    @Test
    fun countsMatchZeldaWiki() = withCatalog { c ->
        assertEquals(44, c.pieces.size)
        assertEquals(6, c.containers.size)
        assertEquals(3, c.startingHearts)
        assertEquals(20, c.maxHearts)
    }

    @Test
    fun idsAreUnique() = withCatalog { c ->
        val ids = c.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicates: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
        ids.forEach { assertTrue(Regex("[a-z0-9_]+").matches(it), "bad id $it") }
    }

    @Test
    fun everyRegionIsListedAndUsed() = withCatalog { c ->
        assertEquals(c.regions.size, c.regions.toSet().size)
        c.entries.forEach { assertTrue(it.region in c.regions, "${it.id}: unknown region ${it.region}") }
        c.regions.forEach { r -> assertTrue(c.entries.any { it.region == r }, "$r has no entries") }
    }

    @Test
    fun piecesPerRegion() = withCatalog { c ->
        assertEquals(
            mapOf(
                "Minish Woods" to 3, "Minish Village" to 1, "Deepwood Shrine" to 2, "Hyrule Town" to 7,
                "South Hyrule Field" to 2, "Mount Crenel" to 5, "Cave of Flames" to 1, "North Hyrule Field" to 1,
                "Veil Falls" to 4, "Lon Lon Ranch" to 2, "Castor Wilds" to 3, "Hyrule Castle Garden" to 2,
                "Wind Ruins" to 1, "Fortress of Winds" to 1, "Eastern Hills" to 1, "Western Wood" to 1,
                "Lake Hylia" to 5, "Royal Valley" to 1, "Palace of Winds" to 1,
            ),
            c.pieces.groupingBy { it.region }.eachCount(),
        )
    }

    /** One container per boss, Vaati excepted, and Librari's. The Temple of Droplets has no piece, so it's under Lake Hylia. */
    @Test
    fun containersAreTheFiveBossesAndLibrari() = withCatalog { c ->
        assertEquals(
            listOf(
                "Big Green ChuChu" to "Deepwood Shrine",
                "Gleerok" to "Cave of Flames",
                "Mazaal" to "Fortress of Winds",
                "Big Octorok" to "Lake Hylia",
                "Librari's hideaway" to "Lake Hylia",
                "Gyorg Pair" to "Palace of Winds",
            ),
            c.containers.map { it.name to it.region },
        )
        assertTrue(c.containers.first { it.name == "Big Octorok" }.area.contains("Temple of Droplets"))
        with(c.containers.first { it.name == "Librari's hideaway" }) {
            assertTrue(kinstone && minish)
            assertTrue(assertNotNull(fusionNote).contains("Minish Village"))
        }
    }

    @Test
    fun everyEntryExplainsHowToGetIt() = withCatalog { c ->
        for (e in c.entries) {
            assertTrue(e.name.isNotBlank() && e.area.isNotBlank(), "${e.id} has no name or area")
            assertTrue(e.howTo.length >= 40, "${e.id}: howTo is too short")
            if (e.kinstone) assertNotNull(e.fusionNote, "${e.id}: a fusion entry should say which fusion") else assertNull(e.fusionNote, e.id)
        }
    }

    /** Item names follow the game's, so the search and the "Needs" line read the same everywhere. */
    @Test
    fun itemsAreKnownItems() = withCatalog { c ->
        val known = setOf(
            "Gust Jar", "Cane of Pacci", "Bombs", "Pegasus Boots", "Flippers", "Grip Ring", "Mole Mitts", "Roc's Cape",
            "Ocarina of Wind", "Flame Lantern", "Bow", "White Sword (2 elements)", "White Sword (3 elements)", "Four Sword",
        )
        for (e in c.entries) {
            e.items.forEach { assertTrue(it in known, "${e.id}: unknown item $it") }
            assertEquals(e.items.size, e.items.toSet().size, e.id)
        }
    }

    @Test
    fun requirementCounts() = withCatalog { c ->
        assertEquals(10, c.entries.count { it.kinstone })
        assertEquals(18, c.entries.count { it.minish })
        // The Hurdy-Gurdy Man's tree is the game's first fusion.
        assertTrue(c.entries.first { it.id == "south_hyrule_field_hurdy_gurdy_tree" }.kinstone)
    }

    @Test
    fun spotChecks() = withCatalog { c ->
        fun entry(id: String) = c.entries.first { it.id == id }
        with(entry("hyrule_town_bell")) {
            assertEquals(HeartKind.PIECE, kind)
            assertEquals(listOf("Roc's Cape"), items)
        }
        assertEquals(listOf("Bow", "Mole Mitts"), entry("container_fortress_of_winds").items)
        assertEquals(listOf("Grip Ring", "White Sword (2 elements)"), entry("mount_crenel_grayblade_dojo").items)
        assertEquals(listOf("Four Sword"), entry("hyrule_town_school_minish_path").items)
    }

    @Test
    fun aNewFileHasThreeHearts() = withCatalog { c ->
        val total = c.total(HeartProgress())
        assertEquals(3, total.hearts)
        assertEquals("3 hearts", total.heartsLabel)
        assertEquals(20, c.total(HeartProgress(c.entries.map { it.id }.toSet())).hearts)
    }
}
