package gx.zelda.oot

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
 * Ocarina of Time's checklists (N64), against the counts in docs/oot.md. `ChecklistDatasetsTest`
 * already checks every file's format; this checks OoT's own numbers and cross-references.
 */
class OotDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("oot/$name.json", strict = true)

    private fun ChecklistCatalog.sectionSize(id: String): Int = section(id)!!.items.size

    private fun ChecklistCatalog.summary(label: String, progress: ChecklistProgress = ChecklistProgress()) =
        evaluate((summaries + sections.mapNotNull { it.summary }).single { it.label == label }, progress)

    @Test
    fun theOverviewComesFirstAndEveryChecklistIsListed() {
        val trackers = Games.OOT.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(
            listOf("main_quest", "items", "heart_pieces", "gold_skulltulas", "songs", "great_fairies", "bottles", "big_poes", "trading"),
            trackers.mapNotNull { it.dataFile?.removePrefix("oot/")?.removeSuffix(".json") },
        )
    }

    /** Three files on the cartridge's SRAM (zeldaret/oot `gSramSlotOffsets`: three slots plus three backups). */
    @Test
    fun threeSaveFiles() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.OOT.saveFiles.map { it.id })
    }

    @Test
    fun mainQuestHasThreeStonesAndSixMedallions() = runTest {
        val quest = load("main_quest")
        assertEquals(3.0, quest.summary("Spiritual Stones").max)
        assertEquals(6.0, quest.summary("Medallions").max)
        assertEquals(0.0, quest.summary("Medallions").value)
        // A boss's Heart Container is the reward of each of the eight boss dungeons.
        assertEquals(8, quest.items.count { it.reward?.contains("Heart Container") == true })
    }

    @Test
    fun heartPiecesAndContainersMakeTwentyHearts() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(36, hearts.sectionSize("pieces"))
        assertEquals(8, hearts.sectionSize("containers"))
        val total = hearts.summary("Hearts")
        assertEquals(3.0, total.value)
        assertEquals(20.0, total.max)
    }

    /** Every boss with a Heart Container is a boss named in the Main Quest. */
    @Test
    fun containersMatchTheMainQuestBosses() = runTest {
        val quest = load("main_quest")
        val bosses = quest.items.filter { it.reward?.contains("Heart Container") == true }.map { it.notes.orEmpty() }
        load("heart_pieces").section("containers")!!.items.forEach { container ->
            assertTrue(bosses.any { "Boss: ${container.name}." in it }, "${container.name} isn't a Main Quest boss")
        }
    }

    @Test
    fun aHundredGoldSkulltulasAndSixRewards() = runTest {
        val skulltulas = load("gold_skulltulas")
        val tokens = skulltulas.section("skulltulas")!!.items
        assertEquals(100, tokens.size)
        tokens.forEach { assertTrue(it.region != null, "${it.id} has no area") }
        // Every token says which age can get it.
        tokens.forEach { t -> assertEquals(1, t.tags.count { it in AGES }, "${t.id}: ${t.tags}") }
        assertEquals(6, skulltulas.sectionSize("rewards"))
    }

    @Test
    fun twelveSongsSixFairiesFourBottlesTenBigPoes() = runTest {
        val songs = load("songs")
        assertEquals(6, songs.sectionSize("songs"))
        assertEquals(6, songs.sectionSize("warp_songs"))
        assertEquals(6, load("great_fairies").items.size)
        assertEquals(4, load("bottles").items.size)
        assertEquals(10, load("big_poes").items.size)
    }

    /** Each trade needs the one before it, from the Weird Egg to the Mask of Truth and the Pocket Egg to Biggoron's Sword. */
    @Test
    fun tradingSequencesAreChains() = runTest {
        val trading = load("trading")
        assertEquals(8, trading.sectionSize("child"))
        assertEquals(12, trading.sectionSize("adult"))
        trading.sections.forEach { section ->
            assertEquals(emptyList(), section.items.first().requires)
            section.items.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }
        }
        assertEquals("mask_of_truth", trading.section("child")!!.items.last().id)
        assertEquals("biggorons_sword", trading.section("adult")!!.items.last().id)
    }

    /**
     * Story links (docs/oot.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand, that nothing adult is open from the start, and how many are.
     */
    @Test
    fun everyItemIsLinkedToTheStepsThatOpenIt() = runTest {
        val files = listOf("items", "heart_pieces", "gold_skulltulas", "songs", "great_fairies", "bottles", "big_poes", "trading")
        val items = files.flatMap { load(it).items }
        val links = items.associate { it.id to it.unlockedBy }
        assertEquals(emptyList(), items.filter { "Adult" in it.tags && it.unlockedBy.isEmpty() }.map { it.id }, "adult Link needs the Master Sword")
        assertEquals(58, items.count { it.unlockedBy.isEmpty() }, "childhood's open places, from the start")
        assertEquals(emptyList(), links["kokiri_sword"])
        assertEquals(listOf("light_medallion"), links["forest_temple_lobby"], "inside a temple: the step before it")
        assertEquals(listOf("ice_cavern"), links["water_temple_river"], "the Water Temple needs the Ice Cavern's Iron Boots")
        assertEquals(listOf("deku_tree", "dodongos_cavern", "jabu_jabu"), links["bunny_hood"], "the Running Man waits for all three Spiritual Stones")
        assertEquals(listOf("forest_temple", "fire_temple", "water_temple", "shadow_temple", "spirit_temple"), links["light_arrows"], "every Sage awake")
        assertEquals(listOf("light_medallion"), links["castle_town_entrance_sign"], "Big Poes are shot from Epona with the Fairy Bow")
    }

    private companion object {
        val AGES = setOf("Child", "Adult", "Either")
    }
}
