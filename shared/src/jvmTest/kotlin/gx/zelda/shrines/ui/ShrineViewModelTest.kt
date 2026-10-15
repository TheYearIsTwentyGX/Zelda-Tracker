package gx.zelda.shrines.ui

import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestSection
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.UpgradeKind
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShrineViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = ShrineStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.BOTW)

    private val catalogJson =
        """{"regions":["Great Plateau"],"upgrades":{"orbsPerUpgrade":4,"startingHearts":3,"maxHearts":30,""" +
            """"startingStaminaWheels":1,"maxStaminaVessels":10,"bossHearts":4},"shrines":[]}"""

    // A missing or malformed dataset shows an error instead of crashing the app.

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
        val vm = ShrineViewModel(storage()) { ShrineRepository.parse(catalogJson) }
        assertFalse(vm.loadFailed.value)
    }

    // The Spirit Orb card counts the Divine Beasts the Main Quest tracker has freed on the same file.

    @Test
    fun beastsFreedOnTheActiveFileAddHearts() = runTest {
        val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
        val shrines = ShrineStorage(dataStore, Games.BOTW)
        val mainQuest = MainQuestStorage(dataStore, Games.BOTW)
        shrines.setBought("normal_1", hearts = 27, stamina = 0)
        mainQuest.setDone("normal_1", MainQuestSection.DIVINE_BEASTS, "vah_ruta", true)
        mainQuest.setDone("normal_2", MainQuestSection.DIVINE_BEASTS, "vah_medoh", true)
        mainQuest.setDone("normal_2", MainQuestSection.DIVINE_BEASTS, "vah_naboris", true)
        mainQuest.setDone("normal_1", MainQuestSection.TOWERS, "great_plateau", true)

        val vm = ShrineViewModel(shrines, mainQuest) { ShrineRepository.parse(catalogJson) }
        val orbs = vm.state.first { it != null }!!.orbs
        assertEquals(1, orbs.beastHearts, "only Save File 1's beast; towers don't count")
        assertEquals(27, orbs.heartsBought, "the entered number is never changed")
        assertEquals(31, orbs.hearts)
        assertTrue(orbs.heartsOverMax)

        shrines.setActiveSaveFile("normal_2")
        assertEquals(2, vm.state.first { it?.saveFile?.id == "normal_2" }!!.orbs.beastHearts)

        mainQuest.setDone("normal_2", MainQuestSection.DIVINE_BEASTS, "vah_naboris", false)
        assertEquals(1, vm.state.first { it?.beastsFreed == 1 }!!.orbs.beastHearts)
        assertEquals(27, shrines.progress("normal_1").first().heartsBought)
    }

    @Test
    fun withoutTheMainQuestTrackerNoBeastsCount() = runTest {
        val vm = ShrineViewModel(storage()) { ShrineRepository.parse(catalogJson) }
        assertEquals(0, vm.state.first { it != null }!!.orbs.beastHearts)
    }

    // TotK: no Main Quest tracker, so boss hearts are entered by hand; Lightroots are tracked per file.

    @Test
    fun totkTracksLightrootsAndBossHeartsOnTheActiveFile() = runTest {
        val storage = ShrineStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.TOTK)
        val vm = ShrineViewModel(storage)
        val loaded = vm.state.first { it != null }!!
        assertTrue(loaded.bossHeartsByHand)
        assertEquals(152, loaded.orbs.total)

        vm.setLightrootState("kawumoro", DiscoveryState.COMPLETED)
        vm.setBossHearts(2)
        vm.setFreeUpgrade(UpgradeKind.STAMINA)
        val s = vm.state.first { it?.progress?.lightroots?.completed?.isNotEmpty() == true && it.progress.freeUpgrade != null }!!
        assertEquals(DiscoveryState.UNDISCOVERED, s.progress.state("oromuwak"), "the shrine above is untouched")
        assertEquals(2, s.orbs.beastHearts)
        assertEquals(1, s.orbs.staminaVessels)
        assertEquals(1, s.saveFiles.first { it.file.id == "file_1" }.lightrootsLit)
        assertEquals(0, s.saveFiles.first { it.file.id == "file_2" }.lightrootsLit)

        storage.setActiveSaveFile("file_2")
        val two = vm.state.first { it?.saveFile?.id == "file_2" }!!
        assertEquals(DiscoveryState.UNDISCOVERED, two.progress.lightroots.state("kawumoro"))
        assertEquals(0, two.orbs.beastHearts)
    }
}
