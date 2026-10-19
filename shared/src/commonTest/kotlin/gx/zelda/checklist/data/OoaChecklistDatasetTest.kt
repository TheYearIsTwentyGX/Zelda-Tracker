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
 * Oracle of Ages' checklists (`files/ooa/`), against the counts in docs/ooa.md. The format itself is
 * checked for every game by [ChecklistDatasetsTest].
 */
class OoaChecklistDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("ooa/$name.json", strict = true)

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.ids(section: String): Set<String> = section(section)!!.items.map { it.id }.toSet()

    @Test
    fun ooaListsEveryChecklistAfterItsOverview() {
        val trackers = Games.OOA.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(
            listOf(
                "main_quest", "items", "harp_songs", "heart_pieces", "rings", "gasha",
                "trading", "animal_companions", "linked_secrets",
            ),
            trackers.drop(1).map { it.dataFile!!.removePrefix("ooa/").removeSuffix(".json") },
        )
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.OOA.saveFiles.map { it.id })
    }

    @Test
    fun eightEssencesAndVeranNeedsEveryDungeon() = runTest {
        val quest = load("main_quest")
        // The eight dungeons and rescuing Nayru, which is worth no Essence.
        assertEquals(mapOf("prologue" to 2, "dungeons" to 9, "finale" to 3), quest.sizes())
        assertEquals(quest.ids("dungeons") - "rescue_nayru", quest.item("black_tower")!!.requires.toSet())
        assertEquals("Essences of Time 0 / 8", quest.evaluate(quest.summaries.single(), ChecklistProgress()).text)
        assertEquals(listOf("twinrova", "ganon"), quest.section("finale")!!.items.filter { "Linked" in it.tags }.map { it.id })
    }

    @Test
    fun itemsUpgradesAndQuestItems() = runTest {
        val items = load("items")
        assertEquals(mapOf("items" to 15, "upgrades" to 7, "key_items" to 23), items.sizes())
        // Sword and shield 3 each, then two steps for the bracelet, flippers, hook, satchel and bombs.
        assertEquals(16, items.section("upgrades")!!.items.sumOf { it.steps })
        assertEquals(listOf("Seeds of each 20 / 99", "Bombs 10 / 50"), items.summaries.map { items.evaluate(it, ChecklistProgress()).text })
        assertTrue(listOf("ember_seeds", "scent_seeds", "pegasus_seeds", "gale_seeds", "mystery_seeds").all { items.item(it) != null })
        assertTrue("Linked" in items.item("biggorons_sword")!!.tags)
    }

    @Test
    fun theHarpAndItsThreeTunes() = runTest {
        val harp = load("harp_songs")
        assertEquals(mapOf("tunes" to 4), harp.sizes())
        assertEquals("Tunes 0 / 3", harp.evaluate(harp.summaries.single(), ChecklistProgress()).text)
    }

    @Test
    fun twelvePiecesNineContainersAndFifteenHearts() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(mapOf("pieces" to 12, "containers" to 9), hearts.sizes())
        assertTrue(hearts.items.all { it.region != null }, "every heart has a region")
        assertEquals("Hearts 3 / 15", hearts.evaluate(hearts.summaries.single(), ChecklistProgress()).text)
        assertEquals(listOf("linked_secret_heart_container"), hearts.items.filter { "Linked" in it.tags }.map { it.id })
    }

    @Test
    fun sixtyFourRingsAndTheRingBox() = runTest {
        val rings = load("rings")
        assertEquals(mapOf("ring_boxes" to 1, "rings" to 64), rings.sizes())
        assertEquals(3, rings.item("ring_box")!!.steps)
        assertEquals("Rings 0 / 64", rings.evaluate(rings.summaries.single(), ChecklistProgress()).text)
        // In ring-list order, the same ids as Seasons' list.
        val ids = rings.section("rings")!!.items.map { it.id }
        assertEquals(listOf("friendship_ring", "power_ring_l1"), ids.take(2))
        assertEquals("protection_ring", ids.last())
        assertEquals(10, rings.items.count { "Not in Ages" in it.tags })
    }

    /** One ring collection across both games, so the two lists share their ids, in ring-number order. */
    @Test
    fun ringsAndSecretsMatchSeasons() = runTest {
        val seasons = ChecklistRepository.load("oos/rings.json", strict = true)
        assertEquals(seasons.items.map { it.id }, load("rings").items.map { it.id })
        // A secret told in one game is heard in the other: Ages' "tell" is Seasons' "give" and back.
        val seasonsSecrets = ChecklistRepository.load("oos/linked_secrets.json", strict = true)
        val secrets = load("linked_secrets")
        assertEquals(seasonsSecrets.ids("give"), secrets.ids("tell"))
        assertEquals(seasonsSecrets.ids("tell"), secrets.ids("give"))
    }

    @Test
    fun gashaSpotsTradesCompanionsAndSecrets() = runTest {
        assertEquals(mapOf("spots" to 16), load("gasha").sizes())

        val trading = load("trading")
        assertEquals(mapOf("trade" to 13), trading.sizes())
        // Each trade needs the one before it, from the Poe Clock to the Noble Sword.
        trading.items.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }

        val companions = load("animal_companions")
        assertEquals(mapOf("companions" to 6), companions.sizes())
        assertEquals(setOf("ricky", "dimitri", "moosh"), companions.item("companion_flute")!!.requires.toSet())

        val secrets = load("linked_secrets")
        assertEquals(mapOf("linking" to 4, "tell" to 10, "give" to 10), secrets.sizes())
        assertTrue((secrets.section("tell")!!.items + secrets.section("give")!!.items).all { it.steps == 2 })
    }
}
