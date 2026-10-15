package gx.zelda.compendium.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.compendium.data.CompendiumStorage
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
class CompendiumViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = CompendiumStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = CompendiumViewModel(storage()) { CompendiumRepository.parse("""{"entries": "not a list"}""") }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = CompendiumViewModel(storage()) { throw IllegalStateException("files/botw/compendium.json not found") }
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = CompendiumViewModel(storage()) { CompendiumRepository.parse("""{"entries":[]}""") }
        assertFalse(vm.loadFailed.value)
    }
}
