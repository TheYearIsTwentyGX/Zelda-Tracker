package gx.zelda.shrines.scan

import gx.zelda.core.Games
import gx.zelda.core.PickedImage
import gx.zelda.core.createDataStore
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.ShrineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scan screen's flow with the real partial-save screenshots: pick, review, apply. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShrineScanViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = ShrineStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    private fun sample(name: String) = PickedImage("$name.jpg") {
        javaClass.getResourceAsStream("/map-samples/$name.jpg")!!.readBytes()
    }

    /** A view model whose catalog, map data and save file have loaded. */
    private suspend fun viewModel(storage: ShrineStorage) =
        ShrineScanViewModel(storage, work = Dispatchers.Unconfined).also { vm -> vm.state.first { it != null } }

    /** Scans and waits for the review; DataStore reads finish on their own thread. */
    private suspend fun ShrineScanViewModel.scanAndWait(vararg images: PickedImage): ShrineScanUiState {
        val before = state.value!!.shots.size
        scan(images.toList())
        return state.first { it != null && it.scanning == null && it.shots.size == before + images.size }!!
    }

    @Test
    fun suggestsAndAppliesToTheActiveSaveFile() = runTest {
        val storage = storage()
        storage.setActiveSaveFile("normal_2")
        // Already completed here, though the map shows it unfinished: must stay completed.
        storage.setState("normal_2", "qua_raym", ShrineState.COMPLETED)
        val vm = viewModel(storage)
        vm.scanAndWait(sample("partial/1000002226"), sample("partial/1000002228"))

        val state = assertNotNull(vm.state.value)
        assertEquals("normal_2", state.saveFile.id)
        val review = assertNotNull(state.review)
        assertEquals(listOf("tena_kosah", "shae_mosah"), review.section(ReviewSection.NOT_DONE).map { it.shrine.id })
        assertEquals(listOf("qua_raym"), review.keptCompleted.map { it.id })
        assertEquals(41, review.section(ReviewSection.COMPLETED).size)
        assertTrue(review.section(ReviewSection.OFF_SCREEN).isEmpty())

        // The user also ticks one unsure shrine as done and leaves one suggestion out.
        vm.setChoice("chaas_qeta", ScanChoice.COMPLETED)
        vm.setChoice("oman_au", ScanChoice.LEAVE)
        assertEquals(41 - 1 + 2 + 1, vm.state.value!!.changes.size)
        vm.apply()
        assertEquals(43, vm.state.first { it?.applied != null }!!.applied)

        val progress = storage.progress("normal_2").first()
        assertEquals(ShrineState.FOUND, progress.state("tena_kosah"))
        assertEquals(ShrineState.COMPLETED, progress.state("qua_raym"))
        assertEquals(ShrineState.COMPLETED, progress.state("chaas_qeta"))
        assertEquals(ShrineState.COMPLETED, progress.state("ja_baij"))
        assertEquals(ShrineState.UNDISCOVERED, progress.state("oman_au"))
        // Not on the map is never written, and the other save file is untouched.
        assertEquals(ShrineState.UNDISCOVERED, progress.state("toto_sah"))
        assertTrue(storage.progress("normal_1").first().completed.isEmpty())
    }

    @Test
    fun unusableFilesAreReportedAndTheRestStillRead() = runTest {
        val vm = viewModel(storage())
        vm.scanAndWait(PickedImage("notes.txt") { "not an image".encodeToByteArray() }, sample("all-complete/1000002225"))
        val state = assertNotNull(vm.state.value)
        assertEquals(RejectReason.UNREADABLE, assertIs<ShotScan.Rejected>(state.shots[0].scan).reason)
        assertIs<ShotScan.Read>(state.shots[1].scan)
        // North only: the south half's shrines are listed as not in any screenshot.
        assertTrue(assertNotNull(state.review).section(ReviewSection.OFF_SCREEN).isNotEmpty())
    }

    @Test
    fun addingAScreenshotKeepsHandEdits() = runTest {
        val vm = viewModel(storage())
        vm.scanAndWait(sample("partial/1000002226"))
        vm.setChoice("tena_kosah", ScanChoice.LEAVE)
        vm.scanAndWait(sample("partial/1000002228"))
        val state = assertNotNull(vm.state.value)
        assertEquals(2, state.readShots.size)
        assertEquals(ScanChoice.LEAVE, state.selections["tena_kosah"])
        assertEquals(ScanChoice.FOUND, state.selections["shae_mosah"])
    }

    @Test
    fun hiddenUndiscoveredShrinesStayHiddenUntilRevealed() = runTest {
        val storage = storage()
        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        val vm = viewModel(storage)
        vm.scanAndWait(sample("partial/1000002226"))
        val state = assertNotNull(vm.state.value)
        val review = assertNotNull(state.review)
        val offScreen = review.section(ReviewSection.OFF_SCREEN).first()
        assertTrue(state.isHidden(offScreen))
        // A shrine the map shows is found in the game, so its name is no spoiler.
        assertTrue(review.section(ReviewSection.COMPLETED).none { state.isHidden(it) })
        vm.reveal(offScreen.shrine.id)
        assertTrue(!vm.state.value!!.isHidden(offScreen))
    }

    @Test
    fun startOverForgetsTheScreenshots() = runTest {
        val vm = viewModel(storage())
        vm.scanAndWait(sample("partial/1000002226"))
        vm.startOver()
        val state = assertNotNull(vm.state.value)
        assertTrue(state.shots.isEmpty())
        assertNull(state.review)
    }
}
