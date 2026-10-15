package gx.zelda.bosses.data

import gx.zelda.bosses.model.BossCatalog
import gx.zelda.bosses.model.BossProgress
import gx.zelda.bosses.model.tallies
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.core.Games
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Structural checks on the bundled BotW bosses.json, against Zelda Wiki's counts and the Compendium. */
class BotwBossDatasetTest {
    private fun withData(block: (BossCatalog, CompendiumCatalog) -> Unit) = runTest {
        block(BossRepository.load(Games.BOTW), CompendiumRepository.load(Games.BOTW))
    }

    private val normal = Games.BOTW.saveFiles.first { !it.masterMode }
    private val master = Games.BOTW.saveFiles.first { it.masterMode }

    @Test
    fun kindsAreTheFourOverworldBosses() = withData { c, _ ->
        assertEquals(listOf("hinox", "talus", "molduga", "lynel"), c.kinds.map { it.id })
        assertEquals(
            listOf("Medal of Honor: Hinox", "Medal of Honor: Talus", "Medal of Honor: Molduga", null),
            c.kinds.map { it.medal },
        )
    }

    /** Kilton's medals: all 40 Hinoxes and Stalnoxes, all 40 Taluses and all four Moldugas (Zelda Wiki, Medal of Honor). */
    @Test
    fun medalCountsMatchKilton() = withData { c, _ ->
        val counted = c.spawns.filter { it.countsForMedal }.groupingBy { it.kind }.eachCount()
        assertEquals(mapOf("hinox" to 40, "talus" to 40, "molduga" to 4, "lynel" to 22), counted)
        c.kinds.filter { it.medal != null }.forEach { assertEquals(counted[it.id], it.medalTotal, it.id) }
    }

    /** Zelda Wiki, Lynel: 22 in Hyrule, one more on the Great Plateau in Master Mode. */
    @Test
    fun spawnsPerSaveFile() = withData { c, _ ->
        assertEquals(mapOf("hinox" to 40, "talus" to 41, "molduga" to 5, "lynel" to 22), c.spawnsFor(normal).groupingBy { it.kind }.eachCount())
        assertEquals(mapOf("hinox" to 40, "talus" to 41, "molduga" to 5, "lynel" to 23), c.spawnsFor(master).groupingBy { it.kind }.eachCount())
        assertEquals(listOf("Great Plateau"), c.spawns.filter { it.masterModeOnly }.map { it.region })
    }

    @Test
    fun variantCounts() = withData { c, _ ->
        assertEquals(
            mapOf(
                "hinox" to 7, "blue_hinox" to 16, "black_hinox" to 7, "stalnox" to 10,
                "stone_talus" to 11, "stone_talus_luminous" to 12, "stone_talus_rare" to 7, "igneo_talus" to 5,
                "frost_talus" to 5, "igneo_talus_titan" to 1,
                "molduga" to 4, "molduking" to 1,
                "lynel" to 7, "blue_maned_lynel" to 7, "white_maned_lynel" to 9,
            ),
            c.spawns.groupingBy { it.variant }.eachCount(),
        )
    }

    @Test
    fun onlyTheChampionsBalladTitansAreDlc() = withData { c, _ ->
        assertEquals(setOf("igneo_talus_titan", "molduking"), c.spawns.filter { it.dlc }.map { it.variant }.toSet())
    }

    @Test
    fun idsAreUnique() = withData { c, _ ->
        val ids = c.spawns.flatMap { listOf(it.hashId) + it.otherHashIds }
        assertEquals(ids.size, ids.toSet().size, "duplicates: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun everyRegionIsATowerRegion() = withData { c, _ ->
        assertEquals(
            listOf(
                "Great Plateau", "Dueling Peaks", "Hateno", "Lanayru", "Faron", "Lake", "Central", "Ridgeland",
                "Tabantha", "Hebra", "Woodland", "Eldin", "Akkala", "Wasteland", "Gerudo",
            ),
            c.regions,
        )
        c.spawns.forEach { assertTrue(it.region in c.regions, "${it.id}: unknown region ${it.region}") }
        // Every Molduga is in the desert.
        assertEquals(setOf("Wasteland"), c.spawns.filter { it.kind == "molduga" }.map { it.region }.toSet())
    }

    @Test
    fun everySpawnIsOnTheMap() = withData { c, _ ->
        for (s in c.spawns) {
            assertTrue(s.x in -5000.0..5000.0 && s.z in -4000.0..4000.0, "${s.id} is off the map: ${s.x}, ${s.z}")
            assertTrue(Regex("[A-J]-[1-8]").matches(s.mapSection), "${s.id}: bad map section ${s.mapSection}")
            assertTrue(s.nearestPlace.isNotBlank(), "${s.id} has no nearest place")
        }
    }

    @Test
    fun variantsAreCompendiumMonstersOfTheirKind() = withData { c, comp ->
        val kinds = c.kinds.associateBy { it.id }
        for (s in c.spawns) {
            val kind = assertNotNull(kinds[s.kind], "${s.id}: unknown kind ${s.kind}")
            assertTrue(s.variant in kind.variants, "${s.id}: ${s.variant} isn't a ${kind.name} variant")
        }
        for (variant in c.kinds.flatMap { it.variants }) {
            val entry = assertNotNull(comp.entry(variant), "$variant has no Compendium entry")
            assertEquals(CompendiumCategory.MONSTERS, entry.category, variant)
        }
    }

    /** Drops reuse the Compendium's names: each spawn lists exactly its variant's Compendium drops. */
    @Test
    fun dropsResolveToCompendiumNames() = withData { c, comp ->
        for (s in c.spawns) {
            assertEquals(comp.entry(s.variant)?.drops, s.drops, "${s.id} (${s.variant})")
        }
        assertTrue(c.spawns.first { it.variant == "hinox" }.drops.containsAll(listOf("Hinox Toenail", "Hinox Tooth", "Hinox Guts")))
    }

    /** World-level scaling is a Lynel thing; Hinox, Talus and Molduga colours are fixed in the map data. */
    @Test
    fun onlyLynelsScale() = withData { c, _ ->
        assertEquals(setOf("lynel"), c.spawns.filter { it.scalesWithWorldLevel }.map { it.kind }.toSet())
        // The Ploymus Mountain, gatehouse and Coliseum Lynels keep their colour (Zelda Wiki, Lynel).
        assertEquals(18, c.spawns.count { it.scalesWithWorldLevel })
    }

    /** objmap's `#/map/z<zoom>,<x>,<z>?id=MainField,<section>,<hashId>` opens the boss's details. */
    @Test
    fun objmapLinksPointAtTheBoss() = withData { c, _ ->
        assertEquals(
            "https://objmap.zeldamods.org/#/map/z7,4537.11,3701.95?id=MainField,J-8,1925665429",
            c.spawns.first { it.id == "1925665429" }.objmapUrl,
        )
    }

    /** What the tabs and a progress overview report for a new file: every kind from zero. */
    @Test
    fun talliesStartAtZero() = withData { c, _ ->
        assertEquals(
            listOf("Hinox 0 / 40 · Medal of Honor: Hinox", "Talus 0 / 40 · Medal of Honor: Talus",
                "Molduga 0 / 4 · Medal of Honor: Molduga", "Lynel 0 / 22 · no Medal of Honor for Lynels"),
            c.tallies(normal, BossProgress()).map { it.headline },
        )
        assertEquals(listOf(40, 41, 5, 23), c.tallies(master, BossProgress()).map { it.total })
    }

    @Test
    fun spotChecks() = withData { c, _ ->
        fun spawn(id: Long) = c.spawns.first { it.hashId == id }
        with(spawn(1925665429)) {
            assertEquals("blue_hinox", variant)
            assertEquals("Eventide Island", nearestPlace)
        }
        with(spawn(73882430)) {
            assertEquals("stalnox", variant)
            assertEquals("Central", region)
            assertEquals("Lockup", nearestPlace)
        }
        with(spawn(4200912374)) {
            assertEquals("Coliseum Ruins", nearestPlace)
            assertEquals(3, otherHashIds.size)
        }
    }
}
