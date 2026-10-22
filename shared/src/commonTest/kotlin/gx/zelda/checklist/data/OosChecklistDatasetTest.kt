package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Oracle of Seasons' checklists (`files/oos/`), against the counts in docs/oos.md. The format itself
 * is checked for every game by [ChecklistDatasetsTest].
 */
class OosChecklistDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("oos/$name.json", strict = true)

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.ids(section: String): Set<String> = section(section)!!.items.map { it.id }.toSet()

    @Test
    fun oosListsEveryChecklistAfterItsOverview() {
        val trackers = Games.OOS.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(
            listOf(
                "main_quest", "items", "seasons", "heart_pieces", "rings", "gasha",
                "trading", "animal_companions", "linked_secrets", "subrosia",
            ),
            trackers.drop(1).map { it.dataFile!!.removePrefix("oos/").removeSuffix(".json") },
        )
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.OOS.saveFiles.map { it.id })
    }

    @Test
    fun eightEssencesAndOnoxNeedsEveryDungeon() = runTest {
        val quest = load("main_quest")
        assertEquals(mapOf("prologue" to 2, "dungeons" to 8, "finale" to 3), quest.sizes())
        assertEquals(quest.ids("dungeons"), quest.item("onoxs_castle")!!.requires.toSet())
        assertEquals("Essences of Nature 0 / 8", quest.evaluate(quest.summaries.single(), ChecklistProgress()).text)
        // Twinrova and Ganon only appear in a linked game.
        assertEquals(listOf("twinrova", "ganon"), quest.section("finale")!!.items.filter { "Linked" in it.tags }.map { it.id })
    }

    @Test
    fun itemsUpgradesAndQuestItems() = runTest {
        val items = load("items")
        assertEquals(mapOf("items" to 15, "upgrades" to 7, "key_items" to 21), items.sizes())
        // Sword and shield 3 each, then two steps for the feather, boomerang, slingshot, satchel and bombs.
        assertEquals(16, items.section("upgrades")!!.items.sumOf { it.steps })
        // Capacities from the upgrades' values: seeds start at 20, bombs at 10.
        assertEquals(listOf("Seeds of each 20 / 99", "Bombs 10 / 50"), items.summaries.map { items.evaluate(it, ChecklistProgress()).text })
        assertTrue(listOf("ember_seeds", "scent_seeds", "pegasus_seeds", "gale_seeds", "mystery_seeds").all { items.item(it) != null })
        assertTrue("Linked" in items.item("biggorons_sword")!!.tags)
    }

    @Test
    fun theRodAndItsFourSeasons() = runTest {
        val seasons = load("seasons")
        assertEquals(mapOf("seasons" to 5), seasons.sizes())
        assertEquals("Seasons 0 / 4", seasons.evaluate(seasons.summaries.single(), ChecklistProgress()).text)
    }

    @Test
    fun twelvePiecesNineContainersAndFifteenHearts() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(mapOf("pieces" to 12, "containers" to 9), hearts.sizes())
        assertTrue(hearts.items.all { it.region != null }, "every heart has a region")
        // 3 to start, 3 from the pieces, 8 bosses and the linked secret's container.
        assertEquals("Hearts 3 / 15", hearts.evaluate(hearts.summaries.single(), ChecklistProgress()).text)
        assertEquals(listOf("linked_secret_heart_container"), hearts.items.filter { "Linked" in it.tags }.map { it.id })
    }

    @Test
    fun sixtyFourRingsAndTheRingBox() = runTest {
        val rings = load("rings")
        assertEquals(mapOf("ring_boxes" to 1, "rings" to 64), rings.sizes())
        assertEquals(3, rings.item("ring_box")!!.steps)
        assertEquals("Rings 0 / 64", rings.evaluate(rings.summaries.single(), ChecklistProgress()).text)
        // The Ages-only rings say so rather than being left out.
        assertEquals(10, rings.items.count { "Not in Seasons" in it.tags })
    }

    @Test
    fun gashaSpotsTradesCompanionsSecretsAndSubrosia() = runTest {
        assertEquals(mapOf("spots" to 16), load("gasha").sizes())

        val trading = load("trading")
        assertEquals(mapOf("trade" to 13), trading.sizes())
        // Each trade needs the one before it, from the Cuccodex to the Noble Sword.
        trading.items.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }

        assertEquals(mapOf("companions" to 6), load("animal_companions").sizes())

        val secrets = load("linked_secrets")
        assertEquals(mapOf("linking" to 4, "tell" to 10, "give" to 10), secrets.sizes())
        // Each secret is its reward in one game, then the return secret in the other.
        assertTrue((secrets.section("tell")!!.items + secrets.section("give")!!.items).all { it.steps == 2 })

        assertEquals(mapOf("visits" to 7, "market" to 6, "ores" to 6, "pastimes" to 4), load("subrosia").sizes())
    }
    /**
     * Story links (docs/oos.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand and counts what's had from the start.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensIt() = runTest {
        val files = listOf("items", "seasons", "heart_pieces", "rings", "gasha", "trading", "animal_companions", "linked_secrets", "subrosia")
        val items = files.flatMap { load(it).items }
        val links = items.associate { it.id to it.unlockedBy }
        assertEquals(42, items.count { it.unlockedBy.isEmpty() }, "random rings, Ages-side secrets and Horon's own")
        assertEquals(listOf("snakes_remains"), links["roc_feather"], "Poison Moth's Lair's item, by the step before it")
        assertEquals(listOf("gnarled_root_dungeon"), links["eastern_suburbs_winter_cave"], "winter, from the Rod after the first dungeon")
        assertEquals(listOf("poison_moths_lair"), links["x_shaped_jewel"], "an Ember Seed from the Slingshot")
        assertEquals(emptyList(), links["deku_secret"], "heard in the linked Ages game")
    }
}
