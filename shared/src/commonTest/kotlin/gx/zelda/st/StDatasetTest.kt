package gx.zelda.st

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
 * Spirit Tracks's checklists against the counts in docs/st.md, where Zelda Wiki, Zelda Dungeon and
 * IGN agree, plus the links between files. `ChecklistDatasetsTest` already checks every file's
 * format and in-file references.
 */
class StDatasetTest {
    private suspend fun load(name: String): ChecklistCatalog = ChecklistRepository.load("st/$name.json", strict = true)

    private fun ChecklistCatalog.sizes(): Map<String, Int> = sections.associate { it.id to it.items.size }

    private fun ChecklistCatalog.all(): ChecklistProgress = items.fold(ChecklistProgress()) { p, i -> p.withLevel(i, i.steps) }

    private fun ChecklistCatalog.summary(label: String, progress: ChecklistProgress = ChecklistProgress()) =
        evaluate(summaries.single { it.label == label }, progress)

    @Test
    fun theOverviewComesFirstAndEveryChecklistIsListed() {
        val trackers = Games.ST.trackers
        assertEquals(TrackerKind.OVERVIEW, trackers.first().kind)
        assertEquals(FILES, trackers.mapNotNull { it.dataFile?.removePrefix("st/")?.removeSuffix(".json") })
    }

    /** "A maximum of two save files at any time" (Nintendo's manual). */
    @Test
    fun twoSaveFiles() {
        assertEquals(listOf("file_1", "file_2"), Games.ST.saveFiles.map { it.id })
    }

    /** Ids are saved progress: unique across every ST file too, so a search or a note can't confuse two. */
    @Test
    fun idsAreUniqueAcrossFiles() = runTest {
        val ids = FILES.flatMap { f -> load(f).let { c -> c.items.map { it.id } + c.sections.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }

    /** Zelda Wiki's realms, in the order they're reached; each file keeps that order for the ones it uses. */
    @Test
    fun regionsFollowTheRealmsInOrder() = runTest {
        FILES.forEach { f ->
            val regions = load(f).regions
            assertEquals(REALMS.filter { it in regions }, regions, f)
        }
    }

    @Test
    fun mainQuestIsOneChainThroughFiveTemplesAndTheTower() = runTest {
        val quest = load("main_quest")
        assertEquals(27, quest.items.size)
        quest.items.zipWithNext().forEach { (before, step) -> assertEquals(listOf(before.id), step.requires, step.id) }
        assertEquals("malladus", quest.items.last().id)
        assertEquals(5.0 to 0.0, quest.summary("Temples cleared", quest.all()).value to quest.summary("Temples cleared").value)
        assertEquals(4.0, quest.summary("Rail Maps", quest.all()).value)
        assertEquals(5.0, quest.summary("Lokomo duets", quest.all()).value)
        assertEquals(5, quest.items.count { it.reward?.contains("Heart Container") == true })
        assertEquals(6, quest.items.count { it.region == "Tower of Spirits" })
    }

    @Test
    fun heartsAddUpToSixteen() = runTest {
        val hearts = load("heart_containers")
        assertEquals(mapOf("containers" to 13), hearts.sizes())
        assertEquals(3.0 to 16.0, hearts.summary("Hearts").let { it.value to it.max })
    }

    /** The five boss Heart Containers are the bosses the Main Quest's temple notes name ("Boss: Stagnox."); Byrne gives none. */
    @Test
    fun bossHeartContainersMatchTheMainQuest() = runTest {
        val bosses = load("main_quest").items.filter { it.id.endsWith("_temple") }.mapNotNull { Regex("""Boss: (\w+)\.""").find(it.notes.orEmpty())?.groupValues?.get(1) }
        val hearts = load("heart_containers").items.filter { "Boss" in it.tags }.map { it.name }
        assertEquals(bosses, hearts)
    }

    @Test
    fun itemsAndCapacities() = runTest {
        val items = load("items")
        assertEquals(mapOf("equipment" to 12, "upgrades" to 2, "techniques" to 2), items.sizes())
        assertEquals(listOf("Recruit's Sword", "Lokomo Sword"), items.item("sword")!!.tiers.map { it.name })
        assertEquals(0.0 to 30.0, items.summary("Bombs").let { it.value to it.max })
        assertEquals(0.0 to 50.0, items.summary("Arrows").let { it.value to it.max })
    }

    @Test
    fun twentyForceGemsAllDelivered() = runTest {
        val gems = load("force_gems")
        assertEquals(mapOf("gems" to 20), gems.sizes())
        // Every gem is a passenger or cargo run's reward, named the same, and no run names a gem that isn't one.
        val paid = load("deliveries").items.mapNotNull { it.reward?.let { r -> Regex("""^Force Gem: (.+?)( \(.*\))?$""").find(r)?.groupValues?.get(1) } }
        assertEquals(gems.items.map { it.name }.toSet(), paid.toSet())
        assertEquals(20, paid.size)
    }

    @Test
    fun deliveriesSplitIntoPassengersAndCargo() = runTest {
        val runs = load("deliveries")
        assertEquals(25, runs.items.size)
        assertEquals(15.0, runs.summary("Passengers", runs.all()).value)
        assertEquals(10.0, runs.summary("Cargo", runs.all()).value)
    }

    @Test
    fun fiftyRabbitsTenPerRealm() = runTest {
        val rabbits = load("rabbits")
        assertEquals(mapOf("rabbits" to 50, "bunnio_rewards" to 8), rabbits.sizes())
        assertEquals(listOf("Forest Realm", "Snow Realm", "Ocean Realm", "Fire Realm", "Sand Realm").associateWith { 10 },
            rabbits.section("rabbits")!!.items.groupingBy { it.region!! }.eachCount())
        assertEquals(50.0, rabbits.summary("Rabbits", rabbits.all()).value)
    }

    @Test
    fun stampsSongsStationsAndTrainParts() = runTest {
        assertEquals(mapOf("stamps" to 20, "stamp_rewards" to 3), load("stamps").sizes())
        assertEquals(mapOf("flute_songs" to 5), load("songs").sizes())
        assertEquals(mapOf("stations" to 22, "warp_gates" to 8), load("stations").sizes())
        val train = load("train_parts")
        assertEquals(8, train.sections.size)
        train.sections.forEach { set -> assertEquals(listOf("Engine", "Cannon", "Passenger Car", "Freight Car"), set.items.map { it.tags.single() }, set.id) }
        assertEquals(0.0 to 8.0, train.summary("Complete sets").let { it.value to it.max })
    }

    /** Nothing in Spirit Tracks can be missed for good (docs/st.md). */
    @Test
    fun nothingIsMissable() = runTest {
        FILES.forEach { f -> assertEquals(emptyList(), load(f).items.filter { it.missable }.map { it.id }, f) }
    }

    @Test
    fun everyItemSaysHow() = runTest {
        FILES.forEach { f ->
            val c = load(f)
            c.items.filter { it.tiers.isEmpty() }.forEach { assertTrue(!it.howTo.isNullOrBlank(), "${it.id} in $f") }
            // A tiered item says how on the item or on every tier.
            c.items.filter { it.tiers.isNotEmpty() && it.howTo.isNullOrBlank() }
                .forEach { assertTrue(it.tiers.all { t -> !t.howTo.isNullOrBlank() }, "${it.id} in $f") }
        }
    }

    private companion object {
        val FILES = listOf(
            "main_quest", "items", "heart_containers", "force_gems", "deliveries", "rabbits",
            "stamps", "songs", "stations", "train_parts", "minigames",
        )
        val REALMS = listOf("Forest Realm", "Tower of Spirits", "Snow Realm", "Ocean Realm", "Fire Realm", "Sand Realm", "Dark Realm")
    }
    /**
     * Story links (docs/st.md, *Story links*). `StoryLinksDatasetTest` checks every link is a real
     * step; this checks a few by hand and lists what's had from the start in Aboda and Castle Town.
     */
    @Test
    fun everyItemIsLinkedToTheStepThatOpensIt() = runTest {
        val files = listOf("items", "heart_containers", "force_gems", "deliveries", "rabbits", "stamps", "stations", "train_parts", "minigames", "songs")
        val links = files.flatMap { load(it).items }.associate { it.id to it.unlockedBy }
        assertEquals(setOf("shield", "station_aboda_village", "station_castle_town"), links.filterValues { it.isEmpty() }.keys)
        assertEquals(listOf("trading_post_bridge"), links["station_papuchia_village"], "the Ocean Realm, over the rebuilt bridge")
        assertEquals(listOf("carben_duet"), links["whip"], "inside the Ocean Temple, which Carben's duet opens")
        assertEquals(listOf("kagoron_mega_ice"), links["slippery_station_rink"], "Steem's Force Gem, which needs the freight car")
        assertEquals(listOf("trading_post_bridge"), links["wooden_engine"], "Linebeck III trades once the bridge is rebuilt")
        assertEquals(listOf("sand_temple"), links["cargo_dark_ore_linebeck"], "after Ferrus's ride, which follows the Sand Temple")
    }
}
