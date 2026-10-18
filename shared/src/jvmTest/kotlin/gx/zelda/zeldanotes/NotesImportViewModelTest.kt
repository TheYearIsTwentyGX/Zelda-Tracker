package gx.zelda.zeldanotes

import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.PickedImage
import gx.zelda.core.PickedVideo
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.shrines.scan.decodeMapImage
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

/** The Zelda Notes import's flow on the golden captures: read, review against a chosen save file, apply. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesImportViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun storage() = KorokStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    private fun file(name: String) = File(javaClass.getResource("/zelda-notes/$name")!!.toURI())

    private fun picked(vararg names: String) = names.map { n -> PickedImage(n) { file(n).readBytes() } }

    @Test
    fun screenshotsAreReviewedAgainstThePickedSaveFileAndApplied() = runTest {
        val storage = storage()
        // R28 is already collected on file 2; R12 is marked collected there but Zelda Notes still draws it.
        storage.setState("normal_2", "R28", DiscoveryState.COMPLETED)
        storage.setState("normal_2", "R12", DiscoveryState.COMPLETED)
        val vm = main.track(NotesImportViewModel(storage))
        assertEquals("normal_1", vm.state.first { it != null }!!.target.id)

        vm.readImages(picked("shot-114500.jpg", "shot-114505.jpg", "categories.jpg"))!!.join()
        val onFile1 = assertNotNull(vm.state.value?.review)
        assertEquals(setOf("G11", "R28", "T26", "T34"), onFile1.toCollect.map { it.id }.toSet())
        assertEquals(listOf(true, false, false), vm.state.value!!.inputs.map { it.ok })

        // Pointing the import at file 2 rebuilds the review against its progress.
        vm.setTarget("normal_2")
        val review = vm.state.first { it?.target?.id == "normal_2" && it.review?.alreadyCollected == 1 }!!.review!!
        assertEquals(setOf("G11", "T26", "T34"), review.toCollect.map { it.id }.toSet())
        assertEquals(listOf("R12"), review.shownButCollectedHere.map { it.id })
        val st = vm.state.value!!
        assertEquals(setOf("G11", "T26", "T34"), st.selected)
        assertEquals(5, st.collectedAfterApply(review.total))
        assertEquals(1, st.percentAfterApply(review.total))

        vm.setSelected("T26", false)
        vm.apply()!!.join()
        assertEquals(2, vm.state.value!!.applied!!.first)
        assertEquals("normal_2", vm.state.value!!.applied!!.second.id)
        val progress = storage.progress("normal_2").first()
        assertTrue(progress.completed.containsAll(listOf("G11", "T34", "R28", "R12")))
        assertTrue("T26" !in progress.completed)
        // Nothing went to the active file.
        assertTrue(storage.progress("normal_1").first().completed.isEmpty())
    }

    @Test
    fun aRecordingIsReadFrameByFrame() = runTest {
        val vm = main.track(NotesImportViewModel(storage()))
        vm.state.first { it != null }
        val frames = File(javaClass.getResource("/zelda-notes/video")!!.toURI()).listFiles()!!.sortedBy { it.name }
        val video = PickedVideo("screen.mp4") { _, _, onFrame ->
            for ((i, f) in frames.withIndex()) onFrame(i, frames.size, decodeMapImage(f.readBytes())!!)
        }
        vm.readVideo(video)!!.join()
        val st = vm.state.value!!
        val input = st.inputs.single()
        assertTrue(input.ok && input.summary.startsWith("${frames.size - 2} of ${frames.size} frames placed"), input.summary)
        assertEquals(setOf("E08", "E14", "E21", "H30", "H70", "K28"), st.review!!.toCollect.map { it.id }.toSet())
        assertTrue(st.review!!.views.isNotEmpty())

        vm.startOver()
        assertEquals(null, vm.state.value!!.review)
        assertTrue(vm.state.value!!.inputs.isEmpty())
    }
}
