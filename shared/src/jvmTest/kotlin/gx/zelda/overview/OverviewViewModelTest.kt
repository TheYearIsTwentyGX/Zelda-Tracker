package gx.zelda.overview

import gx.zelda.core.CachedLoad
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TestMain
import gx.zelda.core.TrackerKind
import gx.zelda.core.activeSaveFileStore
import gx.zelda.core.createDataStore
import gx.zelda.fairies.data.FairyProgressProvider
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.fairies.model.FairyCatalog
import gx.zelda.figurines.data.FigurineStorage
import gx.zelda.hearts.data.HeartProgressProvider
import gx.zelda.hearts.data.HeartStorage
import gx.zelda.kinstones.data.KinstoneProgressProvider
import gx.zelda.kinstones.data.KinstoneStorage
import gx.zelda.kinstones.model.KinstoneCatalog
import gx.zelda.koroks.data.KorokProgressProvider
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.shrines.data.ShrineStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.BOTW

    /** Loads on the test's main dispatcher, so nothing outlives the test on another thread. */
    private fun viewModel(providers: List<ProgressProvider> = overviewProviders(dataStore, game)) =
        main.track(OverviewViewModel(dataStore, game, providers, loadDispatcher = dispatcher))

    private fun OverviewUiState.summary(title: String): ProgressSummary? =
        (rows.single { it.title == title }.status as? RowStatus.Ready)?.summary

    private fun OverviewUiState.loaded(): Boolean = rows.none { it.status == RowStatus.Loading }

    @Test
    fun switchingSaveFileUpdatesEveryRow() = runTest {
        val shrines = ShrineStorage(dataStore, game)
        val koroks = KorokStorage(dataStore, game)
        val fairies = FairyStorage(dataStore, game)
        shrines.setState("normal_1", "ja_baij", DiscoveryState.COMPLETED)
        koroks.setState("normal_2", "P01", DiscoveryState.COMPLETED)
        koroks.setState("normal_2", "P02", DiscoveryState.COMPLETED)
        fairies.setUnlocked("normal_2", "tera", true)

        val vm = viewModel()
        val first = vm.state.first { it != null && it.loaded() }!!
        assertEquals("normal_1", first.saveFile.id)
        assertEquals(game.saveFiles, first.saveFiles)
        assertEquals(1, first.summary("Shrines")!!.done)
        assertEquals(0, first.summary("Korok Seeds")!!.done)
        assertEquals(0, first.summary("Great Fairy Fountains")!!.done)

        vm.setSaveFile("normal_2").join()
        val second = vm.state.first { it?.saveFile?.id == "normal_2" && it.loaded() }!!
        assertEquals(0, second.summary("Shrines")!!.done)
        assertEquals(2, second.summary("Korok Seeds")!!.done)
        assertEquals(1, second.summary("Great Fairy Fountains")!!.done)
        assertEquals("normal_2", activeSaveFileStore(dataStore, game).data.first(), "every tracker follows the switch")

        // Edits made elsewhere show up without reopening.
        koroks.setState("normal_2", "P03", DiscoveryState.COMPLETED)
        assertEquals(3, vm.state.first { it?.summary("Korok Seeds")?.done == 3 }!!.summary("Korok Seeds")!!.done)
    }

    @Test
    fun aTrackerThatFailsToLoadIsUnavailableAndTheRestCarryOn() = runTest {
        val koroks = KorokStorage(dataStore, game)
        koroks.setState("normal_1", "P01", DiscoveryState.COMPLETED)
        val providers = listOf(
            FairyProgressProvider(FairyStorage(dataStore, game), CachedLoad<FairyCatalog> { error("files/botw/fairies.json not found") }),
            ThrowingProvider,
            KorokProgressProvider(koroks),
        )

        val vm = viewModel(providers)
        val state = vm.state.first { it != null && it.loaded() }!!
        assertEquals(RowStatus.Unavailable, state.rows[0].status)
        assertEquals("Great Fairy Fountains", state.rows[0].title)
        assertEquals(RowStatus.Unavailable, state.rows[1].status)
        assertEquals(1, state.summary("Korok Seeds")!!.done)
        assertEquals(listOf(ItemCount("Korok seeds", 1, 900)), state.mapItems)
    }

    @Test
    fun mapItemsAddUpShrinesKoroksAndBeasts() = runTest {
        ShrineStorage(dataStore, game).setState("normal_1", "ja_baij", DiscoveryState.COMPLETED)
        KorokStorage(dataStore, game).setState("normal_1", "P01", DiscoveryState.COMPLETED)

        val state = viewModel().state.first { it != null && it.loaded() }!!
        val items = state.mapItems!!
        assertEquals(listOf("Divine Beasts", "shrines", "Korok seeds"), items.map { it.label })
        assertEquals(2, items.sumOf { it.done })
        assertEquals(4 + 120 + 900, items.sumOf { it.total })
    }

    @Test
    fun theMinishCapsRowsFollowItsOwnSaveFiles() = runTest {
        val mc = Games.MC
        val hearts = HeartStorage(dataStore, mc)
        val figurines = FigurineStorage(dataStore, mc)
        hearts.setCollected("file_2", "minish_woods_deepwood_entrance", true)
        figurines.setShells("file_2", 7)
        // BotW's active file is its own; switching MC's doesn't touch it.
        activeSaveFileStore(dataStore, game).update { "master" }

        val vm = main.track(OverviewViewModel(dataStore, mc, loadDispatcher = dispatcher))
        val first = vm.state.first { it != null && it.loaded() }!!
        assertEquals("file_1", first.saveFile.id)
        assertEquals(listOf("Heart Pieces", "Kinstone Fusions", "Main Quest", "Carlov's Figurines"), first.rows.map { it.title })
        assertEquals(0, first.summary("Heart Pieces")!!.done)
        assertNull(first.mapItems, "nothing in MC counts toward BotW's map")

        vm.setSaveFile("file_2").join()
        val second = vm.state.first { it?.saveFile?.id == "file_2" && it.loaded() }!!
        assertEquals(1, second.summary("Heart Pieces")!!.done)
        assertTrue(second.summary("Carlov's Figurines")!!.secondary!!.startsWith("7 shells"))
        assertEquals("file_2", activeSaveFileStore(dataStore, mc).data.first())
        assertEquals("master", activeSaveFileStore(dataStore, game).data.first())
    }

    @Test
    fun aMinishCapTrackerThatFailsToLoadIsUnavailable() = runTest {
        val mc = Games.MC
        val providers = listOf(
            HeartProgressProvider(HeartStorage(dataStore, mc)),
            KinstoneProgressProvider(KinstoneStorage(dataStore, mc), CachedLoad<KinstoneCatalog> { error("files/mc/kinstones.json not found") }),
        )

        val vm = main.track(OverviewViewModel(dataStore, mc, providers, loadDispatcher = dispatcher))
        val state = vm.state.first { it != null && it.loaded() }!!
        assertEquals(50, state.summary("Heart Pieces")!!.total)
        assertEquals(RowStatus.Unavailable, state.rows[1].status)
        assertEquals("Kinstone Fusions", state.rows[1].title)
        assertEquals(TrackerKind.KINSTONES, state.rows[1].kind, "a tap still opens the tracker")
    }

    @Test
    fun aGameWithNoProvidersHasNoRows() = runTest {
        val state = viewModel(providers = emptyList()).state.first { it != null }!!
        assertTrue(state.rows.isEmpty())
        assertNull(state.mapItems)
    }

    /** Throws when asked for its flow, before there is any flow to catch. */
    private object ThrowingProvider : ProgressProvider {
        override val kind = TrackerKind.ARMOR
        override val title = "Broken"

        override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = throw IllegalStateException("storage unreadable")
    }
}
