package gx.zelda.overview

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.ChecklistTestGame
import gx.zelda.checklist.data.ChecklistProgressProvider
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.core.CachedLoad
import gx.zelda.core.TestMain
import gx.zelda.core.TrackerKind
import gx.zelda.core.createDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A game made only of checklists gets an overview row per checklist with no code of its own. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = ChecklistTestGame

    @Test
    fun everyChecklistGetsARowInTheGamesOrder() {
        val providers = overviewProviders(dataStore, game)
        assertEquals(listOf("Hearts", "Echoes"), providers.map { it.title })
        assertEquals(listOf("test/hearts.json", "test/echoes.json"), providers.map { it.dataFile })
        providers.forEach { assertIs<ChecklistProgressProvider>(it) }
        assertEquals(setOf(TrackerKind.CHECKLIST), providers.map { it.kind }.toSet())
    }

    @Test
    fun rowsFollowTheActiveFileAndKnowWhichChecklistTheyOpen() = runTest {
        val hearts = ChecklistStorage(dataStore, game, "test/hearts.json")
        hearts.setDone("file_2", ChecklistFixture.catalog.item("boss_jabul")!!, true)
        val providers = listOf(
            ChecklistProgressProvider(hearts, "Hearts", CachedLoad { ChecklistFixture.catalog }),
            // No such bundled file: this row alone is unavailable.
            ChecklistProgressProvider(ChecklistStorage(dataStore, game, "test/echoes.json"), "Echoes"),
        )
        val vm = main.track(OverviewViewModel(dataStore, game, providers, loadDispatcher = dispatcher))

        val first = vm.state.first { s -> s != null && s.rows.none { it.status == RowStatus.Loading } }!!
        assertEquals(listOf("test/hearts.json", "test/echoes.json"), first.rows.map { it.dataFile })
        assertEquals(RowStatus.Unavailable, first.rows[1].status)
        val row = (first.rows[0].status as RowStatus.Ready).summary
        assertEquals(0, row.done)
        assertEquals(9, row.total)
        assertEquals("Cave under the rock, in Hyrule Field", row.next)

        vm.setSaveFile("file_2").join()
        val second = vm.state.first { s -> s?.saveFile?.id == "file_2" && s.rows[0].status is RowStatus.Ready }!!
        assertEquals(1, (second.rows[0].status as RowStatus.Ready).summary.done)
    }
}
