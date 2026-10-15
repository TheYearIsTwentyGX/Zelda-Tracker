package gx.zelda.mainquest.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
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
class MainQuestViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = MainQuestStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = MainQuestViewModel(storage()) { MainQuestRepository.parse("""{"towers": "not a list"}""") }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = MainQuestViewModel(storage()) { throw IllegalStateException("files/botw/main_quest.json not found") }
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = MainQuestViewModel(storage()) { MainQuestRepository.parse("""{"towers":[],"divineBeasts":[],"memories":[]}""") }
        assertFalse(vm.loadFailed.value)
    }
}
