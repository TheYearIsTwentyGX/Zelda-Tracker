package gx.zelda.shrines.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
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
class ShrineViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = ShrineStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = ShrineViewModel(storage()) { ShrineRepository.parse("""{"regions": "not a list"}""") }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = ShrineViewModel(storage()) { throw IllegalStateException("files/botw/shrines.json not found") }
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = ShrineViewModel(storage()) {
            ShrineRepository.parse(
                """{"regions":["Great Plateau"],"upgrades":{"orbsPerUpgrade":4,"startingHearts":3,"maxHearts":30,""" +
                    """"startingStaminaWheels":1,"maxStaminaVessels":10,"bossHearts":4},"shrines":[]}""",
            )
        }
        assertFalse(vm.loadFailed.value)
    }
}
