package gx.zelda.overview

import gx.zelda.bossrush.data.BossRushStorage
import gx.zelda.core.Games
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFileSettingsStorage
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

/** Skyward Sword's overview has a Lightning Round row that counts each file's slots in that file's mode. */
@OptIn(ExperimentalCoroutinesApi::class)
class BossRushOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val storage = BossRushStorage(dataStore, Games.SS)
    private val settings = SaveFileSettingsStorage(dataStore, Games.SS)

    private fun OverviewUiState?.records(): ProgressSummary? =
        (this?.rows?.singleOrNull { it.kind == TrackerKind.BOSS_RUSH }?.status as? RowStatus.Ready)?.summary

    @Test
    fun theRowFollowsEachFilesMode() = runTest {
        storage.swap("file_1") { it.withAdded("scaldera", 7_543, 1).withAdded("koloktos", 10_000, 1).withAdded("demise", 30_000, 1) }
        settings.update("file_2") { it.copy(heroMode = true) }
        storage.swap("file_2") { it.withAdded("demise", 30_000, 1) }
        val vm = main.track(OverviewViewModel(dataStore, Games.SS, loadDispatcher = dispatcher))

        val one = vm.state.first { it.records() != null }.records()!!
        assertEquals("Lightning Round Records", one.title)
        assertEquals(2 to 11, one.done to one.total, "Demise's record is hidden on a Normal file")
        assertEquals("Records set 2/11 · bests total 2:55.43", one.secondary)
        assertEquals("Ghirahim (1st battle)", one.next)

        vm.setSaveFile("file_2").join()
        val two = vm.state.first { s -> s?.saveFile?.id == "file_2" && s.records()?.total == 12 }.records()!!
        assertEquals("Records set 1/12 (Hero Mode) · bests total 5:00.00", two.secondary)

        settings.update("file_2") { it.copy(heroMode = false) }
        val normal = vm.state.first { it.records()?.total == 11 }.records()!!
        assertEquals("Records set 0/11", normal.secondary)
        assertEquals(listOf(30_000), storage.records("file_2").first().of("demise").map { it.time })
    }
}
