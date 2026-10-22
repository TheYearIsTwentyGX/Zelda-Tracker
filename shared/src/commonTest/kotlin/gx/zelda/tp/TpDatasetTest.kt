package gx.zelda.tp

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
 * Twilight Princess's checklists (the GameCube original, with HD's lists as separate tabs), against
 * the counts in docs/tp.md. `ChecklistDatasetsTest` already checks every file's format; this checks
 * TP's own numbers and cross-references.
 */
class TpDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("tp/$name.json", strict = true)

    private fun ChecklistCatalog.sectionSize(id: String): Int = section(id)!!.items.size

    private fun ChecklistCatalog.summary(label: String, progress: ChecklistProgress = ChecklistProgress()) =
        evaluate((summaries + sections.mapNotNull { it.summary }).single { it.label == label }, progress)

    private fun ChecklistCatalog.all(): ChecklistProgress = items.fold(ChecklistProgress()) { p, i -> p.withLevel(i, i.steps) }

    @Test
    fun theOverviewComesFirstAndEveryChecklistIsListed() {
        val trackers = Games.TP.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(FILES, trackers.mapNotNull { it.dataFile?.removePrefix("tp/")?.removeSuffix(".json") })
    }

    /** Three files on the GameCube, the Wii and HD (Zelda Wiki, Saving). */
    @Test
    fun threeSaveFiles() {
        assertEquals(listOf("file_1", "file_2", "file_3"), Games.TP.saveFiles.map { it.id })
    }

    /** Ids are saved progress: unique across every TP file too, so a search or a note can't confuse two. */
    @Test
    fun idsAreUniqueAcrossFiles() = runTest {
        val ids = FILES.flatMap { f -> load(f).let { c -> c.items.map { it.id } + c.sections.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }

    @Test
    fun nineDungeonsThreeFusedShadowsAndFourMirrorShards() = runTest {
        val quest = load("main_quest")
        val dungeons = quest.items.filter { "Dungeon" in it.tags }
        assertEquals(DUNGEONS, dungeons.map { it.id })
        assertEquals(3.0, quest.summary("Fused Shadows").max)
        assertEquals(4.0, quest.summary("Mirror Shards").max)
        assertEquals(48.0, quest.summary("Tears of Light").max)
        assertEquals(0.0, quest.summary("Tears of Light").value)
        assertEquals(8, quest.items.count { it.reward?.contains("Heart Container") == true })
        // The quest is one chain: every step after the first needs the one before it.
        quest.items.zipWithNext().forEach { (before, after) -> assertEquals(listOf(before.id), after.requires, after.id) }
        assertEquals("hyrule_castle", quest.items.last().id)
        // Every dungeon names its boss and its dungeon item or lack of one.
        dungeons.forEach { assertTrue(it.notes.orEmpty().startsWith("Boss: ") || it.notes.orEmpty().startsWith("Final boss: "), it.id) }
    }

    @Test
    fun heartPiecesMakeTwentyHearts() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(45, hearts.sectionSize("pieces"))
        assertEquals(8, hearts.sectionSize("containers"))
        assertEquals(3.0, hearts.summary("Hearts").value)
        assertEquals(20.0, hearts.summary("Hearts").max)
        assertEquals(ChecklistCount(53, 53), hearts.count(hearts.all()))
    }

    /** Every Heart Container is a boss named in the Main Quest. */
    @Test
    fun containersMatchTheMainQuestBosses() = runTest {
        val bosses = load("main_quest").items.filter { it.reward?.contains("Heart Container") == true }.map { it.notes.orEmpty() }
        load("heart_pieces").section("containers")!!.items.forEach { container ->
            assertTrue(bosses.any { "Boss: ${container.name}." in it }, "${container.name} isn't a Main Quest boss")
        }
    }

    @Test
    fun sixtyPoeSoulsAndJovanisRewards() = runTest {
        val poes = load("poe_souls")
        assertEquals(60, poes.sectionSize("poes"))
        assertEquals(listOf("jovani_20_souls", "jovani_60_souls", "gengle_rupees"), poes.section("rewards")!!.items.map { it.id })
        // The three Poes in the Cave of Ordeals are the floors that list one as a reward.
        val inCave = poes.items.filter { it.area.orEmpty().startsWith("Cave of Ordeals, floor ") }.map { it.area!!.substringAfterLast(' ').toInt() }
        val floors = load("cave_of_ordeals").section("floors")!!.items.filter { it.reward == "Poe Soul" }.map { it.name.removePrefix("Floor ").toInt() }
        assertEquals(listOf(17, 33, 44), inCave)
        assertEquals(inCave, floors)
    }

    /** 12 species, a male and a female of each, and Agitha's two wallets. */
    @Test
    fun twentyFourGoldenBugsInPairs() = runTest {
        val bugs = load("golden_bugs")
        assertEquals(24, bugs.sectionSize("bugs"))
        val species = bugs.section("bugs")!!.items.groupBy { it.name.substringAfter(' ') }
        assertEquals(12, species.size)
        species.forEach { (name, pair) -> assertEquals(setOf("Male", "Female"), pair.map { it.name.substringBefore(' ') }.toSet(), name) }
        assertEquals(listOf("agitha_first_bug", "agitha_all_bugs"), bugs.section("agitha")!!.items.map { it.id })
    }

    /** The seven skills come in order, each after the Howling Stone that calls the Golden Wolf. */
    @Test
    fun sevenHiddenSkillsInOrder() = runTest {
        val skills = load("hidden_skills")
        assertEquals(SKILLS, skills.section("skills")!!.items.map { it.id })
        assertEquals(7, skills.sectionSize("howling_stones"))
        val stones = skills.section("howling_stones")!!.items.map { it.id }.toSet()
        skills.section("skills")!!.items.zipWithNext().forEach { (before, after) ->
            assertTrue(before.id in after.requires, "${after.id} doesn't follow ${before.id}")
            assertEquals(1, after.requires.count { it in stones }, after.id)
        }
        // Six stones call the Golden Wolf; the Sacred Grove's opens the way to the Master Sword.
        val used = skills.items.flatMap { it.requires }.filter { it in stones }.toSet()
        assertEquals(setOf("stone_sacred_grove"), stones - used)
    }

    @Test
    fun fiftyFloorsOfOrdealsAndFortyOfShadows() = runTest {
        val cave = load("cave_of_ordeals")
        val floors = cave.section("floors")!!.items
        assertEquals((1..50).map { "floor_" + it.toString().padStart(2, '0') }, floors.map { it.id })
        // A Great Fairy every tenth floor.
        listOf(10, 20, 30, 40, 50).forEach { assertTrue(floors[it - 1].reward != null, "floor $it has no reward") }
        assertEquals(40, cave.sectionSize("cave_of_shadows"))
        assertTrue(cave.section("cave_of_shadows")!!.separate)
        assertEquals(ChecklistCount(50, 50), cave.count(cave.all()))
    }

    @Test
    fun sidequestsLettersFishAndHdStamps() = runTest {
        val side = load("sidequests")
        assertEquals(12, side.sectionSize("sidequests"))
        assertEquals(16, side.sectionSize("letters"))
        assertEquals(6, side.sectionSize("fish"))
        assertEquals(50, side.sectionSize("hd_stamps"))
        assertEquals(listOf("hd_stamps"), side.sections.filter { it.separate }.map { it.id })
        assertEquals(listOf("letter_ooccoo_dear_adventurer", "letter_ooccoo_dear_adventurer_2"), side.items.filter { it.missable }.map { it.id })
    }

    @Test
    fun itemsUpgradesAndFourBottles() = runTest {
        val items = load("items")
        assertEquals(15, items.sectionSize("inventory"))
        assertEquals(8, items.sectionSize("equipment"))
        assertEquals(6, items.sectionSize("upgrades"))
        assertEquals(4, items.sectionSize("bottles"))
        assertEquals(listOf("hd_items"), items.sections.filter { it.separate }.map { it.id })
        val tiered = items.items.filter { it.tiers.isNotEmpty() }.associate { it.id to it.tiers.size }
        assertEquals(
            mapOf(
                "fishing_rod" to 2, "bombs" to 3, "clawshot" to 2, "dominion_rod" to 2, "sky_book" to 2,
                "master_sword" to 2, "wallet" to 2, "quiver" to 2,
            ),
            tiered,
        )
        assertEquals(1000.0, items.summary("Rupees").max)
        assertEquals(100.0, items.summary("Arrows").max)
        assertEquals(3.0, items.summary("Bomb Bags").max)
        assertEquals(60.0, items.summary("Bombs per bag").max)
        // Every dungeon item is in the list.
        DUNGEON_ITEMS.forEach { name -> assertTrue(items.items.any { i -> i.name == name || i.tiers.any { it.name == name } }, name) }
    }

    /**
     * Story links (docs/tp.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand, and that every item was given a link or chosen to have none.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensItOrIsAvailableFromTheStart() = runTest {
        val links = FILES.drop(1).flatMap { f -> load(f).items }.associate { it.id to it.unlockedBy }
        assertEquals(START.toSet(), links.filterValues { it.isEmpty() }.keys)
        assertEquals(listOf("rutela_gift"), links["lakebed_chandelier"], "inside the Lakebed Temple: the Zora Armor opens it")
        assertEquals(listOf("lakebed_temple"), links["stamp_u"], "in the Goron Mines, but needs the Clawshot")
        assertEquals(listOf("lanayru_twilight"), links["male_ladybug"], "Hyrule Field once Lanayru is light again")
        assertEquals(listOf("draw_master_sword"), links["jovani_20_souls"], "Poes need Wolf Link at will")
        assertEquals(listOf("ilia_memory"), links["floor_35"], "floors inherit the gate of the floor that names it (31)")
        assertEquals(listOf("temple_of_time"), links["great_spin"], "a Hidden Skill follows its Howling Stone")
    }

    private companion object {
        /** Ordon from the first day: the rod, the Slingshot, Sera's Bottle and Ordon's two fish. */
        val START = listOf("fishing_rod", "slingshot", "bottle_sera", "fish_greengill", "fish_ordon_catfish")
        val FILES = listOf("main_quest", "items", "heart_pieces", "poe_souls", "golden_bugs", "hidden_skills", "cave_of_ordeals", "sidequests")
        val DUNGEONS = listOf(
            "forest_temple", "goron_mines", "lakebed_temple", "arbiters_grounds", "snowpeak_ruins",
            "temple_of_time", "city_in_the_sky", "palace_of_twilight", "hyrule_castle",
        )
        val SKILLS = listOf("ending_blow", "shield_attack", "back_slice", "helm_splitter", "mortal_draw", "jump_strike", "great_spin")
        val DUNGEON_ITEMS = listOf("Gale Boomerang", "Hero's Bow", "Clawshot", "Spinner", "Ball and Chain", "Dominion Rod", "Double Clawshots")
    }
}
