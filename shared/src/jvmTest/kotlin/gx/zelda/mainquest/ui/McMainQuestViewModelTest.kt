package gx.zelda.mainquest.ui

import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.mainquest.data.MainQuestProgressProvider
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The Minish Cap's dungeons, items and Tiger Scrolls on the shared main quest screen state. */
@OptIn(ExperimentalCoroutinesApi::class)
class McMainQuestViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val storage = MainQuestStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.MC)

    @Test
    fun countsEachChecklistOnTheActiveSaveFile() = runTest {
        val vm = main.track(MainQuestViewModel(storage))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val loaded = vm.state.filterNotNull().first()
        assertFalse(vm.loadFailed.value)
        assertEquals(listOf(MainQuestSection.DUNGEONS, MainQuestSection.ITEMS, MainQuestSection.TIGER_SCROLLS), loaded.catalog.sections)
        assertEquals("file_1", loaded.saveFile.id)

        vm.setDone(MainQuestSection.DUNGEONS, "deepwood_shrine", true)!!.join()
        vm.setDone(MainQuestSection.DUNGEONS, "fortress_of_winds", true)!!.join()
        vm.setDone(MainQuestSection.ITEMS, "gust_jar", true)!!.join()
        vm.setDone(MainQuestSection.TIGER_SCROLLS, "spin_attack", true)!!.join()
        vm.setDone(MainQuestSection.TIGER_SCROLLS, "faster_split", true)!!.join()

        val state = vm.state.filterNotNull().first { it.progress.tigerScrolls.size == 2 }
        assertEquals(2, state.doneCount(MainQuestSection.DUNGEONS))
        assertEquals(1, state.elementCount, "the Fortress of Winds has no Element")
        assertEquals(1, state.doneCount(MainQuestSection.ITEMS))
        assertEquals(1, state.doneCount(MainQuestSection.TIGER_SCROLLS), "upgrades aren't scrolls")
        assertEquals(1, state.upgradeCount)
        assertEquals(
            mapOf(MainQuestSection.DUNGEONS to 2, MainQuestSection.ITEMS to 1, MainQuestSection.TIGER_SCROLLS to 1),
            state.saveFiles.single { it.file.id == "file_1" }.counts,
        )
        assertEquals(listOf("file_1", "file_2", "file_3"), state.saveFiles.map { it.file.id })
    }

    @Test
    fun editsFollowTheActiveSaveFile() = runTest {
        val vm = main.track(MainQuestViewModel(storage))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.filterNotNull().first()
        vm.setSaveFile("file_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "file_2" }
        vm.setDone(MainQuestSection.ITEMS, "flippers", true)!!.join()
        vm.state.filterNotNull().first { "flippers" in it.progress.items }

        assertEquals(setOf("flippers"), storage.progress("file_2").first().items)
        assertEquals(emptySet(), storage.progress("file_1").first().items)
    }

    @Test
    fun overviewRowAddsUpTheThreeChecklists() = runTest {
        val catalog = MainQuestRepository.load(Games.MC)
        storage.setDone("file_1", MainQuestSection.DUNGEONS, "deepwood_shrine", true)
        catalog.items.take(4).forEach { storage.setDone("file_1", MainQuestSection.ITEMS, it.id, true) }
        storage.setDone("file_1", MainQuestSection.TIGER_SCROLLS, "spin_attack", true)
        storage.setDone("file_1", MainQuestSection.TIGER_SCROLLS, "faster_spin", true)

        val summary = MainQuestProgressProvider(storage).summary(Games.MC.saveFile("file_1")).first()
        assertEquals(1 + 4 + 1, summary.done)
        assertEquals(6 + 38 + 8, summary.total)
        assertEquals("Dungeons 1 / 6 · Items 4 / 38 · Tiger Scrolls 1 / 8", summary.secondary)
        assertEquals("Clear Cave of Flames in Mount Crenel", summary.next)
        assertNull(summary.mapItems)
    }
}
