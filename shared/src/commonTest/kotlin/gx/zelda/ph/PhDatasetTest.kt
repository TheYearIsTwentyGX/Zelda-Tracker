package gx.zelda.ph

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phantom Hourglass's checklists against the counts in docs/ph.md, where Zelda Wiki, Zelda Dungeon
 * and IGN agree, plus the links between files. `ChecklistDatasetsTest` already checks every file's
 * format and in-file references.
 */
class PhDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("ph/$name.json", strict = true)

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.all(): ChecklistProgress = items.fold(ChecklistProgress()) { p, i -> p.withLevel(i, i.steps) }

    private fun ChecklistCatalog.summary(label: String, progress: ChecklistProgress = ChecklistProgress()) =
        evaluate(summaries.single { it.label == label }, progress)

    @Test
    fun theOverviewComesFirstAndEveryChecklistIsListed() {
        val trackers = Games.PH.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(FILES, trackers.mapNotNull { it.dataFile?.removePrefix("ph/")?.removeSuffix(".json") })
    }

    /** "You can create up to two different files" (Nintendo's manual). */
    @Test
    fun twoSaveFiles() {
        assertEquals(listOf("file_1", "file_2"), Games.PH.saveFiles.map { it.id })
    }

    /** Ids are saved progress: unique across every PH file too, so a search or a note can't confuse two. */
    @Test
    fun idsAreUniqueAcrossFiles() = runTest {
        val ids = FILES.flatMap { f -> load(f).let { c -> c.items.map { it.id } + c.sections.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }

    /** Zelda Wiki's four quadrants of the World of the Ocean King, in the order the Sea Charts open them. */
    @Test
    fun filesWithRegionsUseTheFourSeas() = runTest {
        FILES.forEach { f ->
            val c = load(f)
            if (c.regions.isNotEmpty()) assertEquals(SEAS, c.regions, f)
        }
    }

    @Test
    fun mainQuestIsOneChainThroughEveryTempleAndFloor() = runTest {
        val quest = load("main_quest")
        assertEquals(21, quest.items.size)
        quest.items.zipWithNext().forEach { (before, step) -> assertEquals(listOf(before.id), step.requires, step.id) }
        assertEquals("bellumbeck", quest.items.last().id)
        assertEquals(3.0, quest.summary("Spirits", quest.all()).value)
        assertEquals(4.0, quest.summary("Sea Charts", quest.all()).value)
        assertEquals(3.0, quest.summary("Pure Metals", quest.all()).value)
        assertEquals(0.0, quest.summary("Pure Metals").value)
        // The four charts and three metals are each one step's reward.
        listOf("Southwestern", "Northwestern", "Southeastern", "Northeastern").forEach { sea ->
            assertEquals(1, quest.items.count { it.reward == "$sea Sea Chart" || it.reward?.endsWith(", $sea Sea Chart") == true }, sea)
        }
        listOf("Crimsonine", "Azurine", "Aquanine").forEach { metal -> assertEquals(1, quest.items.count { it.reward?.startsWith(metal) == true }, metal) }
        assertEquals(7, quest.items.count { it.reward?.contains("Heart Container") == true })
    }

    @Test
    fun heartsAddUpToSixteen() = runTest {
        val hearts = load("heart_containers")
        assertEquals(mapOf("bosses" to 7, "others" to 6), hearts.sizes())
        assertEquals(3.0, hearts.summary("Hearts").value)
        assertEquals(16.0, hearts.summary("Hearts", hearts.all()).value)
    }

    /** Each boss's Heart Container is named for a boss the Main Quest's notes give ("Boss: Blaaz, …"). */
    @Test
    fun bossHeartContainersMatchTheMainQuest() = runTest {
        val notes = load("main_quest").items.mapNotNull { it.notes }.filter { "Boss:" in it }
        val bosses = load("heart_containers").section("bosses")!!.items.map { it.name.removeSuffix("'s Heart Container") }
        bosses.forEach { boss -> assertEquals(1, notes.count { "Boss: $boss" in it || "Boss: the Diabolical $boss" in it }, boss) }
    }

    @Test
    fun itemsAndCapacities() = runTest {
        val items = load("items")
        assertEquals(mapOf("equipment" to 9, "key_items" to 9, "hourglass" to 10), items.sizes())
        assertEquals(listOf("Oshus's Sword", "Phantom Sword"), items.item("sword")!!.tiers.map { it.name })
        listOf("Bombs" to 30.0, "Arrows" to 50.0, "Bombchus" to 30.0).forEach { (label, max) ->
            assertEquals(0.0 to max, items.summary(label).let { it.value to it.max }, label)
        }
        // 10 minutes to start, 25 with every boss's and Sunken Treasure's sand.
        assertEquals(10.0 to 25.0, items.summary("Phantom Hourglass minutes").let { it.value to it.max })
    }

    /** The five Sands of Hours on the hourglass tab are the five Treasure Maps that hold one. */
    @Test
    fun hourglassSandsAreTreasureMaps() = runTest {
        val sands = load("items").section("hourglass")!!.items.filter { it.id.startsWith("sand_map_") }
        val maps = load("treasure_maps").items.filter { it.reward!!.startsWith("Sand of Hours") }
        assertEquals(maps.map { it.name.removePrefix("Treasure Map #") }, sands.map { it.id.removePrefix("sand_map_") })
    }

    @Test
    fun twentyOfEachSpiritGem() = runTest {
        val gems = load("spirit_gems")
        assertEquals(mapOf("power" to 20, "wisdom" to 20, "courage" to 20, "spirit_upgrades" to 3), gems.sizes())
        listOf("Power", "Wisdom", "Courage").forEach { kind ->
            assertEquals(20.0, gems.summary("$kind Gems", gems.all()).value, kind)
            assertTrue(gems.section(kind.lowercase())!!.items.all { it.reward == "$kind Gem" }, kind)
        }
        assertTrue(gems.section("spirit_upgrades")!!.items.all { it.tiers.map { t -> t.name } == listOf("Level 1", "Level 2") })
    }

    @Test
    fun thirtyOneTreasureMapsInNumberOrder() = runTest {
        val maps = load("treasure_maps")
        assertEquals((1..31).map { "Treasure Map #$it" }, maps.items.map { it.name })
        assertTrue(maps.items.all { it.tiers.map { t -> t.name } == listOf("Map", "Salvaged") })
        assertEquals(0.0 to 31.0, maps.summary("Maps found").let { it.value to it.max })
        assertEquals(31.0, maps.summary("Treasures salvaged", maps.all()).value)
        // One map ticked but not salvaged counts as found, not salvaged.
        val one = ChecklistProgress().withLevel(maps.items.first(), 1)
        assertEquals(1.0 to 0.0, maps.summary("Maps found", one).value to maps.summary("Treasures salvaged", one).value)
        // Zelda Wiki: five Sands of Hours, two Treasures, one Big Gold Rupee, the rest Ship Parts.
        assertEquals(mapOf("Sand" to 5, "Treasure" to 2, "Big Gold Rupee" to 1, "Ship Part" to 23), maps.items.groupingBy {
            when {
                it.reward!!.startsWith("Sand") -> "Sand"
                "Treasure" in it.reward!! -> "Treasure"
                "Big Gold" in it.reward!! -> "Big Gold Rupee"
                else -> "Ship Part"
            }
        }.eachCount())
    }

    @Test
    fun eightSetsOfEightShipPartsAndEightTreasures() = runTest {
        val parts = load("ship_parts")
        val sets = parts.sections.filter { it.id.endsWith("_ship") }
        assertEquals(8, sets.size)
        sets.forEach { set ->
            assertEquals(listOf("Prow", "Anchor", "Hull", "Cannon", "Handrail", "Bridge", "Chimney", "Wheel"), set.items.map { it.tags.single() }, set.id)
        }
        assertEquals(8, parts.section("treasure")!!.items.size)
    }

    @Test
    fun seaChartIslandsAndFrogs() = runTest {
        val chart = load("sea_chart")
        assertEquals(mapOf("islands" to 16, "golden_frogs" to 6), chart.sizes())
        assertEquals(mapOf(SEAS[0] to 2, SEAS[1] to 1, SEAS[2] to 2, SEAS[3] to 1), chart.section("golden_frogs")!!.items.groupingBy { it.region!! }.eachCount())
    }

    @Test
    fun sidequests() = runTest {
        val side = load("sidequests")
        assertEquals(mapOf("fishing" to 8, "trading" to 5, "minigames" to 6, "beedle" to 4), side.sizes())
        assertEquals(6, side.items.count { "Fish" in it.tags })
        // The trading quest is a chain.
        side.section("trading")!!.items.zipWithNext().forEach { (before, step) -> assertEquals(listOf(before.id), step.requires, step.id) }
    }

    /** Nothing in Phantom Hourglass can be missed for good (docs/ph.md). */
    @Test
    fun nothingIsMissable() = runTest {
        FILES.forEach { f -> assertEquals(emptyList(), load(f).items.filter { it.missable }.map { it.id }, f) }
    }

    @Test
    fun everyItemSaysHow() = runTest {
        FILES.forEach { f ->
            val c = load(f)
            c.items.filter { it.tiers.isEmpty() }.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.id} in $f") }
            c.items.flatMap { it.tiers }.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.name} in $f") }
        }
    }

    private companion object {
        val FILES = listOf("main_quest", "items", "heart_containers", "spirit_gems", "treasure_maps", "ship_parts", "sea_chart", "sidequests")
        val SEAS = listOf("Southwestern Sea", "Northwestern Sea", "Southeastern Sea", "Northeastern Sea")
    }
    /**
     * Story links (docs/ph.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand and lists what's had on Mercay from the start.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensIt() = runTest {
        val files = listOf("items", "heart_containers", "spirit_gems", "treasure_maps", "ship_parts", "sea_chart", "sidequests")
        val links = files.flatMap { load(it).items }.associate { it.id to it.unlockedBy }
        assertEquals(setOf("wooden_shield", "power_mercay_shop", "island_mercay"), links.filterValues { it.isEmpty() }.keys)
        assertEquals(listOf("molida_fog_route"), links["island_bannan"], "the Northwestern Sea's storm needs the fog route")
        assertEquals(listOf("goron_temple"), links["courage_ember_islet"], "the Grappling Hook, from the Temple of Ice")
        assertEquals(listOf("ghost_ship"), links["wisdom_totok_b9"], "B7–B9 open after the Ghost Ship")
        assertEquals(listOf("buy_salvage_arm"), links["sand_map_2"], "salvaged")
        assertEquals(listOf("totok_1f"), links["golden_prow"], "random, once Link has a ship")
    }
}
