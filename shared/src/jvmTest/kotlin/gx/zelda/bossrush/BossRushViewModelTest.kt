package gx.zelda.bossrush

import gx.zelda.bossrush.data.BossRushStorage
import gx.zelda.bossrush.data.bossRushStoryGate
import gx.zelda.bossrush.model.RecordEntry
import gx.zelda.bossrush.ui.BossRushViewModel
import gx.zelda.bossrush.ui.RecordChange
import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.SaveFileSettingsStorage
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Lightning Round board's view model: entering, editing and deleting times, the best, undo, save files and each file's mode. */
@OptIn(ExperimentalCoroutinesApi::class)
class BossRushViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = BossRushStorage(dataStore, Games.SS)
    private val settings = SaveFileSettingsStorage(dataStore, Games.SS)
    private var day = 20_000L

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(gated: Boolean = false): BossRushViewModel {
        val gate = if (gated) bossRushStoryGate(dataStore, Games.SS) else { _: String -> flowOf(StoryGate.OFF) }
        val vm = main.track(BossRushViewModel(storage, settings, gate, today = { day }))
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.lastChange.collect {} }
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun records(file: String = "file_1") = storage.records(file).first()

    @Test
    fun aNormalFileShowsElevenBossesAndAHeroFileTwelve() = runTest {
        settings.update("file_2") { it.copy(heroMode = true) }
        val vm = viewModel()
        val one = vm.state.filterNotNull().first()
        assertFalse(one.heroMode)
        assertEquals(11, one.rows.size)
        assertTrue(one.rows.none { it.boss.id == "demise" })
        assertEquals("ghirahim_1", one.selected.boss.id, "the first boss to start with")
        assertEquals(listOf(11, 12, 11), one.saveFiles.map { it.slots })
        assertEquals(listOf(false, true, false), one.saveFiles.map { it.heroMode })

        vm.setSaveFile("file_2").join()
        val two = vm.state.filterNotNull().first { it.saveFile.id == "file_2" }
        assertTrue(two.heroMode, "the mode follows the file")
        assertEquals(12, two.rows.size)
        assertEquals("demise", two.rows.last().boss.id)

        vm.setSaveFile("file_1").join()
        assertEquals(11, vm.state.filterNotNull().first { it.saveFile.id == "file_1" }.rows.size)
    }

    @Test
    fun enteringATimeIsOneWriteDatedToday() = runTest {
        val vm = viewModel()
        val before = dataStore.writes
        vm.addTime("koloktos", 10_532)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(listOf(RecordEntry(1, 10_532, day)), records().of("koloktos"))
        val state = vm.state.filterNotNull().first { it.board.set == 1 }
        assertEquals(10_532, state.rows.single { it.boss.id == "koloktos" }.best?.time)
        assertEquals(listOf(1, 0, 0), state.saveFiles.map { it.set })
        val change = vm.lastChange.filterNotNull().first()
        assertEquals(RecordChange.Kind.ADDED, change.kind)
        assertFalse(change.newBest, "a first time isn't called a new best")
        assertTrue(records("file_2").entries.isEmpty())
    }

    @Test
    fun aFasterTimeIsTheNewBestAndASlowerOneIsHistory() = runTest {
        val vm = viewModel()
        vm.addTime("scaldera", 9_000)!!.join()
        day++
        vm.addTime("scaldera", 8_000)!!.join()
        assertTrue(vm.lastChange.filterNotNull().first { it.time == 8_000 }.newBest)
        day++
        vm.addTime("scaldera", 8_500)!!.join()
        assertFalse(vm.lastChange.filterNotNull().first { it.time == 8_500 }.newBest)
        val row = vm.state.filterNotNull().first { it.records.of("scaldera").size == 3 }.rows.single { it.boss.id == "scaldera" }
        assertEquals(RecordEntry(2, 8_000, 20_001), row.best)
        assertEquals(9_000, row.previousBest?.time)
        assertEquals(1_000, row.improvement)
        assertEquals(listOf(8_500, 8_000, 9_000), row.history.map { it.time }, "newest first")
    }

    @Test
    fun editingAndDeletingAreOneWriteEachAndUndoPutsItBack() = runTest {
        val vm = viewModel()
        vm.addTime("tentalus", 12_000)!!.join()
        vm.addTime("tentalus", 11_000)!!.join()
        vm.state.filterNotNull().first { it.records.of("tentalus").size == 2 }

        var before = dataStore.writes
        vm.editTime("tentalus", 2, 13_000)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(listOf(12_000, 13_000), records().of("tentalus").map { it.time })
        assertEquals(12_000, vm.state.filterNotNull().first { it.records.best("tentalus")?.time == 12_000 }.records.best("tentalus")?.time)
        val edit = vm.lastChange.filterNotNull().first { it.kind == RecordChange.Kind.EDITED }

        before = dataStore.writes
        vm.undo(edit)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(listOf(12_000, 11_000), records().of("tentalus").map { it.time })
        assertNull(vm.lastChange.value)
        assertNull(vm.undo(edit), "undone once")

        vm.state.filterNotNull().first { it.records.best("tentalus")?.time == 11_000 }
        before = dataStore.writes
        vm.deleteEntry("tentalus", 2)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(listOf(12_000), records().of("tentalus").map { it.time })
        val delete = vm.lastChange.filterNotNull().first { it.kind == RecordChange.Kind.DELETED }
        assertEquals(11_000, delete.time)
        vm.undo(delete)!!.join()
        assertEquals(listOf(12_000, 11_000), records().of("tentalus").map { it.time })

        vm.state.filterNotNull().first { it.records.of("tentalus").size == 2 }
        vm.deleteEntry("tentalus", 9)!!.join()
        assertEquals(listOf(12_000, 11_000), records().of("tentalus").map { it.time }, "no such entry: nothing changes")
    }

    @Test
    fun undoGoesToTheFileTheChangeWasMadeOn() = runTest {
        storage.swap("file_2") { it.withAdded("moldarach", 5_000, 1) }
        val vm = viewModel()
        vm.addTime("moldarach", 6_000)!!.join()
        val change = vm.lastChange.filterNotNull().first()
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" && it.records.hasRecord("moldarach") }
        vm.undo(change)!!.join()
        assertTrue(records("file_1").entries.isEmpty())
        assertEquals(listOf(5_000), records("file_2").of("moldarach").map { it.time })
    }

    @Test
    fun theHeroModeSwitchPersistsPerFileAndNeverDropsDemise() = runTest {
        val vm = viewModel()
        var before = dataStore.writes
        vm.setHeroMode(true)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertTrue(settings.settings("file_1").first().heroMode)
        assertFalse(settings.settings("file_2").first().heroMode)
        vm.state.filterNotNull().first { it.heroMode && it.rows.size == 12 }

        vm.select("demise")
        assertEquals("demise", vm.state.filterNotNull().first { it.selectedId == "demise" }.selected.boss.id)
        vm.addTime("demise", 30_000)!!.join()
        vm.state.filterNotNull().first { it.board.set == 1 }

        before = dataStore.writes
        vm.setHeroMode(false)!!.join()
        assertEquals(1, dataStore.writes - before, "only the setting is written")
        val normal = vm.state.filterNotNull().first { !it.heroMode }
        assertEquals(11, normal.rows.size)
        assertEquals(0, normal.board.set, "Demise's record isn't counted on a Normal file")
        assertEquals(listOf("demise"), normal.board.hidden.map { it.id }, "but it's kept, and the board says so")
        assertEquals("ghirahim_1", normal.selected.boss.id, "the hidden boss can't stay chosen")
        assertEquals(listOf(30_000), records().of("demise").map { it.time })

        vm.setHeroMode(true)!!.join()
        val back = vm.state.filterNotNull().first { it.heroMode }
        assertEquals(30_000, back.rows.single { it.boss.id == "demise" }.best?.time)
        assertEquals(1, back.board.set)
    }

    @Test
    fun aNewFileNeverShowsTheOldFilesRecordsOrMode() = runTest {
        settings.update("file_3") { it.copy(heroMode = true) }
        storage.swap("file_3") { it.withAdded("demise", 40_000, 1) }
        val vm = viewModel()
        vm.addTime("scaldera", 7_000)!!.join()
        vm.setSaveFile("file_3").join()
        val three = vm.state.filterNotNull().first { it.saveFile.id == "file_3" }
        assertTrue(three.heroMode)
        assertEquals(setOf("demise"), three.records.entries.keys)
        vm.addTime("koloktos", 9_000)!!.join()
        assertEquals(setOf("demise", "koloktos"), records("file_3").entries.keys)
        assertEquals(setOf("scaldera"), records("file_1").entries.keys)
    }

    /** With story gating on, a file before Lanayru's part shows the board locked; records entered stay and still count. */
    @Test
    fun theStoryGateLocksTheBoardButHidesNothing() = runTest {
        AppSettingsStorage(dataStore).setStoryGating(true)
        storage.swap("file_1") { it.withAdded("scaldera", 7_000, 1) }
        val vm = viewModel(gated = true)
        val locked = vm.state.filterNotNull().first { it.locked }
        assertEquals(listOf("Lanayru's part"), locked.lockedBy)
        assertEquals(1, locked.board.set)
        assertEquals(11, locked.rows.size)

        val story = ChecklistRepository.load("ss/main_quest.json")
        ChecklistStorage(dataStore, Games.SS, "ss/main_quest.json").setDone("file_1", story.item("hero_song_lanayru")!!, true)
        assertFalse(vm.state.filterNotNull().first { !it.locked }.locked)
    }

    @Test
    fun aBrokenDatasetSaysSo() = runTest {
        val vm = main.track(BossRushViewModel(storage, settings, loadData = { error("missing") }))
        backgroundScope.launch { vm.state.collect {} }
        vm.loadFailed.first { it }
        assertNull(vm.state.value)
        assertNull(vm.addTime("scaldera", 1))
    }
}
