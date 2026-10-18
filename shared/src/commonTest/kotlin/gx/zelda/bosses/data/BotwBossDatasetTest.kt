package gx.zelda.bosses.data

import gx.zelda.bosses.model.BossCatalog
import gx.zelda.bosses.model.BossProgress
import gx.zelda.bosses.model.tallies
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.core.Games
import gx.zelda.core.SaveFile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** On the Master Mode file, after the game's rank-up (see docs/overworld-bosses.md). */
    @Test
    fun masterModeVariantCounts() = withData { c, _ ->
        assertEquals(
            mapOf(
                "hinox" to 1, "blue_hinox" to 7, "black_hinox" to 22, "stalnox" to 10,
                "stone_talus" to 1, "stone_talus_luminous" to 10, "stone_talus_rare" to 19, "igneo_talus" to 5,
                "frost_talus" to 5, "igneo_talus_titan" to 1,
                "molduga" to 4, "molduking" to 1,
                "blue_maned_lynel" to 6, "white_maned_lynel" to 8, "silver_lynel" to 8, "lynel" to 1,
            ),
            c.spawnsFor(master).groupingBy { it.variant }.eachCount(),
        )
    }

    /**
     * The user's report: Gerudo Taluses listed as Luminous that were Rare. The game's map data places
     * Luminous ones (Enemy_Golem_Middle) there, and Master Mode ranks them up to Rare.
     */
    @Test
    fun gerudoTalusesByMode() = withData { c, _ ->
        fun gerudo(file: SaveFile) =
            c.spawnsFor(file).filter { it.kind == "talus" && it.region == "Gerudo" }.associate { it.hashId to it.variant }
        val luminous = listOf(3900382371, 3273877672, 372172601) // Rutimala Hill, Mount Nabooru, Gerudo Canyon Stable
        assertEquals(
            luminous.associateWith { "stone_talus_luminous" } +
                mapOf(2455625785L to "stone_talus_rare", 648092042L to "frost_talus", 4007690024L to "frost_talus"),
            gerudo(normal),
        )
        assertEquals(
            luminous.associateWith { "stone_talus_rare" } +
                mapOf(2455625785L to "stone_talus_rare", 648092042L to "frost_talus", 4007690024L to "frost_talus"),
            gerudo(master),
        )
        assertEquals(
            listOf("Rutimala Hill", "Mount Nabooru", "Gerudo Canyon Stable"),
            luminous.map { id -> c.spawns.first { it.hashId == id }.nearestPlace },
        )
    }

    /**
     * Master Mode raises Hinoxes, Stone Taluses and Lynels one tier (HardModeManager::rankUpEnemy):
     * the next variant in the kind's order, except where the game exempts the spawn. Stalnoxes,
     * Igneo and Frost Taluses, Moldugas, Black Hinoxes, Rare Taluses and the Ploymus Mountain Lynel
     * have no next tier.
     */
    @Test
    fun masterModeRanksUpOneTier() = withData { c, _ ->
        val chains = listOf(
            listOf("hinox", "blue_hinox", "black_hinox"),
            listOf("stone_talus", "stone_talus_luminous", "stone_talus_rare"),
            listOf("lynel", "blue_maned_lynel", "white_maned_lynel", "silver_lynel"),
        )
        val ploymus = 1396251553L
        for (s in c.spawns) {
            val chain = chains.firstOrNull { s.variant in it }
            val next = chain?.getOrNull(chain.indexOf(s.variant) + 1)
            val expected = if (s.noMasterModeRankUp || s.hashId == ploymus) null else next
            assertEquals(expected, s.masterModeVariant, "${s.id} ${s.variant} near ${s.nearestPlace}")
            if (expected == null) assertNull(s.masterModeDrops, s.id)
        }
        assertEquals(64, c.spawns.count { it.masterModeVariant != null })
        // The five the game exempts: the Great Plateau's Talus and extra Lynel, and three Hinoxes.
        assertEquals(
            mapOf("hinox" to 3, "talus" to 1, "lynel" to 1),
            c.spawns.filter { it.noMasterModeRankUp }.groupingBy { it.kind }.eachCount(),
        )
    }

    @Test
    fun aMasterModeFileMeetsTheRankedUpVariant() = withData { c, comp ->
        val eventide = c.spawns.first { it.hashId == 1925665429L }
        assertEquals("blue_hinox", eventide.onFile(masterMode = false).variant)
        with(c.spawnsFor(master).first { it.hashId == 1925665429L }) {
            assertEquals("black_hinox", variant)
            assertEquals(comp.entry("black_hinox")?.drops, drops)
            assertEquals("blue_hinox", rankedUpFrom)
        }
        assertNull(c.spawnsFor(normal).first { it.hashId == 1925665429L }.rankedUpFrom)
        // Ids don't change with the mode, so progress is kept under the same key.
        assertEquals(c.spawnsFor(normal).map { it.id }, c.spawnsFor(master).filter { !it.masterModeOnly }.map { it.id })
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
            s.masterModeVariant?.let { assertTrue(it in kind.variants, "${s.id}: $it isn't a ${kind.name} variant") }
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
            s.masterModeVariant?.let { assertEquals(comp.entry(it)?.drops, s.masterModeDrops, "${s.id} ($it)") }
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
