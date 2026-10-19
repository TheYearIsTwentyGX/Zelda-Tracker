package gx.zelda.compendium.ui

import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.compendium.model.EntrySource
import gx.zelda.core.BulkAction
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mark all photographed and clear all on the Compendium: per tab and header, Master Mode's entries, and the undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompendiumBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = CompendiumStorage(dataStore, Games.BOTW)

    private val catalog = CompendiumCatalog(
        listOf(
            CompendiumEntry("horse", 1, 1, "Horse", CompendiumCategory.CREATURES),
            CompendiumEntry("hyrule_bass", 2, 2, "Hyrule Bass", CompendiumCategory.CREATURES, food = true),
            CompendiumEntry("sky_octorok", null, 3, "Sky Octorok", CompendiumCategory.MONSTERS, source = EntrySource.MASTER_MODE),
            CompendiumEntry("lynel", 3, 4, "Lynel", CompendiumCategory.MONSTERS, locations = listOf("Hyrule Field")),
            CompendiumEntry("moblin", 4, 5, "Moblin", CompendiumCategory.MONSTERS, locations = listOf("Akkala")),
            CompendiumEntry("apple", 5, 6, "Apple", CompendiumCategory.MATERIALS, locations = listOf("Hyrule Field")),
        ),
    )

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): CompendiumViewModel {
        val vm = main.track(CompendiumViewModel(storage) { catalog })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun CompendiumViewModel.showCategory(category: CompendiumCategory?) {
        setCategory(category)
        state.filterNotNull().first { it.category == category }
    }

    private suspend fun photographed(file: String = "normal_1") = storage.photographed(file).first()

    @Test
    fun aTabMarksItsEntriesInOneWriteThenClearsThem() = runTest {
        storage.setPhotographed("normal_1", "lynel", true)
        storage.setPhotographed("normal_1", "apple", true)
        val vm = viewModel()
        vm.showCategory(CompendiumCategory.MONSTERS)

        val before = dataStore.writes
        vm.setAllPhotographed(null, photographed = true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(setOf("lynel", "moblin", "apple"), photographed(), "Master Mode's Sky Octorok isn't on a Normal Mode file")
        assertEquals("Marked 1 entry complete on Save File 1", vm.bulkChange.first { it != null }!!.message)

        vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        vm.setAllPhotographed(null, photographed = false)!!.join()
        assertEquals(setOf("apple"), photographed(), "another tab's entry stays")
        assertEquals("Cleared 2 entries on Save File 1", vm.bulkChange.first { it?.action == BulkAction.CLEAR }!!.message)
    }

    @Test
    fun aHeaderMarksOnlyItsEntries() = runTest {
        val vm = viewModel()
        assertEquals("Creatures · Food", vm.state.value!!.bulkScope("food").label)
        vm.setAllPhotographed("food", photographed = true)!!.join()
        assertEquals(setOf("hyrule_bass"), photographed())

        vm.showCategory(CompendiumCategory.CREATURES)
        vm.setAllPhotographed("creatures", photographed = true)!!.join()
        assertEquals(setOf("hyrule_bass", "horse"), photographed())
    }

    @Test
    fun theAllTabMarksTheFilesOwnEntries() = runTest {
        val vm = viewModel()
        val scope = vm.state.value!!.bulkScope()
        assertEquals("Everything", scope.label)
        assertTrue(scope.note!!.contains("isn't in Master Mode"))
        vm.setAllPhotographed(null, photographed = true)!!.join()
        assertEquals(5, photographed().size)
        assertFalse("sky_octorok" in photographed())
        assertNull(vm.state.value!!.bulkScope("materials").note, "no Master Mode entries there to mention")
    }

    @Test
    fun aMasterModeFileTakesInItsExtraEntries() = runTest {
        // A mark only the Master Mode file has, so the switch can be waited for by its progress.
        storage.setPhotographed("master", "horse", true)
        val vm = viewModel()
        vm.setSaveFile("master").join()
        vm.state.filterNotNull().first { it.saveFile.id == "master" && "horse" in it.photographed }
        vm.showCategory(CompendiumCategory.MONSTERS)

        val scope = vm.state.value!!.bulkScope()
        assertEquals(3, scope.total)
        assertTrue(scope.note!!.contains("a Master Mode one"))
        vm.setAllPhotographed(null, photographed = true)!!.join()
        assertEquals(setOf("horse", "sky_octorok", "lynel", "moblin"), photographed("master"))
        assertEquals(emptySet(), photographed("normal_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 3 entries complete on Master Mode", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" }
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf("horse"), photographed("master"))
        assertEquals(emptySet(), photographed("normal_1"))
    }

    @Test
    fun aFilterNarrowsItToTheEntriesShown() = runTest {
        val vm = viewModel()
        vm.setQuery("hyrule field")
        vm.state.filterNotNull().first { it.query.isNotEmpty() }
        assertEquals("Mark 2 shown complete", vm.state.value!!.bulkScope().actionLabel)
        vm.setAllPhotographed(null, photographed = true)!!.join()
        assertEquals(setOf("lynel", "apple"), photographed())

        vm.setQuery("")
        vm.setMissingOnly(true)
        vm.state.filterNotNull().first { it.query.isEmpty() && it.missingOnly }
        vm.setAllPhotographed("monsters", photographed = true)!!.join()
        assertEquals(setOf("lynel", "moblin", "apple"), photographed())
    }

    @Test
    fun undoPutsBackExactlyWhatWasThere() = runTest {
        storage.setPhotographed("normal_1", "lynel", true)
        val vm = viewModel()
        vm.setAllPhotographed(null, photographed = true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.undoBulkChange(change)!!.join()
        assertEquals(setOf("lynel"), photographed())
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun theDialogNeverNamesAnEntry() = runTest {
        val vm = viewModel()
        val state = vm.state.value!!
        for (scope in listOf(state.bulkScope(), state.bulkScope("monsters"), state.bulkScope("food"))) {
            val text = scope.confirmTitle + scope.confirmText("Save File 1") + scope.contentDescription
            catalog.entries.forEach { assertFalse(it.name in text, "${it.name} in: $text") }
        }
    }

    /** [CompendiumViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: CompendiumViewModel) = launch { vm.state.collect {} }
}
