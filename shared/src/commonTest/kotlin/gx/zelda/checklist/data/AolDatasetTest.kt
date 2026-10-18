package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zelda II's checklists against the counts in docs/aol.md, where Zelda Wiki, Zelda Dungeon and
 * StrategyWiki agree, plus the links between files. Format and in-file references are checked for
 * every game by [ChecklistDatasetsTest].
 */
class AolDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.AOL.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("aol/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.names(section: String? = null): Set<String> =
        (section?.let { section(it)!!.items } ?: items).map { it.name }.toSet()

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private val regions = listOf("Western Hyrule", "Death Mountain", "Eastern Hyrule")

    @Test
    fun threeSaveFilesAndAnOverviewFirst() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.AOL.saveFiles.map { it.id })
        assertEquals(TrackerKind.OVERVIEW, Games.AOL.trackers.first().kind)
        assertEquals(
            listOf("palaces", "items", "spells", "containers", "levels"),
            Games.AOL.trackers.drop(1).map { it.dataFile!!.removePrefix("aol/").removeSuffix(".json") },
        )
    }

    @Test
    fun everyFileUsesTheSameRegionsInTheSameOrder() = withCatalogs { all ->
        all.forEach { (name, c) ->
            assertEquals(regions.filter { it in c.regions }, c.regions, name)
            assertTrue(c.items.all { it.region in regions || it.tiers.isNotEmpty() }, name)
        }
    }

    @Test
    fun sevenPalacesAndTheGreatPalaceNeedsTheOtherSix() = withCatalogs { all ->
        val c = all.getValue("palaces")
        assertEquals(mapOf("palaces" to 7, "bosses" to 8), c.sizes())
        val great = c.item("great_palace")!!
        assertEquals(c.section("palaces")!!.items.dropLast(1).map { it.id }, great.requires)
        // Each boss fights in one of the palaces, one per palace and two in the Great Palace.
        val palaces = c.names("palaces")
        val bosses = c.section("bosses")!!.items
        assertTrue(bosses.all { it.area in palaces }, bosses.map { it.area }.toString())
        assertEquals(palaces.toList(), bosses.map { it.area }.distinct())
        assertEquals(listOf("Thunderbird", "Dark Link"), bosses.filter { it.area == "Great Palace" }.map { it.name })
        bosses.forEach { boss ->
            val palace = c.section("palaces")!!.items.single { it.name == boss.area }
            if (palace.id != "great_palace") assertTrue(palace.notes!!.startsWith("Boss: ${boss.name} ("), boss.id)
        }
    }

    /** Each palace's treasure is in the items file, found in that palace. */
    @Test
    fun palaceItemsMatchTheItemsFile() = withCatalogs { all ->
        val items = all.getValue("items").items
        val palaces = all.getValue("palaces").section("palaces")!!.items.filter { it.id != "great_palace" }
        palaces.forEach { palace ->
            val named = Regex("""Item: ([A-Z][a-z]+(?: [A-Z][a-z]+)?)""").find(palace.notes!!)!!.groupValues[1]
            assertEquals(palace.name, items.single { it.name == named }.area, named)
        }
        assertEquals(6, items.count { "Palace" in it.tags })
    }

    @Test
    fun fourteenItems() = withCatalogs { all ->
        val c = all.getValue("items")
        assertEquals(mapOf("items" to 14), c.sizes())
        assertEquals(mapOf("Palace" to 6, "Quest item" to 6, "Overworld" to 2), c.items.groupingBy { it.tags.single() }.eachCount())
    }

    /** A quest item's reward names a spell, and its spell's town is where the quest item goes. */
    @Test
    fun questItemsUnlockSpells() = withCatalogs { all ->
        val spells = all.getValue("spells").section("spells")!!.items
        val rewards = all.getValue("items").items.mapNotNull { it.reward?.removeSuffix(" spell") }
        assertEquals(listOf("Jump", "Life", "Fairy", "Fire", "Reflect"), rewards)
        rewards.forEach { name -> assertTrue(spells.any { it.name == name }, name) }
    }

    @Test
    fun eightSpellsInEightTownsAndTwoTechniques() = withCatalogs { all ->
        val c = all.getValue("spells")
        assertEquals(mapOf("spells" to 8, "techniques" to 2), c.sizes())
        assertEquals(
            listOf("Rauru", "Ruto", "Saria", "Mido", "Nabooru", "Darunia", "New Kasuto", "Old Kasuto"),
            c.section("spells")!!.items.map { it.area },
        )
        c.section("techniques")!!.items.forEach { assertEquals(listOf("jump"), it.requires, it.id) }
    }

    @Test
    fun metersGoFromFourToEight() = withCatalogs { all ->
        val c = all.getValue("containers")
        assertEquals(mapOf("heart_containers" to 4, "magic_containers" to 4), c.sizes())
        c.summaries.forEach { meter ->
            assertEquals(4.0, c.evaluate(meter, ChecklistProgress()).value, meter.label)
            assertEquals(8.0, c.evaluate(meter, c.allDone()).value, meter.label)
        }
        // New Kasuto's old woman wants 7 bars: the other three containers.
        assertEquals(listOf("mc_north_castle_cave", "mc_spectacle_rock", "mc_maze_island"), c.item("mc_new_kasuto")!!.requires)
    }

    @Test
    fun levelsGoToEightAndSixLinkDolls() = withCatalogs { all ->
        val c = all.getValue("levels")
        assertEquals(mapOf("levels" to 3, "link_dolls" to 6), c.sizes())
        c.section("levels")!!.items.forEach { stat ->
            assertEquals((2..8).map { "Level $it" }, stat.tiers.map { it.name.substringBefore(" (") }, stat.id)
        }
        // The two dolls inside palaces are lost once the palace is finished; nothing else can be missed.
        assertEquals(listOf("doll_three_eye_rock_palace", "doll_great_palace"), all.values.flatMap { f -> f.items.filter { it.missable } }.map { it.id })
    }

    @Test
    fun everyItemSaysWhereOrHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            val vague = c.items.filter { it.howTo.isNullOrBlank() || (it.area.isNullOrBlank() && it.tiers.isEmpty()) }.map(ChecklistItem::id)
            assertEquals(emptyList(), vague, name)
        }
    }
}
