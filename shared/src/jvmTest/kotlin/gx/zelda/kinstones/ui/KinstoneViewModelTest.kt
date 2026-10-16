package gx.zelda.kinstones.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.kinstones.data.KinstoneProgressProvider
import gx.zelda.kinstones.data.KinstoneRepository
import gx.zelda.kinstones.data.KinstoneStorage
import gx.zelda.kinstones.model.KinstoneProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class KinstoneViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun storage() = KinstoneStorage(createDataStore(Files.createTempDirectory("zgx").toString()), Games.MC)

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = KinstoneViewModel(storage()) { KinstoneRepository.parse("""{"fusions": "not a list"}""") }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun missingDatasetReportsLoadFailure() = runTest {
        val vm = KinstoneViewModel(storage()) { throw IllegalStateException("files/mc/kinstones.json not found") }
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun fusingGoesToTheSaveFileOnScreen() = runTest {
        val storage = storage()
        val vm = KinstoneViewModel(storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val first = vm.state.filterNotNull().first()
        assertFalse(vm.loadFailed.value)
        assertEquals(100, first.total)
        assertEquals(listOf(0, 0, 0), first.saveFiles.map { it.fused })

        vm.setFused("mayor_hagen", true)
        vm.state.filterNotNull().first { it.fused == 1 }

        vm.setSaveFile("file_2")
        val second = vm.state.filterNotNull().first { it.saveFile.id == "file_2" }
        assertEquals(0, second.fused)
        vm.setFused("statue_left", true)
        val now = vm.state.filterNotNull().first { it.gold.fused == 1 }
        assertEquals(listOf(1, 1, 0), now.saveFiles.map { it.fused })
        assertEquals(setOf("mayor_hagen"), storage.progress("file_1").first().fused)
        assertEquals(setOf("statue_left"), storage.progress("file_2").first().fused)
    }

    @Test
    fun hidingResultsIsSavedAndRevealingIsNot() = runTest {
        val storage = storage()
        val vm = KinstoneViewModel(storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.filterNotNull().first()

        vm.setHideResults(true)
        val hidden = vm.state.filterNotNull().first { it.prefs.hideResults }
        val hagen = hidden.catalog.fusion("mayor_hagen")!!
        assertTrue(hidden.isHidden(hagen))
        assertTrue(storage.prefs.first().hideResults)

        vm.reveal("mayor_hagen")
        assertFalse(vm.state.filterNotNull().first { "mayor_hagen" in it.filters.revealed }.isHidden(hagen))
        assertEquals(KinstoneProgress(), storage.progress("file_1").first(), "revealing isn't fusing")
    }

    @Test
    fun clearFiltersKeepsTheSearch() = runTest {
        val vm = KinstoneViewModel(storage())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.filterNotNull().first()
        vm.setQuery("smith")
        vm.setRegion("Hyrule Town")
        vm.setNotFusedOnly(true)
        vm.clearFilters()
        val filters = vm.state.filterNotNull().first().filters
        assertEquals(KinstoneFilters(query = "smith"), filters)
    }

    /** The overview row, for when MC gets a Progress Overview. */
    @Test
    fun progressProviderCountsFusionsAndNamesAPartner() = runTest {
        val storage = storage()
        val provider = KinstoneProgressProvider(storage)
        val empty = provider.summary(Games.MC.saveFiles.first()).first()
        assertEquals(0, empty.done)
        assertEquals(100, empty.total)
        assertEquals("Gold 0 / 9 · Hearts 0 / 10", empty.secondary)
        assertEquals("Fuse with Hurdy-Gurdy Man (Hyrule Town) · 100 fusions left", empty.next)

        storage.setFused("file_1", "hurdy_gurdy_man", true)
        storage.setFused("file_1", "statue_left", true)
        val some = provider.summary(Games.MC.saveFiles.first()).first()
        assertEquals(2, some.done)
        assertEquals("Gold 1 / 9 · Hearts 1 / 10", some.secondary)
        assertEquals("Fuse with Mayor Hagen (Hyrule Town) · 98 fusions left", some.next)
    }
}
