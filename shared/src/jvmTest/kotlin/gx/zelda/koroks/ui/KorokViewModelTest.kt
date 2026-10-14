package gx.zelda.koroks.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.KorokData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A missing or malformed dataset shows an error instead of crashing the app. */
@OptIn(ExperimentalCoroutinesApi::class)
class KorokViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = KorokStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = KorokViewModel(storage()) { KorokData(KorokRepository.parse("""{"regions": "not a list"}"""), error("unreached")) }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = KorokViewModel(storage()) { throw IllegalStateException("files/botw/koroks.json not found") }
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = KorokViewModel(storage()) {
            KorokData(
                KorokRepository.parse("""{"coordinates":"","regions":[],"hestu":{"pouches":[]},"koroks":[]}"""),
                KorokRepository.parsePuzzleTypes("""{"groups":[],"types":[]}"""),
            )
        }
        assertFalse(vm.loadFailed.value)
    }
}
