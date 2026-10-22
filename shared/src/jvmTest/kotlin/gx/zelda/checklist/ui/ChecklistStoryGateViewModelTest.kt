package gx.zelda.checklist.ui

import gx.zelda.checklist.ChecklistTestGame
import gx.zelda.checklist.StoryFixture
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.data.StoryProgress
import gx.zelda.checklist.data.storyFileOf
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.Game
import gx.zelda.core.ReleaseDate
import gx.zelda.core.SaveFile
import gx.zelda.core.TestMain
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind
import gx.zelda.core.createDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Settings toggle and the Main Quest's progress, live, on the save file on screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistStoryGateViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val game = Game(
        id = "story",
        title = "Story Game",
        released = ReleaseDate(2030, 1, 1),
        platform = "Test",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", ""),
            Tracker(TrackerKind.CHECKLIST, "Main Quest", "", dataFile = "story/main_quest.json"),
            Tracker(TrackerKind.CHECKLIST, "Hearts", "", dataFile = "story/hearts.json"),
        ),
    )

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val settings = AppSettingsStorage(dataStore)
    private val story = ChecklistStorage(dataStore, game, "story/main_quest.json")
    private val hearts = ChecklistStorage(dataStore, game, "story/hearts.json")

    private fun viewModel() = main.track(
        ChecklistViewModel(
            hearts,
            loadCatalog = { StoryFixture.gated },
            storyGate = StoryProgress(dataStore, game, "story/hearts.json") { StoryFixture.story }::gate,
        ),
    )

    @Test
    fun theToggleAndTheActiveFilesMainQuestHideAndShowRowsLive() = runTest {
        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        val forestPiece = StoryFixture.gated.item("forest_piece")!!
        // Let the state load before writing (see ChecklistViewModelTest).
        assertFalse(vm.state.filterNotNull().first().isLocked(forestPiece), "off by default")

        settings.setStoryGating(true)
        vm.state.filterNotNull().first { it.isLocked(forestPiece) }

        story.setDone("file_1", StoryFixture.story.item("forest")!!, true)
        val unlocked = vm.state.filterNotNull().first { !it.isLocked(forestPiece) }
        assertEquals("Forest Temple", unlocked.gate.stepNames["forest"])

        // File 2 hasn't reached the forest: its own Main Quest gates it.
        vm.setSaveFile("file_2").join()
        assertTrue(vm.state.filterNotNull().first { it.saveFile.id == "file_2" }.isLocked(forestPiece))

        settings.setStoryGating(false)
        vm.state.filterNotNull().first { !it.isLocked(forestPiece) }
    }

    @Test
    fun markAllWithGatingOnWritesOnlyWhatsUnlocked() = runTest {
        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        settings.setStoryGating(true)
        vm.state.filterNotNull().first { it.gate.enabled }

        vm.setAllDone(null, true)!!.join()
        assertEquals(setOf("start", "next_to_it", "typo"), hearts.progress("file_1").first().levels.keys)
    }

    @Test
    fun theMainQuestAndGamesWithoutOneAreNeverGated() = runTest {
        settings.setStoryGating(true)
        assertEquals("story/main_quest.json", storyFileOf(game))
        assertNull(storyFileOf(ChecklistTestGame))
        assertEquals(StoryGate.OFF, StoryProgress(dataStore, game, "story/main_quest.json") { StoryFixture.story }.gate("file_1").first())
        assertEquals(StoryGate.OFF, StoryProgress(dataStore, ChecklistTestGame, "test/hearts.json").gate("file_1").first())
        assertTrue(StoryProgress(dataStore, game, "story/hearts.json") { StoryFixture.story }.gate("file_1").first().enabled)
    }

    /** [ChecklistViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: ChecklistViewModel) = launch { vm.state.collect {} }
}
