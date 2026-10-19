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
 * A Link Between Worlds' checklists against the counts in docs/albw.md, where Zelda Wiki, Zelda
 * Dungeon, IGN and Zelda Universe agree, plus the links between files. Format and in-file
 * references (ids, regions, `requires`, formulas) are checked for every game by [ChecklistDatasetsTest].
 */
class AlbwDatasetTest {
    private fun withCatalogs(block: (Map<String, ChecklistCatalog>) -> Unit) = runTest {
        val files = Games.ALBW.trackers.mapNotNull { it.dataFile }
        block(files.associate { it.removePrefix("albw/").removeSuffix(".json") to ChecklistRepository.load(it, strict = true) })
    }

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.allDone(): ChecklistProgress = ChecklistProgress(items.associate { it.id to it.steps })

    private fun ChecklistCatalog.summary(label: String) = summaries.single { it.label == label }

    private fun ChecklistCatalog.range(label: String): Pair<Double, Double> =
        evaluate(summary(label), ChecklistProgress()).value to evaluate(summary(label), allDone()).value

    @Test
    fun everyTrackerIsAChecklistWithItsOwnFile() {
        val checklists = Games.ALBW.trackers.filter { it.kind == TrackerKind.CHECKLIST }
        assertEquals(
            listOf("main_quest", "items", "maiamais", "heart_pieces", "minigames"),
            checklists.map { it.dataFile!!.removePrefix("albw/").removeSuffix(".json") },
        )
    }

    @Test
    fun filesWithRegionsUseTheTwoWorlds() = withCatalogs { all ->
        all.forEach { (name, c) ->
            if (c.regions.isNotEmpty()) assertEquals(listOf("Hyrule", "Lorule"), c.regions, name)
        }
    }

    @Test
    fun mainQuestHasThreePendantsAndSevenSages() = withCatalogs { all ->
        val c = all.getValue("main_quest")
        assertEquals(mapOf("hyrule" to 10, "lorule" to 11), c.sizes())
        assertEquals(0.0 to 3.0, c.range("Pendants"))
        assertEquals(0.0 to 7.0, c.range("Sages"))
        // Only the steps that give a Pendant or rescue a Sage count, and each is tagged.
        assertEquals(3, c.section("hyrule")!!.items.count { "Pendant" in it.tags && it.value == 1.0 })
        assertEquals(7, c.section("lorule")!!.items.count { "Sage" in it.tags && it.value == 1.0 })
        assertEquals(c.items.filter { it.value > 0 }, c.items.filter { it.tags.isNotEmpty() })
        // The Triforce of Courage needs all seven Sages; Lorule Castle needs it, and the ending is last.
        assertEquals(c.items.filter { "Sage" in it.tags }.map { it.id }.toSet(), c.item("triforce_of_courage")!!.requires.toSet())
        assertEquals(listOf("triforce_of_courage"), c.item("lorule_castle")!!.requires)
        assertEquals("ending", c.items.last().id)
        // The Sand Rod is only in Ravio's stock after the Thieves' Hideout.
        assertEquals(listOf("thieves_hideout"), c.item("desert_palace")!!.requires)
    }

    @Test
    fun heartsAddUpToTwenty() = withCatalogs { all ->
        val c = all.getValue("heart_pieces")
        assertEquals(mapOf("pieces" to 28, "containers" to 10), c.sizes())
        assertEquals(mapOf("Hyrule" to 18, "Lorule" to 10), c.section("pieces")!!.items.groupingBy { it.region!! }.eachCount())
        assertEquals(3.0 to 20.0, c.range("Hearts"))
    }

    /** One container from each dungeon boss whose Main Quest step lists one, and none from the castles. */
    @Test
    fun heartContainersMatchTheMainQuest() = withCatalogs { all ->
        val steps = all.getValue("main_quest").items.filter { "Heart Container" in it.reward.orEmpty() }
        assertEquals(10, steps.size)
        val containers = all.getValue("heart_pieces").section("containers")!!.items
        assertEquals(steps.map { it.id }.toSet(), containers.map { it.id.removePrefix("hc_") }.toSet())
    }

    /** The seven prize games that give a Piece of Heart are the seven Mini-game pieces. */
    @Test
    fun miniGamePiecesMatchTheMiniGames() = withCatalogs { all ->
        val games = all.getValue("minigames")
        assertEquals(mapOf("minigames" to 7), games.sizes())
        assertEquals(13, games.items.sumOf { it.steps })
        assertEquals(listOf(3, 4), listOf("cucco_ranch", "treacherous_tower").map { games.item(it)!!.steps })
        val prizes = games.items.filter { "Piece of Heart" in it.tags }
        val pieces = all.getValue("heart_pieces").items.filter { "Mini-game" in it.tags }
        assertEquals(7, prizes.size)
        assertEquals(prizes.map { "poh_${it.id}" }.toSet(), pieces.map { it.id }.toSet())
    }

    @Test
    fun itemsAndUpgrades() = withCatalogs { all ->
        val c = all.getValue("items")
        assertEquals(mapOf("ravio" to 9, "equipment" to 14, "master_ore" to 4, "bottles" to 5), c.sizes())
        // Every Ravio item is rented, bought, then made Nice by Mother Maiamai; only the last counts as an upgrade.
        c.section("ravio")!!.items.forEach { item ->
            assertEquals(listOf("Rented", "Bought", "Nice ${item.name}"), item.tiers.map { it.name }, item.id)
        }
        assertEquals(0.0 to 9.0, c.range("Nice upgrades"))
        assertEquals(listOf("Captain's Sword", "Master Sword", "Master Sword Lv2", "Master Sword Lv3"), c.item("sword")!!.tiers.map { it.name })
    }

    /** 50 Maiamai in each world, in the Maiamai Map's 9 Hyrule and 7 Lorule areas; every 10 is a reward, the tenth the Great Spin. */
    @Test
    fun aHundredMaiamai() = withCatalogs { all ->
        val c = all.getValue("maiamais")
        assertEquals(mapOf("maiamai" to 100), c.sizes())
        assertEquals(mapOf("Hyrule" to 50, "Lorule" to 50), c.items.groupingBy { it.region!! }.eachCount())
        assertEquals(mapOf("Hyrule" to 9, "Lorule" to 7), c.items.groupBy { it.region!! }.mapValues { (_, items) -> items.map { it.area }.toSet().size })
        assertEquals(0.0 to 10.0, c.range("Rewards earned"))
    }

    /** Each dungeon's "Treasure: X" in the Main Quest is an item, tier or reward in Items & Equipment. */
    @Test
    fun dungeonTreasuresAreItems() = withCatalogs { all ->
        val items = all.getValue("items").items
        val names = items.flatMap { listOf(it.name, it.reward) + it.tiers.map { t -> t.name } }.filterNotNull().toSet()
        val treasures = all.getValue("main_quest").items.mapNotNull { Regex("""Treasure: ([^.]+)\.""").find(it.notes.orEmpty())?.groupValues?.get(1) }
        assertEquals(8, treasures.size)
        treasures.forEach { assertTrue(it in names, "$it isn't in Items & Equipment") }
    }

    /** No source flags anything in ALBW as missable (docs/albw.md). */
    @Test
    fun nothingIsMissable() = withCatalogs { all ->
        all.forEach { (name, c) -> assertEquals(emptyList(), c.items.filter { it.missable }.map { it.id }, name) }
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
