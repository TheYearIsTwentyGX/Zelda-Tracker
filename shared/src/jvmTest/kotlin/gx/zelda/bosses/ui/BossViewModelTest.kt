package gx.zelda.bosses.ui

import gx.zelda.bosses.data.BossRepository
import gx.zelda.bosses.data.BossStorage
import gx.zelda.bosses.model.BossData
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A missing or malformed dataset shows an error instead of crashing the app. */
@OptIn(ExperimentalCoroutinesApi::class)
class BossViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun storage() = BossStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = main.track(BossViewModel(storage()) { BossData(BossRepository.parse("""{"spawns": "not a list"}"""), error("unreached")) })
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = main.track(BossViewModel(storage()) { throw IllegalStateException("files/botw/bosses.json not found") })
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun goodDatasetDoesNotReportFailure() = runTest {
        val vm = main.track(BossViewModel(storage()) {
            BossData(
                BossRepository.parse("""{"coordinates":"","regions":[],"kinds":[],"spawns":[]}"""),
                CompendiumRepository.parse("""{"entries":[]}"""),
            )
        })
        assertFalse(vm.loadFailed.value)
    }
}
