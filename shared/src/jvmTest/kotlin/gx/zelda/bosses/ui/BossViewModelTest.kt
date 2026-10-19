package gx.zelda.bosses.ui

import gx.zelda.bosses.data.BossRepository
import gx.zelda.bosses.data.BossStorage
import gx.zelda.bosses.model.BossData
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
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

/** Loading (a missing or malformed dataset shows an error) and each save file's bosses. */
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

    /** The bundled data: the Gerudo Taluses reported as Rare are Luminous on Normal Mode, Rare on Master Mode. */
    @Test
    fun theMasterModeFileShowsItsRankedUpVariants() = runTest {
        val storage = storage()
        // A mark only the Master Mode file has: its state shows the switch has reached that file's
        // progress, not just the file. Writing before then races the progress collector subscribing.
        val marker = "not_a_spawn"
        storage.setState("master", marker, DiscoveryState.FOUND)
        val vm = main.track(BossViewModel(storage))
        backgroundScope.launch { vm.state.collect {} }
        val normal = vm.state.filterNotNull().first()
        val rutimala = "3900382371"
        assertEquals("stone_talus_luminous", normal.spawns.first { it.id == rutimala }.variant)

        vm.setSaveFile("master").join()
        val master = vm.state.filterNotNull().first { it.saveFile.masterMode && it.progress.isFound(marker) }
        val talus = master.spawns.first { it.id == rutimala }
        assertEquals("Stone Talus (Rare)", master.data.variantName(talus.variant))
        assertEquals("stone_talus_luminous", talus.rankedUpFrom)
        assertEquals(19, master.pages.first { it.kind.id == "talus" }.spawns.count { it.variant == "stone_talus_rare" })

        // Progress is kept under the same id whatever the mode.
        vm.setState(rutimala, DiscoveryState.COMPLETED)!!.join()
        assertTrue(storage.progress("master").first { it.defeated.isNotEmpty() }.isDefeated(rutimala))
        val after = vm.state.filterNotNull().first { it.progress.isDefeated(rutimala) }
        assertEquals(listOf(0, 1, 0, 0), after.pages.map { it.tally.defeated })
        assertEquals(mapOf("normal_1" to 0, "normal_2" to 0, "master" to 1), after.saveFiles.associate { it.file.id to it.defeated })
    }
}
