package gx.zelda.la

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistCount
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Link's Awakening's checklists (Game Boy and DX, with the Switch remake's lists as separate tabs),
 * against the counts in docs/la.md. `ChecklistDatasetsTest` already checks every file's format; this
 * checks LA's own numbers and cross-references.
 */
class LaDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("la/$name.json", strict = true)

    private fun ChecklistCatalog.sectionSize(id: String): Int = section(id)!!.items.size

    private fun ChecklistCatalog.summary(label: String, progress: ChecklistProgress = ChecklistProgress()) =
        evaluate((summaries + sections.mapNotNull { it.summary }).single { it.label == label }, progress)

    private fun ChecklistCatalog.all(): ChecklistProgress = items.fold(ChecklistProgress()) { p, i -> p.withLevel(i, i.steps) }

    @Test
    fun theOverviewComesFirstAndEveryChecklistIsListed() {
        val trackers = Games.LA.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(
            listOf("main_quest", "items", "heart_pieces", "seashells", "trading", "songs", "photographs", "color_dungeon"),
            trackers.mapNotNull { it.dataFile?.removePrefix("la/")?.removeSuffix(".json") },
        )
    }

    /** Three files in every version (Zelda Wiki, Saving; the DX disassembly's SaveGame1–3). */
    @Test
    fun threeSaveFiles() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.LA.saveFiles.map { it.id })
    }

    /** Ids are saved progress: unique across every LA file too, so a search or a note can't confuse two. */
    @Test
    fun idsAreUniqueAcrossFiles() = runTest {
        val ids = FILES.flatMap { f -> load(f).let { c -> c.items.map { it.id } + c.sections.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }

    @Test
    fun eightDungeonsGiveEightInstruments() = runTest {
        val quest = load("main_quest")
        assertEquals(8.0, quest.summary("Instruments of the Sirens").max)
        assertEquals(0.0, quest.summary("Instruments of the Sirens").value)
        assertEquals(8, quest.items.count { it.reward?.contains("Heart Container") == true })
        assertEquals(INSTRUMENTS, quest.items.mapNotNull { i -> INSTRUMENTS.firstOrNull { i.reward?.startsWith(it) == true } })
        assertEquals("wind_fish", quest.items.last().id)
        // The quest is one chain: every step after the first needs one before it.
        quest.items.drop(1).forEach { assertTrue(it.requires.isNotEmpty(), "${it.id} needs nothing") }
    }

    @Test
    fun heartPiecesMakeFourteenHeartsAndTwentyOnSwitch() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(12, hearts.sectionSize("pieces"))
        assertEquals(8, hearts.sectionSize("containers"))
        assertEquals(32, hearts.sectionSize("switch_pieces"))
        assertEquals(1, hearts.sectionSize("switch_containers"))
        assertEquals(3.0, hearts.summary("Hearts").value)
        assertEquals(14.0, hearts.summary("Hearts").max)
        assertEquals(20.0, hearts.summary("Hearts on Switch").max)
        // The Switch lists are counted apart: everything else done is the whole total.
        val switchOnly = hearts.sections.filter { it.separate }.map { it.id }
        assertEquals(listOf("switch_pieces", "switch_containers"), switchOnly)
        assertEquals(ChecklistCount(20, 20), hearts.count(hearts.all()))
    }

    /** Every Heart Container is a boss named in the Main Quest. */
    @Test
    fun containersMatchTheMainQuestBosses() = runTest {
        val bosses = load("main_quest").items.filter { it.reward?.contains("Heart Container") == true }.map { it.notes.orEmpty() }
        load("heart_pieces").section("containers")!!.items.forEach { container ->
            assertTrue(bosses.any { "Boss: ${container.name}." in it }, "${container.name} isn't a Main Quest boss")
        }
    }

    /** The remake keeps all 12 original pieces (its own copy of each, under the same id with `switch_`). */
    @Test
    fun theSwitchListsKeepTheOriginalSpots() = runTest {
        val hearts = load("heart_pieces")
        val switchHearts = hearts.section("switch_pieces")!!.items.map { it.id }.toSet()
        hearts.section("pieces")!!.items.forEach { assertTrue("switch_${it.id}" in switchHearts, it.id) }

        val shells = load("seashells")
        val switchShells = shells.section("switch_seashells")!!.items.map { it.id }.toSet()
        val kept = shells.section("seashells")!!.items.filter { "switch_${it.id}" in switchShells }
        // All but the two Mansion bonus shells, which the remake dropped.
        assertEquals(24, kept.size)
        assertEquals(setOf("mansion_bonus_five", "mansion_bonus_ten"), shells.section("seashells")!!.items.map { it.id }.toSet() - kept.map { it.id }.toSet())
    }

    @Test
    fun twentySixSeashellsAndFiftyOnSwitch() = runTest {
        val shells = load("seashells")
        assertEquals(26, shells.sectionSize("seashells"))
        assertEquals(1, shells.sectionSize("mansion"))
        assertEquals(50, shells.sectionSize("switch_seashells"))
        assertEquals(5, shells.sectionSize("switch_mansion"))
        assertEquals(ChecklistCount(27, 27), shells.count(shells.all()))
        // Only the original's exact-count bonuses can be missed.
        assertEquals(listOf("mansion_bonus_five", "mansion_bonus_ten"), shells.items.filter { it.missable }.map { it.id })
        // The Switch Mansion's rewards come in order.
        shells.section("switch_mansion")!!.items.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires) }
    }

    @Test
    fun itemsUpgradesAndGoldenLeaves() = runTest {
        val items = load("items")
        assertEquals(15, items.sectionSize("equipment"))
        assertEquals(3, items.sectionSize("upgrades"))
        assertEquals(5, items.sectionSize("golden_leaves"))
        assertEquals(3, items.sectionSize("switch_bottles"))
        val tiered = items.items.filter { it.tiers.isNotEmpty() }.associate { it.id to it.tiers.size }
        assertEquals(mapOf("sword" to 2, "shield" to 2, "power_bracelet" to 2), tiered)
        // Every dungeon item is in the list.
        DUNGEON_ITEMS.forEach { name -> assertTrue(items.items.any { i -> i.name == name || i.tiers.any { it.name == name } }, name) }
    }

    @Test
    fun threeSongsTwelvePhotosAndTheColorDungeon() = runTest {
        assertEquals(3, load("songs").items.size)
        val photos = load("photographs")
        assertEquals(12, photos.items.size)
        assertEquals(listOf("marin_cliff", "heads_up", "say_mushroom", "richard_gate"), photos.items.filter { it.missable }.map { it.id })
        assertEquals("color_tunic", load("color_dungeon").items.last().id)
    }

    /** Each trade needs the one before it, from the Yoshi Doll to the Magnifying Lens. */
    @Test
    fun theTradingSequenceIsAChainOfFourteen() = runTest {
        val trades = load("trading").items
        assertEquals(14, trades.size)
        assertEquals(emptyList(), trades.first().requires)
        trades.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }
        assertEquals("yoshi_doll", trades.first().id)
        assertEquals("magnifying_lens", trades.last().id)
    }

    private companion object {
        val FILES = listOf("main_quest", "items", "heart_pieces", "seashells", "trading", "songs", "photographs", "color_dungeon")
        val INSTRUMENTS = listOf(
            "Full Moon Cello", "Conch Horn", "Sea Lily's Bell", "Surf Harp",
            "Wind Marimba", "Coral Triangle", "Organ of Evening Calm", "Thunder Drum",
        )
        val DUNGEON_ITEMS = listOf(
            "Roc's Feather", "Power Bracelet", "Pegasus Boots", "Flippers", "Hookshot", "Power Bracelet L-2", "Mirror Shield", "Magic Rod",
        )
    }
    /**
     * Story links (docs/la.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand and counts what Mabe Village has from the start.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensIt() = runTest {
        val files = listOf("items", "heart_pieces", "seashells", "songs", "trading", "photographs", "color_dungeon")
        val items = files.flatMap { load(it).items }
        val links = items.associate { it.id to it.unlockedBy }
        assertEquals(23, items.count { it.unlockedBy.isEmpty() }, "Mabe Village and Toronbo Shores, from the start")
        assertEquals(emptyList(), links["shovel"])
        assertEquals(listOf("angler_key"), links["flippers"], "Angler's Tunnel's item, by the step that opens it")
        assertEquals(listOf("anglers_tunnel"), links["boomerang"], "the Magnifying Lens, at the end of the trading sequence")
        assertEquals(listOf("dream_shrine"), links["heads_up"], "Marin follows Link only after the Dream Shrine")
    }
}
