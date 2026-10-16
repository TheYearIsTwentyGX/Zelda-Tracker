package gx.zelda.overview

import gx.zelda.core.Games
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.TrackerKind
import gx.zelda.core.createDataStore
import gx.zelda.figurines.data.FigurineProgressProvider
import gx.zelda.figurines.data.FigurineRepository
import gx.zelda.figurines.data.FigurineStorage
import gx.zelda.hearts.data.HeartProgressProvider
import gx.zelda.hearts.data.HeartRepository
import gx.zelda.hearts.data.HeartStorage
import gx.zelda.kinstones.data.KinstoneProgressProvider
import gx.zelda.kinstones.data.KinstoneRepository
import gx.zelda.kinstones.data.KinstoneStorage
import gx.zelda.mainquest.data.MainQuestProgressProvider
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Minish Cap's overview rows, computed from its real datasets and progress written to a fresh store. */
class McProgressProvidersTest {
    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.MC

    private suspend fun ProgressProvider.on(file: String = "file_1"): ProgressSummary = summary(game.saveFile(file)).first()

    @Test
    fun eachGameGetsItsOwnRows() {
        assertEquals(TrackerKind.OVERVIEW, game.trackers.first().kind, "listed first, as in BotW")
        assertEquals(
            listOf("Heart Pieces", "Kinstone Fusions", "Main Quest", "Carlov's Figurines"),
            overviewProviders(dataStore, game).map { it.title },
        )
        assertEquals(
            listOf("Armor", "Great Fairy Fountains", "Main Quest", "Shrines", "Hearts & Stamina", "Hyrule Compendium", "Korok Seeds", "Side Quests", "Overworld Bosses"),
            overviewProviders(dataStore, Games.BOTW).map { it.title },
        )
        // TotK lists no overview, so none of its trackers is built for one.
        assertTrue(overviewProviders(dataStore, Games.TOTK).isEmpty())
    }

    @Test
    fun heartsCountPiecesContainersAndTheHeartsTotal() = runTest {
        val catalog = HeartRepository.load(game)
        val storage = HeartStorage(dataStore, game)
        val collected = catalog.pieces.take(5) + catalog.containers.first()
        collected.forEach { storage.setCollected("file_1", it.id, true) }

        val summary = HeartProgressProvider(storage).on()
        assertEquals(6, summary.done)
        assertEquals(44 + 6, summary.total)
        assertEquals("5 hearts + 1/4 of 20 · Pieces 5 / 44 · Containers 1 / 6", summary.secondary, "3 + 1 container + 4 pieces")
        val next = catalog.entries.first { it !in collected }
        assertEquals("${next.name}, in ${next.region}", summary.next)
        assertNull(summary.mapItems)
    }

    @Test
    fun heartSuggestionNamesNoEntryWhileSpoilersAreHidden() = runTest {
        val catalog = HeartRepository.load(game)
        val storage = HeartStorage(dataStore, game)
        storage.updatePrefs { it.copy(hideUncollected = true) }

        val hidden = HeartProgressProvider(storage).on().next
        assertNotNull(hidden)
        val firstRegion = catalog.entries.first().region
        assertEquals("${catalog.entries.count { it.region == firstRegion }} hearts still to find in $firstRegion", hidden)
        assertFalse(catalog.entries.any { it.name in hidden || it.area in hidden }, "no entry named: $hidden")
    }

    @Test
    fun kinstonesCountFusionsAndGoldAndNeverNameAResult() = runTest {
        val catalog = KinstoneRepository.load(game)
        val storage = KinstoneStorage(dataStore, game)
        val gold = catalog.fusions.filter { it.isGold }
        storage.setFused("file_1", gold[0].id, true)
        storage.setFused("file_1", catalog.fusions.first { !it.isGold }.id, true)

        val shown = KinstoneProgressProvider(storage).on()
        assertEquals(2, shown.done)
        assertEquals(100, shown.total)
        assertTrue(shown.secondary!!.startsWith("Gold 1 / 9 · "), shown.secondary)
        assertNotNull(shown.next)
        assertTrue(shown.next!!.startsWith("Fuse with "), shown.next)

        // Results hidden: the row names a partner, which the list shows anyway, and never what a fusion does.
        storage.updatePrefs { it.copy(hideResults = true) }
        val hidden = KinstoneProgressProvider(storage).on()
        assertEquals(shown.next, hidden.next)
        assertFalse(catalog.fusions.any { it.result in hidden.next!! }, hidden.next)
    }

    @Test
    fun mainQuestCountsDungeonsElementsItemsAndScrolls() = runTest {
        val catalog = MainQuestRepository.load(game)
        val storage = MainQuestStorage(dataStore, game)
        // Fortress of Winds has no Element, so two dungeons give one.
        storage.setDone("file_1", MainQuestSection.DUNGEONS, "deepwood_shrine", true)
        storage.setDone("file_1", MainQuestSection.DUNGEONS, "fortress_of_winds", true)
        catalog.items.take(3).forEach { storage.setDone("file_1", MainQuestSection.ITEMS, it.id, true) }
        storage.setDone("file_1", MainQuestSection.TIGER_SCROLLS, "spin_attack", true)

        val summary = MainQuestProgressProvider(storage).on()
        assertEquals(2 + 3 + 1, summary.done)
        assertEquals(6 + 38 + 8, summary.total, "Elements come with dungeons, so they aren't counted twice")
        assertEquals("Dungeons 2 / 6 · Elements 1 / 4 · Items 3 / 38 · Tiger Scrolls 1 / 8", summary.secondary)
        assertEquals("Clear Cave of Flames in Mount Crenel", summary.next, "the first dungeon not cleared, in story order")
        assertNull(summary.mapItems)
    }

    @Test
    fun mainQuestSuggestsScrollsThenItemsOnceTheDungeonsAreDone() = runTest {
        val catalog = MainQuestRepository.load(game)
        val storage = MainQuestStorage(dataStore, game)
        val provider = MainQuestProgressProvider(storage)
        catalog.dungeons.forEach { storage.setDone("file_1", MainQuestSection.DUNGEONS, it.id, true) }
        storage.setDone("file_1", MainQuestSection.TIGER_SCROLLS, "spin_attack", true)
        assertEquals("Learn Rock Breaker from Swiftblade", provider.on().next)
        assertTrue(provider.on().secondary!!.startsWith("Dungeons 6 / 6 · Elements 4 / 4 · "))

        catalog.scrolls.forEach { storage.setDone("file_1", MainQuestSection.TIGER_SCROLLS, it.id, true) }
        val item = catalog.items.first()
        assertEquals("Next item: ${item.name} (${item.region})", provider.on().next)

        catalog.items.forEach { storage.setDone("file_1", MainQuestSection.ITEMS, it.id, true) }
        val done = provider.on()
        assertEquals(done.total, done.done)
        assertEquals("Every dungeon, item and Tiger Scroll done", done.next)
    }

    @Test
    fun figurinesCountShellsAndFollowTheSpoilerSetting() = runTest {
        val catalog = FigurineRepository.load(game)
        val storage = FigurineStorage(dataStore, game)
        val provider = FigurineProgressProvider(storage)
        val atStart = catalog.figurines.filter { it.checkpoint == catalog.checkpoints.first().id }
        val free = atStart.filter { it.requirement == null }

        val fresh = provider.on()
        assertEquals(0, fresh.done)
        assertEquals(136, fresh.total)
        assertTrue(fresh.secondary!!.startsWith("0 shells · Characters 0 / "), fresh.secondary)
        assertEquals("${free.size} figurines to win now: find Mysterious Shells to draw", fresh.next)

        storage.setShells("file_1", 12)
        assertTrue(provider.on().secondary!!.startsWith("12 shells · "))
        assertEquals("${free.size} figurines to win now · 100% a draw with one shell", provider.on().next)

        // Everything Carlov has now is won: what's left at this checkpoint waits on side tasks.
        free.forEach { storage.setOwned("file_1", it.number, true) }
        val waiting = atStart.filter { it.requirement != null }
        assertEquals("${waiting[0].label} needs a side task: ${waiting[0].requirement}", provider.on().next)
        storage.updatePrefs { it.copy(hideUnavailable = true) }
        assertEquals("${waiting.size} figurines waiting on a side task", provider.on().next)

        // Those done too: the next checkpoint is named only while spoilers are shown.
        waiting.forEach {
            storage.setRequirementMet("file_1", it.number, true)
            storage.setOwned("file_1", it.number, true)
        }
        val hidden = provider.on().next
        assertEquals("Nothing new at Carlov's until the story moves on", hidden)
        storage.updatePrefs { it.copy(hideUnavailable = false) }
        assertEquals("Nothing new at Carlov's until the story moves on: ${catalog.checkpoints[1].detail}", provider.on().next)
        assertEquals(atStart.size, provider.on().done)
    }

    @Test
    fun aSaveFileWithNoDataYetShowsZeroEverywhere() = runTest {
        overviewProviders(dataStore, game).forEach { provider ->
            val summary = provider.on("file_2")
            assertEquals(0, summary.done, provider.title)
            assertTrue(summary.total > 0, provider.title)
            assertEquals(0f, summary.fraction, provider.title)
            assertNotNull(summary.next, provider.title)
        }
    }
}
