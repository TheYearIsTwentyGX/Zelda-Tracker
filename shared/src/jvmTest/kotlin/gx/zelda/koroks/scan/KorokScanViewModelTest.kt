package gx.zelda.koroks.scan

import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.screencam.live.CameraAccess
import gx.zelda.screencam.live.FileFrameRecorder
import gx.zelda.screencam.live.decodeGrayImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The live scan screen's flow, fed the handheld clip's frames as if from the camera. */
@OptIn(ExperimentalCoroutinesApi::class)
class KorokScanViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun storage() = KorokStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    private val frames by lazy {
        val dir = File(javaClass.getResource("/korok-clips/handheld-hebra-ridgeland/frames")!!.toURI())
        dir.listFiles()!!.sortedBy { it.name }.map { decodeGrayImage(it.readBytes())!! }
    }

    @Test
    fun scanSuggestsTheMarkedKoroksAndAppliesWithoutDowngrading() = runTest {
        val storage = storage()
        storage.setActiveSaveFile("normal_2")
        // Already collected here: stays collected, and isn't suggested again.
        storage.setState("normal_2", "R34", DiscoveryState.COMPLETED)
        val vm = main.track(KorokScanViewModel(storage))
        vm.state.first { it != null }
        for (f in frames) vm.analyse(f)

        val live = assertNotNull(vm.state.value)
        assertTrue(live.found >= 12, "found ${live.found}")
        assertTrue(live.live.stats.startsWith("fps"), live.live.stats)

        vm.finish()
        val review = assertNotNull(vm.state.value?.review)
        val suggested = review.toCollect.map { it.id }.toSet()
        assertEquals(setOf("H25", "H42", "H54", "H65", "H73", "T10", "T17", "T36", "R33", "R35", "R36"), suggested)
        assertEquals(1, review.alreadyCollected)
        assertEquals(suggested, vm.state.value!!.selected)

        // The user leaves one out and adds an unsure one they know about.
        vm.setSelected("T17", false)
        val unsure = review.unsure.first().id
        vm.setSelected(unsure, true)
        vm.apply()!!.join()
        assertEquals(11, vm.state.value!!.applied)

        val progress = storage.progress("normal_2").first()
        assertTrue(progress.completed.containsAll(suggested - "T17" + unsure + "R34"))
        assertTrue("T17" !in progress.completed)
        // The other save file is untouched.
        assertTrue(storage.progress("normal_1").first().completed.isEmpty())
    }

    @Test
    fun aDebugSessionSavesFramesAndState() = runTest {
        val vm = main.track(KorokScanViewModel(storage()))
        vm.state.first { it != null }
        val dir = Files.createTempDirectory("korok-debug").toFile()
        vm.cameraInfo("analysis 1280x720 rotation 90")
        vm.startRecording { FileFrameRecorder(dir) }
        assertEquals(dir.absolutePath, vm.state.value!!.recording)
        for (f in frames.take(7)) vm.analyse(f)
        vm.stopRecording()
        assertTrue(File(dir, "frame-0000.jpg").exists() && File(dir, "frame-0005.jpg").exists() && !File(dir, "frame-0001.jpg").exists())
        val lines = File(dir, "state.jsonl").readLines()
        assertEquals(7, lines.size)
        assertTrue(lines[0].contains("\"phase\"") && lines[0].contains("\"saved\":\"frame-0000.jpg\""), lines[0])
        assertTrue(File(dir, "session.json").readText().contains("rotation 90"))
        assertEquals(null, vm.state.value!!.recording)
    }

    @Test
    fun cameraAccessFollowsAndroidsSignals() {
        assertEquals(CameraAccess.GRANTED, CameraAccess.of(granted = true, asked = false, showRationale = false))
        assertEquals(CameraAccess.NOT_ASKED, CameraAccess.of(granted = false, asked = false, showRationale = false))
        assertEquals(CameraAccess.DENIED, CameraAccess.of(granted = false, asked = true, showRationale = true))
        assertEquals(CameraAccess.DENIED_PERMANENTLY, CameraAccess.of(granted = false, asked = true, showRationale = false))
    }
}
