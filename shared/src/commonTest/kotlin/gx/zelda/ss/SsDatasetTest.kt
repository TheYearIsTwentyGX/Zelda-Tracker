package gx.zelda.ss

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
 * Skyward Sword's checklists (the Wii original, with HD's differences in notes), against the counts in
 * docs/ss.md. `ChecklistDatasetsTest` already checks every file's format; this checks SS's own numbers
 * and the links between its files.
 */
class SsDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("ss/$name.json", strict = true)

    private fun ChecklistCatalog.sectionSize(id: String): Int = section(id)!!.items.size

    private fun ChecklistCatalog.steps(id: String): Int = section(id)!!.items.sumOf { it.steps }

    private fun ChecklistCatalog.summary(label: String, progress: ChecklistProgress = ChecklistProgress()) =
        evaluate((summaries + sections.mapNotNull { it.summary }).single { it.label == label }, progress)

    private fun ChecklistCatalog.all(): ChecklistProgress = items.fold(ChecklistProgress()) { p, i -> p.withLevel(i, i.steps) }

    private fun ChecklistCatalog.idsTagged(tag: String): List<String> = items.filter { tag in it.tags }.map { it.id }

    @Test
    fun theOverviewComesFirstAndEveryChecklistIsListed() {
        val trackers = Games.SS.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(FILES, trackers.mapNotNull { it.dataFile?.removePrefix("ss/")?.removeSuffix(".json") })
    }

    /** Three on the Wii (the decompilation's `saveFiles[3]`) and in HD, whose autosave slot isn't a fourth file. */
    @Test
    fun threeSaveFiles() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.SS.saveFiles.map { it.id })
    }

    /** Ids are saved progress: unique across every SS file too, so a search or a note can't confuse two. */
    @Test
    fun idsAreUniqueAcrossFiles() = runTest {
        val ids = FILES.flatMap { f -> load(f).let { c -> c.items.map { it.id } + c.sections.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }

    @Test
    fun sevenDungeonsThreeFlamesFourTrialsAndTheSong() = runTest {
        val quest = load("main_quest")
        assertEquals(7.0, quest.summary("Dungeons").max)
        assertEquals(0.0, quest.summary("Dungeons").value)
        assertEquals(DUNGEONS, quest.section("quest")!!.items.filter { it.value == 1.0 }.map { it.id })
        assertEquals(3.0, quest.summary("Sacred Flames").max)
        assertEquals(4.0, quest.summary("Silent Realms").max)
        assertEquals(4, quest.sectionSize("hero_song"))
        assertEquals("demise", quest.section("quest")!!.items.last().id)
        // The story is one chain: every step after the first needs the one before it.
        quest.section("quest")!!.items.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }
        // Levias sings the last part, once the three dragons have sung theirs.
        assertEquals(listOf("hero_song_faron", "hero_song_eldin", "hero_song_lanayru"), quest.section("hero_song")!!.items.last().requires)

        val sword = quest.items.single { it.id == "goddess_sword" }
        assertEquals(listOf("Goddess Sword", "Goddess Longsword", "Goddess White Sword", "Master Sword", "True Master Sword"), sword.tiers.map { it.name })

        val trials = quest.section("silent_realms")!!.items.map { it.reward }
        assertEquals(listOf("Water Dragon's Scale", "Clawshots", "Fireshield Earrings", "Stone of Trials"), trials)
    }

    @Test
    fun heartPiecesMakeEighteenHearts() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(24, hearts.sectionSize("pieces"))
        assertEquals(6, hearts.sectionSize("containers"))
        assertEquals(6.0, hearts.summary("Hearts").value)
        assertEquals(18.0, hearts.summary("Hearts").max)
        assertEquals(ChecklistCount(30, 30), hearts.count(hearts.all()))
    }

    /** Every Heart Container is a dungeon boss named in the Main Quest's notes; Sky Keep has none. */
    @Test
    fun containersMatchTheMainQuestBosses() = runTest {
        val quest = load("main_quest")
        val containers = load("heart_pieces").section("containers")!!.items
        assertEquals(DUNGEONS - "sky_keep", containers.map { c -> quest.items.single { it.name == c.area }.id })
        containers.forEach { c ->
            val boss = c.name.substringBefore(" (")
            assertTrue(quest.items.single { it.name == c.area }.notes!!.startsWith("Boss: $boss."), c.id)
        }
    }

    /** One Goddess Chest per cube; its Pieces of Heart are the ones Heart Pieces tags "Goddess Chest". */
    @Test
    fun twentySevenCubesOpenTwentySevenChests() = runTest {
        val cubes = load("goddess_cubes")
        assertEquals(27, cubes.sectionSize("cubes"))
        assertTrue(cubes.items.all { it.tiers.map { t -> t.name } == listOf("Cube struck", "Chest opened") })
        assertEquals(27.0, cubes.summary("Goddess Cubes struck").max)
        assertEquals(27.0, cubes.summary("Goddess Chests opened").max)
        assertEquals(ChecklistCount(54, 54), cubes.count(cubes.all()))

        val pieces = cubes.items.count { it.reward == "Piece of Heart" }
        assertEquals(6, pieces)
        assertEquals(pieces, load("heart_pieces").idsTagged("Goddess Chest").size)
        // The pouch expansion and the bottle in Items & Equipment each come from a chest.
        assertEquals(1, cubes.items.count { it.reward == "Adventure Pouch expansion" })
        assertEquals(1, cubes.items.count { it.reward == "Empty Bottle" })
    }

    @Test
    fun eightyGratitudeCrystalsAndBatreauxsRewards() = runTest {
        val crystals = load("gratitude_crystals")
        assertEquals(12, crystals.sectionSize("crystal_quests"))
        assertEquals(15, crystals.sectionSize("loose_crystals"))
        assertEquals(65.0, crystals.section("crystal_quests")!!.items.sumOf { it.value })
        assertEquals(80.0, crystals.summary("Gratitude Crystals").max)

        // Batreaux's thresholds rise, each after the one before.
        val batreaux = crystals.section("batreaux")!!.items
        assertEquals(listOf(5, 10, 30, 40, 50, 70, 80), batreaux.map { it.id.removePrefix("batreaux_").toInt() })
        batreaux.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }
    }

    /**
     * The Pieces of Heart from minigames and side quests are the rows tagged "Piece of Heart" in Side
     * Quests and Gratitude Crystals (Batreaux's), one each.
     */
    @Test
    fun minigameAndSideQuestPiecesAreListedWhereTheyreWon() = runTest {
        val hearts = load("heart_pieces")
        val won = hearts.idsTagged("Minigame") + hearts.idsTagged("Side quest")
        val sources = load("side_quests").idsTagged("Piece of Heart") + load("gratitude_crystals").idsTagged("Piece of Heart")
        assertEquals(7, won.size)
        assertEquals(won.size, sources.size, sources.toString())
    }

    @Test
    fun sideQuestsMinigamesAndTheLightningRound() = runTest {
        val side = load("side_quests")
        assertEquals(7, side.sectionSize("minigames"))
        assertEquals(3, side.sectionSize("side_quests"))
        assertEquals(2, side.sectionSize("lightning_round"))
        // The Hylian Shield (Shields) is the Lightning Round's 8-win prize.
        assertTrue(side.items.single { it.id == "thunder_dragon_boss_rush" }.tiers.any { it.name.startsWith("8 wins") })
        assertEquals(listOf("fledge_barrels"), side.items.filter { it.missable }.map { it.id })
    }

    @Test
    fun itemsPouchBottlesAndWallets() = runTest {
        val items = load("items")
        // The potion infusions moved to their own recipe book (docs/ss.md, Potion Infusions).
        assertEquals(listOf("equipment", "capacity", "pouch", "bottles", "wallets"), items.sections.map { it.id })
        assertEquals(13, items.sectionSize("equipment"))
        assertEquals(3, items.sectionSize("capacity"))
        assertEquals(5, items.sectionSize("bottles"))
        assertEquals(5, items.sectionSize("pouch"))
        assertEquals(8.0, items.summary("Pouch slots").max)
        assertEquals(300.0, items.summary("Rupees you can carry").value)
        assertEquals(9900.0, items.summary("Rupees you can carry").max)
        val tiered = items.items.filter { it.tiers.isNotEmpty() }.associate { it.id to it.tiers.size }
        assertEquals(4, tiered["beetle"])
        assertEquals(3, tiered["bow"])
        assertEquals(4, tiered["wallet"])
        // Every dungeon item is in the list.
        DUNGEON_ITEMS.forEach { name -> assertTrue(items.items.any { i -> i.name == name || i.tiers.any { it.name == name } }, name) }
    }

    @Test
    fun tenShieldsTwelveBugsSixteenTreasures() = runTest {
        val shields = load("shields")
        assertEquals(10, shields.steps("shields"))
        assertEquals(listOf(3, 3, 3, 1), shields.items.map { it.steps })
        assertEquals("hylian_shield", shields.items.last().id)

        val found = load("bugs_treasures")
        assertEquals(12, found.sectionSize("bugs"))
        assertEquals(16, found.sectionSize("treasures"))
        assertTrue(found.section("bugs")!!.items.all { it.id.startsWith("bug_") })
        assertTrue(found.section("treasures")!!.items.all { it.id.startsWith("treasure_") })
    }

    /**
     * Story links (docs/ss.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand, and that every item was given a link or chosen to have none.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensItOrIsAvailableFromTheStart() = runTest {
        val links = FILES.drop(1).flatMap { f -> load(f).items }.associate { it.id to it.unlockedBy }
        assertEquals(START.toSet(), links.filterValues { it.isEmpty() }.keys)
        assertEquals(listOf("power_generator"), links["gust_bellows"], "inside the Lanayru Mining Facility")
        assertEquals(listOf("power_generator"), links["container_moldarach"], "its boss, inside too")
        assertEquals(listOf("lanayru_mining_facility"), links["cube_temple_of_time_minecart"], "outside it, after it")
        assertEquals(listOf("temple_of_time"), links["goddess_harp"], "the step's reward")
        assertEquals(listOf("trial_nayru"), links["zeldas_room_wardrobe"], "needs the Clawshots, from Nayru's Silent Realm")
        assertEquals(listOf("hero_song_lanayru"), links["hylian_shield"], "the Thunder Dragon, once cured")
    }

    private companion object {
        /** What can be had from the start of the game: Skyloft's bugs and drops, its shops, and Fledge's barrels. */
        val START = listOf(
            "bug_skyloft_mantis", "bug_sky_stag_beetle", "bug_starry_firefly", "bug_blessed_butterfly", "treasure_amber_relic",
            "treasure_monster_claw", "iron_shield", "sacred_shield", "fledge_barrels",
        )
        val FILES = listOf(
            "main_quest", "items", "heart_pieces", "goddess_cubes", "gratitude_crystals", "side_quests", "shields", "bugs_treasures",
        )
        val DUNGEONS = listOf("skyview_temple", "earth_temple", "lanayru_mining_facility", "ancient_cistern", "sandship", "fire_sanctuary", "sky_keep")
        val DUNGEON_ITEMS = listOf("Beetle", "Bomb Bag", "Gust Bellows", "Whip", "Bow", "Mogma Mitts")
    }
}
