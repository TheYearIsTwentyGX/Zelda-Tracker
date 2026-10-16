package gx.zelda.armor.ui

import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.armor.data.ArmorRepository
import gx.zelda.armor.data.ArmorStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A missing or malformed dataset shows an error instead of crashing the app. The load waits for
 * [ArmorStorage.migrateLegacy], a real DataStore write, so each test waits for the outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArmorViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun storage() = ArmorStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = main.track(ArmorViewModel(storage()) { ArmorRepository.parse("""{"pieces": "not a list"}""") })
        vm.loadFailed.first { it }
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = main.track(ArmorViewModel(storage()) { throw IllegalStateException("files/botw/armor.json not found") })
        vm.loadFailed.first { it }
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = main.track(ArmorViewModel(storage()) { ArmorRepository.parse("""{"materials":[],"sets":[],"pieces":[]}""") })
        vm.state.filterNotNull().first()
        assertFalse(vm.loadFailed.value)
    }
}
