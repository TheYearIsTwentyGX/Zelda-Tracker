package gx.zelda.compendium.data

import gx.zelda.armor.data.ArmorRepository
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.EntrySource
import gx.zelda.compendium.model.PictureSale
import gx.zelda.core.Games
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled BotW compendium.json, plus spot checks against the in-game numbering. */
class BotwCompendiumDatasetTest {
    private fun withCatalog(block: (CompendiumCatalog) -> Unit) = runTest { block(CompendiumRepository.load(Games.BOTW)) }

    private fun CompendiumCatalog.count(category: CompendiumCategory, masterMode: Boolean) =
        entriesFor(masterMode).count { it.category == category }

    @Test
    fun hasEveryEntry() = withCatalog { c ->
        assertEquals(394, c.entries.size, "Master Mode with both DLC packs")
        assertEquals(389, c.entriesFor(masterMode = false).size, "Normal Mode with both DLC packs")
        assertEquals(385, c.entries.count { it.source == EntrySource.BASE }, "base game")
    }

    @Test
    fun categoryCountsMatchTheGame() = withCatalog { c ->
        val normal = mapOf(
            CompendiumCategory.CREATURES to 83,
            CompendiumCategory.MONSTERS to 81,
            CompendiumCategory.MATERIALS to 36,
            CompendiumCategory.EQUIPMENT to 185,
            CompendiumCategory.TREASURE to 4,
        )
        normal.forEach { (cat, n) -> assertEquals(n, c.count(cat, masterMode = false), "$cat in Normal Mode") }
        normal.forEach { (cat, n) ->
            val master = if (cat == CompendiumCategory.MONSTERS) n + 5 else n
            assertEquals(master, c.count(cat, masterMode = true), "$cat in Master Mode")
        }
        val creatures = c.entries.filter { it.category == CompendiumCategory.CREATURES }
        assertEquals(47, creatures.count { !it.food })
        assertEquals(36, creatures.count { it.food })
        assertTrue(c.entries.none { it.food && it.category != CompendiumCategory.CREATURES }, "only creatures are food")
    }

    @Test
    fun numbersAreContiguousInListOrder() = withCatalog { c ->
        assertEquals((1..394).toList(), c.entries.map { it.masterNumber })
        assertEquals((1..389).toList(), c.entriesFor(masterMode = false).map { it.number })
    }

    @Test
    fun categoriesAreContiguousAndInGameOrder() = withCatalog { c ->
        val runs = c.entries.map { it.category }.zipWithNext().count { (a, b) -> a != b } + 1
        assertEquals(CompendiumCategory.entries.size, runs, "each category is one run")
        assertEquals(CompendiumCategory.entries, c.entries.map { it.category }.distinct())
        // Food is the second half of Creatures: Hyrule Bass (048) to Fireproof Lizard (083).
        assertEquals((48..83).toList(), c.entries.filter { it.food }.map { it.number })
    }

    @Test
    fun idsAndNamesAreUnique() = withCatalog { c ->
        listOf(c.entries.map { it.id }, c.entries.map { it.name.lowercase() }).forEach {
            assertEquals(it.size, it.toSet().size, "duplicates: ${it.groupBy { v -> v }.filter { e -> e.value.size > 1 }.keys}")
        }
        c.entries.forEach { assertTrue(Regex("[a-z0-9_]+").matches(it.id), "bad id ${it.id}") }
    }

    @Test
    fun dlcEntriesAreMarked() = withCatalog { c ->
        assertEquals(
            setOf("monk_maz_koshia", "igneo_talus_titan", "molduking", "one_hit_obliterator"),
            c.entries.filter { it.source == EntrySource.DLC2 }.map { it.id }.toSet(),
        )
        val masterOnly = c.entries.filter { it.source == EntrySource.MASTER_MODE }
        assertEquals(
            setOf("sky_octorok", "golden_bokoblin", "golden_moblin", "golden_lizalfos", "golden_lynel"),
            masterOnly.map { it.id }.toSet(),
        )
        masterOnly.forEach { assertNull(it.number, "${it.id} isn't on Normal Mode files") }
        c.entries.filter { it.source != EntrySource.MASTER_MODE }.forEach { assertNotNull(it.number, "${it.id} has no number") }
    }

    @Test
    fun spotChecksMatchTheGame() = withCatalog { c ->
        // id to (Normal number, Master Mode number), with both DLC packs.
        val expected = mapOf(
            "horse" to (1 to 1),
            "hyrule_bass" to (48 to 48),
            "chuchu" to (84 to 84),
            "sky_octorok" to (null to 97),
            "golden_lynel" to (null to 129),
            "master_kohga" to (136 to 141),
            "monk_maz_koshia" to (137 to 142),
            "molduking" to (152 to 157),
            "dark_beast_ganon" to (164 to 169),
            "apple" to (165 to 170),
            "master_sword" to (201 to 206),
            "one_hit_obliterator" to (244 to 249),
            "dragon_bone_boko_bow" to (340 to 345),
            "savage_lynel_shield" to (385 to 390),
            "luminous_stone_deposit" to (389 to 394),
        )
        expected.forEach { (id, numbers) ->
            val entry = assertNotNull(c.entry(id), "missing $id")
            assertEquals(numbers, entry.number to entry.masterNumber, id)
        }
    }

    @Test
    fun everyEntryIsDescribed() = withCatalog { c ->
        for (entry in c.entries) {
            assertTrue(entry.name.isNotBlank(), "${entry.id} has no name")
            assertTrue(entry.locations.isNotEmpty(), "${entry.id} lists no locations")
            (entry.locations + entry.drops).forEach { assertTrue(it.isNotBlank(), "${entry.id} has a blank location or drop") }
            assertEquals(entry.drops.size, entry.drops.toSet().size, "${entry.id} repeats a drop")
            entry.note?.let { assertTrue(it.isNotBlank(), "${entry.id} has a blank note") }
            if (entry.limited) assertNotNull(entry.note, "${entry.id} is limited but doesn't say why")
        }
    }

    @Test
    fun picturesSyminSellsMatchTheGame() = withCatalog { c ->
        assertEquals(
            setOf(
                "silver_bokoblin", "golden_bokoblin", "silver_moblin", "golden_moblin",
                "silver_lizalfos", "golden_lizalfos", "silver_lynel", "golden_lynel",
                "master_kohga", "monk_maz_koshia",
                "thunderblight_ganon", "fireblight_ganon", "waterblight_ganon", "windblight_ganon",
                "calamity_ganon", "dark_beast_ganon",
            ),
            c.entries.filter { it.picture == PictureSale.ELITE }.map { it.id }.toSet(),
        )
        // "Sunshroom Sensing" registers the Sunshroom itself, so it's the one picture Symin never stocks.
        assertEquals(listOf("sunshroom"), c.entries.filter { it.picture == PictureSale.NONE }.map { it.id })
    }

    @Test
    fun sharesIdsWithArmorMaterials() = runTest {
        val compendium = CompendiumRepository.load(Games.BOTW)
        val materials = ArmorRepository.load(Games.BOTW).materials
        val byName = compendium.entries.associateBy { it.name }
        val shared = materials.filter { it.name in byName }
        assertTrue(shared.size >= 15, "expected plants and creatures in both, found ${shared.map { it.name }}")
        shared.forEach { assertEquals(it.id, byName.getValue(it.name).id, "${it.name} has different ids") }
    }
}
