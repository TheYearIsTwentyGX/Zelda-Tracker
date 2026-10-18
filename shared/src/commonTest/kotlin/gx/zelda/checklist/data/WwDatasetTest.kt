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
 * The Wind Waker's checklists (GameCube) against the counts in docs/ww.md, plus the links between
 * files. Format and in-file references (ids, regions, `requires`, formulas) are checked for every game
 * by [ChecklistDatasetsTest].
 */
class WwDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.WW.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("ww/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.range(label: String): Pair<Double, Double> {
        val summary = summaries.single { it.label == label }
        return evaluate(summary, ChecklistProgress()).value to evaluate(summary, allDone()).value
    }

    /** "A1 Forsaken Fortress" … "G7 Five-Star Isles", from the Sea Chart file. */
    private fun Map<String, ChecklistCatalog>.islands(): List<String> =
        getValue("sea_chart").items.map { it.name.substringAfter(' ') }

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        assertEquals(TrackerKind.OVERVIEW, Games.WW.trackers.first().kind)
        val checklists = Games.WW.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(
            listOf("main_quest", "items", "heart_pieces", "charts", "sea_chart", "sea_secrets", "figurines", "songs", "great_fairies", "side_quests"),
            checklists.map { it.dataFile!!.removePrefix("ww/").removeSuffix(".json") },
        )
    }

    /** Three quest logs on the memory card (zeldaret/tww: `card_gamedata gamedata[3]`). */
    @Test
    fun threeSaveFiles() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.WW.saveFiles.map { it.id })
    }

    @Test
    fun theSeaChartHas49SquaresInSevenRows() = withCatalogs { all ->
        val c = all.getValue("sea_chart")
        assertEquals(49, c.items.size)
        assertEquals(('A'..'G').map { "Row $it" }, c.regions)
        c.regions.forEach { row -> assertEquals(7, c.items.count { it.region == row }, row) }
        val codes = ('A'..'G').flatMap { r -> (1..7).map { "$r$it" } }
        assertEquals(codes, c.items.map { it.name.substringBefore(' ') })
        assertEquals(49, all.islands().toSet().size)
    }

    /** Files with geography use the islands, in Sea Chart order (A1 to G7). */
    @Test
    fun regionsAreIslandsInSeaChartOrder() = withCatalogs { all ->
        val islands = all.islands()
        val roomed = setOf("sea_chart", "figurines")
        all.filterKeys { it !in roomed }.forEach { (name, c) ->
            assertTrue(islands.containsAll(c.regions), "$name: ${c.regions - islands.toSet()}")
            assertEquals(c.regions.sortedBy(islands::indexOf), c.regions, name)
        }
    }

    @Test
    fun mainQuestHasThreePearlsAndEightShards() = withCatalogs { all ->
        val c = all.getValue("main_quest")
        assertEquals(mapOf("story" to 20, "triforce_shards" to 8), c.sizes())
        assertEquals(0.0 to 3.0, c.range("Pearls"))
        assertEquals(3, c.items.count { "Pearl" in it.tags })
        assertEquals((1..8).map { "triforce_shard_$it" }.toSet() + "master_sword_restored", c.item("triforce_of_courage")!!.requires.toSet())
        assertEquals("ganondorf", c.section("story")!!.items.last().id)
    }

    @Test
    fun heartsAddUpToTwenty() = withCatalogs { all ->
        val c = all.getValue("heart_pieces")
        assertEquals(mapOf("pieces" to 44, "containers" to 6), c.sizes())
        assertEquals(3.0 to 20.0, c.range("Hearts"))
        assertTrue(c.items.none { it.missable }, "nothing is missable on the GameCube")
    }

    /** A Heart Container for each of the six dungeons whose Main Quest step gives one. */
    @Test
    fun heartContainersMatchTheMainQuest() = withCatalogs { all ->
        val steps = all.getValue("main_quest").items.filter { it.reward == "Heart Container" }
        assertEquals(6, steps.size)
        val containers = all.getValue("heart_pieces").section("containers")!!.items
        assertEquals(steps.map { it.region }, containers.map { it.region })
        steps.zip(containers).forEach { (step, container) ->
            assertTrue(step.notes!!.startsWith("Boss: ${container.name.substringBefore(" (")}."), step.id)
        }
    }

    /** Each dungeon's "Treasure: X." in the Main Quest is an item, or an item's tier, in Items & Equipment. */
    @Test
    fun dungeonTreasuresAreItems() = withCatalogs { all ->
        val names = all.getValue("items").items.flatMap { listOf(it.name) + it.tiers.map { t -> t.name } }.toSet()
        val treasures = all.getValue("main_quest").items.mapNotNull { Regex("""Treasure: ([^.]+)\.""").find(it.notes.orEmpty())?.groupValues?.get(1) }
        assertEquals(6, treasures.size)
        treasures.forEach { assertTrue(it in names, "$it isn't in Items & Equipment") }
    }

    @Test
    fun itemsAndUpgrades() = withCatalogs { all ->
        val c = all.getValue("items")
        assertEquals(mapOf("equipment" to 5, "items" to 17, "bottles" to 4, "upgrades" to 5), c.sizes())
        assertEquals(200.0 to 5000.0, c.range("Wallet"))
        assertEquals(30.0 to 99.0, c.range("Bombs"))
        assertEquals(30.0 to 99.0, c.range("Arrows"))
        assertEquals(listOf(4, 2, 2, 3), listOf("sword", "shield", "picto_box", "heros_bow").map { c.item(it)!!.steps })
    }

    @Test
    fun chartsNumberedAndLinked() = withCatalogs { all ->
        val c = all.getValue("charts")
        assertEquals(mapOf("treasure_charts" to 41, "triforce_charts" to 8, "special_charts" to 12), c.sizes())
        val treasure = c.section("treasure_charts")!!.items
        assertEquals((1..41).map { "Treasure Chart $it" }, treasure.map { it.name })
        treasure.forEach { assertEquals(listOf(0.0, 1.0), it.tiers.map { t -> t.value ?: it.value }, it.id) }
        assertEquals(0.0 to 41.0, c.range("Treasures salvaged"))
        assertEquals(0.0 to 41.0, c.range("Treasure Charts found"))
        // Six treasures are Special Charts, each one a row of its own.
        val special = c.section("special_charts")!!.items.map { it.name }.toSet()
        val chartTreasures = treasure.mapNotNull { it.reward }.filter { it.endsWith("Chart") }
        assertEquals(6, chartTreasures.size)
        assertTrue(special.containsAll(chartTreasures), "$chartTreasures")
        // Triforce Chart n leads to Triforce Shard n.
        val shards = all.getValue("main_quest").section("triforce_shards")!!.items
        c.section("triforce_charts")!!.items.forEachIndexed { i, chart ->
            assertEquals("Triforce Chart ${i + 1}", chart.name)
            assertTrue("Triforce Shard ${i + 1} at ${shards[i].region}" in chart.notes!!, chart.id)
        }
    }

    /** The 11 sunken Pieces of Heart are the 11 Treasure Charts whose treasure is one, at the same island. */
    @Test
    fun sunkenHeartsMatchTheirCharts() = withCatalogs { all ->
        val charts = all.getValue("charts").section("treasure_charts")!!.items.filter { it.reward == "Piece of Heart" }
        val sunken = all.getValue("heart_pieces").items.filter { "Treasure Chart" in it.tags }
        assertEquals(11, charts.size)
        assertEquals(charts.associate { it.name to it.region }, sunken.associate { Regex("Treasure Chart \\d+").find(it.howTo!!)!!.value to it.region })
    }

    @Test
    fun seaSecrets() = withCatalogs { all ->
        val c = all.getValue("sea_secrets")
        assertEquals(mapOf("secret_caves" to 20, "submarines" to 7, "lookouts" to 20, "big_octos" to 6), c.sizes())
        assertEquals(2, c.section("secret_caves")!!.items.count { it.region == "Pawprint Isle" })
        // The pieces they give are in Heart Pieces, on the same island.
        val hearts = all.getValue("heart_pieces").items.mapNotNull { it.region }.toSet()
        c.items.filter { "Piece of Heart" in it.reward.orEmpty() }.forEach { assertTrue(it.region in hearts, it.id) }
    }

    @Test
    fun theNintendoGalleryHas134Figurines() = withCatalogs { all ->
        val c = all.getValue("figurines")
        assertEquals(134, c.items.size)
        assertEquals(
            listOf(
                "Forest Haven Room" to 14, "Dragon Roost Island Room" to 18, "Dungeon Room" to 19, "Forsaken Fortress Room" to 18,
                "Outset Island Room" to 14, "Windfall Island Room" to 27, "Ocean Room" to 24,
            ),
            c.regions.map { r -> r to c.items.count { it.region == r } },
        )
        // Zelda Wiki: 16 can be missed for good, Knuckle's and the completion figurine among them.
        assertEquals(16, c.items.count { it.missable })
        assertTrue(c.item("knuckle")!!.missable && c.item("link_and_the_king_of_red_lions")!!.missable)
    }

    @Test
    fun songsFairiesAndSideQuests() = withCatalogs { all ->
        assertEquals(6, all.getValue("songs").items.size)
        val fairies = all.getValue("great_fairies").items
        assertEquals(8, fairies.size)
        assertEquals(7, fairies.count { it.name.startsWith("Great Fairy") })
        val side = all.getValue("side_quests")
        assertEquals(mapOf("quests" to 8, "withered_trees" to 8, "tingle_statues" to 6), side.sizes())
        assertEquals(side.section("tingle_statues")!!.items.dropLast(1).map { it.id }, side.item("tingle_statues_reward")!!.requires)
    }

    @Test
    fun everyItemSaysWhereOrHow() = withCatalogs { all ->
        all.forEach { (name, c) ->
            val vague = c.items.filter { it.howTo.isNullOrBlank() && it.tiers.any { t -> t.howTo.isNullOrBlank() } }.map(ChecklistItem::id)
            assertEquals(emptyList(), vague, name)
            c.items.filter { it.tiers.isEmpty() }.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.id} in $name") }
        }
    }
}
