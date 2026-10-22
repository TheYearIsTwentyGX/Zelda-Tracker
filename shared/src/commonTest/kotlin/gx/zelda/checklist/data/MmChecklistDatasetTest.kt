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
 * Majora's Mask's checklists (`files/mm/`), against the counts in docs/mm.md. The format itself is
 * checked for every game by [ChecklistDatasetsTest].
 */
class MmChecklistDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("mm/$name.json", strict = true)

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.ids(section: String): Set<String> = section(section)!!.items.map { it.id }.toSet()

    @Test
    fun mmListsEveryChecklistAfterItsOverview() {
        val trackers = Games.MM.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(
            listOf("main_quest", "masks", "heart_pieces", "bombers_notebook", "stray_fairies", "spider_houses", "songs", "items", "world"),
            trackers.drop(1).map { it.dataFile!!.removePrefix("mm/").removeSuffix(".json") },
        )
        // Two files on the N64 cartridge; an owl save isn't a file of its own.
        assertEquals(listOf("file_1", "file_2"), Games.MM.saveFiles.map { it.id })
    }

    @Test
    fun mainQuestHasFourTemplesAndMajoraNeedsThemAll() = runTest {
        val quest = load("main_quest")
        assertEquals(mapOf("prologue" to 1, "temple_songs" to 4, "temples" to 4, "finale" to 1), quest.sizes())
        assertEquals(quest.ids("temples"), quest.item("defeat_majora")!!.requires.toSet())
        assertEquals("Remains 0 / 4", quest.evaluate(quest.summaries.single(), ChecklistProgress()).text)
    }

    /** Each temple opens with a song, which it requires; the Elegy needs the Ice Arrows from Great Bay. */
    @Test
    fun eachTempleRequiresItsSong() = runTest {
        val quest = load("main_quest")
        assertEquals(
            mapOf(
                "woodfall_temple" to "woodfall_sonata", "snowhead_temple" to "snowhead_lullaby",
                "great_bay_temple" to "great_bay_bossa_nova", "stone_tower_temple" to "ikana_elegy",
            ),
            quest.section("temples")!!.items.associate { it.id to it.requires.single() },
        )
        assertEquals(listOf("great_bay_temple"), quest.item("ikana_elegy")!!.requires)
    }

    /**
     * Story links (docs/mm.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand and counts what a Deku has from the start.
     */
    @Test
    fun everyItemIsLinkedToTheStepsThatOpenIt() = runTest {
        val files = listOf("masks", "heart_pieces", "bombers_notebook", "stray_fairies", "spider_houses", "songs", "items", "world")
        val items = files.flatMap { load(it).items }
        val links = items.associate { it.id to it.unlockedBy }
        assertEquals(17, items.count { it.unlockedBy.isEmpty() }, "the first cycle's Deku things in Clock Town")
        assertEquals(listOf("woodfall_sonata"), links["heros_bow"], "inside Woodfall Temple, which the Sonata opens")
        assertEquals(listOf("snowhead_lullaby"), links["goht_container"])
        assertEquals(listOf("snowhead_temple"), links["bottle_goron_race"], "spring, after Goht")
        assertEquals(listOf("great_bay_bossa_nova"), links["poe_sisters"], "upper Ikana Canyon needs the Ice Arrows")
        assertEquals(listOf("woodfall_temple", "snowhead_temple", "great_bay_temple", "stone_tower_temple"), links["moon_link_trial"])
    }

    @Test
    fun twentyFourMasksAndTheFierceDeityNeedsTheOtherTwenty() = runTest {
        val masks = load("masks")
        assertEquals(mapOf("transformation" to 4, "masks" to 20), masks.sizes())
        assertEquals(masks.ids("masks"), masks.item("fierce_deity_mask")!!.requires.toSet())
    }

    @Test
    fun fiftyTwoPiecesFourContainersAndTwentyHearts() = runTest {
        val hearts = load("heart_pieces")
        assertEquals(mapOf("pieces" to 52, "containers" to 4), hearts.sizes())
        assertTrue(hearts.items.all { it.region != null }, "every heart has a region")
        val total = hearts.summaries.single()
        assertEquals("Hearts 3 / 20", hearts.evaluate(total, ChecklistProgress()).text)
        // The Moon has a piece in each of the four children's trials.
        assertEquals(4, hearts.items.count { it.region == "The Moon" })
    }

    @Test
    fun theNotebookHasItsTwentyPeople() = runTest {
        assertEquals(mapOf("people" to 20), load("bombers_notebook").sizes())
    }

    @Test
    fun fifteenFairiesPerTempleAndEachGreatFairyNeedsHers() = runTest {
        val fairies = load("stray_fairies")
        assertEquals(
            mapOf("clock_town" to 1, "woodfall" to 15, "snowhead" to 15, "great_bay" to 15, "stone_tower" to 15, "great_fairies" to 6),
            fairies.sizes(),
        )
        val temples = listOf("woodfall", "snowhead", "great_bay", "stone_tower")
        val rewards = fairies.section("great_fairies")!!.items
        temples.forEach { temple ->
            assertEquals(1, rewards.count { it.requires.toSet() == fairies.ids(temple) }, "$temple's Great Fairy needs exactly its 15")
        }
        // Clock Town's fairy gives magic (as a Deku) and the Great Fairy's Mask (as human).
        assertEquals(2, rewards.count { it.requires == listOf("clock_town_stray_fairy") })
    }

    @Test
    fun thirtyGoldSkulltulasPerHouseAndEachRewardNeedsThem() = runTest {
        val houses = load("spider_houses")
        assertEquals(mapOf("swamp_spider_house" to 30, "oceanside_spider_house" to 30, "rewards" to 2), houses.sizes())
        assertEquals(houses.ids("swamp_spider_house"), houses.item("swamp_sh_reward")!!.requires.toSet())
        assertEquals(houses.ids("oceanside_spider_house"), houses.item("ocean_sh_reward")!!.requires.toSet())
    }

    @Test
    fun songsItemsBottlesOwlsMapsAndBank() = runTest {
        assertEquals(mapOf("songs" to 10, "secret_songs" to 2), load("songs").sizes())
        val items = load("items")
        assertEquals(6, items.sizes()["bottles"])
        // Upgrade levels: sword 2, wallet 2, quiver 2, bomb bag 3, magic 2; the Mirror Shield is one step.
        assertEquals(12, items.section("upgrades")!!.items.sumOf { it.steps })
        assertEquals(mapOf("owl_statues" to 10, "maps" to 6, "bank" to 3), load("world").sizes())
    }
}
