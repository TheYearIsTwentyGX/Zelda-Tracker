package gx.zelda.armor.ui

import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.armor.model.ArmorPiece
import gx.zelda.armor.model.ArmorSet
import gx.zelda.armor.model.MaterialCost
import gx.zelda.armor.model.PieceProgress
import gx.zelda.armor.model.Slot
import gx.zelda.armor.model.Source
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mark all collected and clear all on the armor list: per set, the DLC and amiibo filters, levels and the undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArmorBulkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val storage = ArmorStorage(dataStore, Games.BOTW)

    private val upgrades = List(4) { listOf(MaterialCost("amber", 1)) }

    private fun piece(id: String, set: String?, slot: Slot, source: Source = Source.BASE) =
        ArmorPiece(id, id, set, slot, source, defense = List(5) { it }, upgrades = upgrades)

    private val catalog = ArmorCatalog(
        materials = emptyList(),
        sets = listOf(
            ArmorSet("zora", "Zora Set", null, Source.BASE),
            ArmorSet("phantom", "Phantom Set", null, Source.DLC1),
        ),
        pieces = listOf(
            piece("zora_helm", "zora", Slot.HEAD),
            piece("zora_armor", "zora", Slot.BODY),
            piece("phantom_helm", "phantom", Slot.HEAD, Source.DLC1),
            piece("phantom_armor", "phantom", Slot.BODY, Source.DLC1),
            piece("hylian_hood", null, Slot.HEAD),
            piece("midna_helm", null, Slot.HEAD, Source.AMIIBO),
        ),
    )

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): ArmorViewModel {
        val vm = main.track(ArmorViewModel(storage) { catalog })
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun pieces(file: String = "normal_1") = storage.pieces(file).first()

    private suspend fun owned(file: String = "normal_1") = pieces(file).filterValues { it.owned }.keys

    @Test
    fun theListCollectsEveryPieceInOneWriteAndLeavesLevelsAlone() = runTest {
        storage.updatePieces("normal_1") { mapOf("zora_helm" to PieceProgress(owned = true, level = 2)) }
        val vm = viewModel()

        val before = dataStore.writes
        vm.setAllOwned(null, owned = true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(catalog.pieces.map { it.id }.toSet(), owned())
        assertEquals(2, pieces()["zora_helm"]!!.level)
        assertEquals(0, pieces()["zora_armor"]!!.level, "collected, not upgraded")
        assertEquals("Marked 5 pieces complete on Save File 1", vm.bulkChange.first { it != null }!!.message)

        val done = vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        assertTrue(done.bulkScope().note!!.contains("Upgrade levels aren't changed"))
        vm.setAllOwned(null, owned = false)!!.join()
        assertEquals(emptySet(), owned())
        assertEquals(PieceProgress(owned = false, level = 2), pieces()["zora_helm"], "the level stays, as unticking a row leaves it")
    }

    @Test
    fun aSetHeaderCollectsOnlyItsPieces() = runTest {
        val vm = viewModel()
        assertEquals("Zora Set", vm.state.value!!.bulkScope("zora").label)
        vm.setAllOwned("zora", owned = true)!!.join()
        assertEquals(setOf("zora_helm", "zora_armor"), owned())

        vm.setAllOwned("_singles", owned = true)!!.join()
        assertEquals(setOf("zora_helm", "zora_armor", "hylian_hood", "midna_helm"), owned())
    }

    @Test
    fun hiddenDlcAndAmiiboArmorIsLeftAlone() = runTest {
        storage.updatePieces("normal_1") { mapOf("phantom_helm" to PieceProgress(owned = true)) }
        val vm = viewModel()
        vm.setShowDlc(false).join()
        vm.setShowAmiibo(false).join()
        val state = vm.state.filterNotNull().first { !it.progress.showDlc && !it.progress.showAmiibo }
        assertTrue(state.bulkScope().note!!.contains("filters hide"))
        assertEquals("Armor", state.bulkScope().label)

        vm.setAllOwned(null, owned = true)!!.join()
        assertEquals(setOf("zora_helm", "zora_armor", "hylian_hood", "phantom_helm"), owned())
        vm.state.filterNotNull().first { it.bulkScope().action == BulkAction.CLEAR }
        vm.setAllOwned(null, owned = false)!!.join()
        assertEquals(setOf("phantom_helm"), owned(), "a hidden DLC piece stays collected")
    }

    @Test
    fun aFilterNarrowsItToThePiecesShown() = runTest {
        storage.updatePieces("normal_1") { mapOf("zora_helm" to PieceProgress(owned = true)) }
        val vm = viewModel()
        vm.setQuery("helm")
        vm.state.filterNotNull().first { it.query.isNotEmpty() }
        assertEquals("Mark 2 shown complete", vm.state.value!!.bulkScope().actionLabel)
        vm.setAllOwned(null, owned = true)!!.join()
        assertEquals(setOf("zora_helm", "phantom_helm", "midna_helm"), owned())

        vm.setQuery("")
        vm.setOwnershipFilter(OwnershipFilter.OWNED)
        vm.state.filterNotNull().first { it.query.isEmpty() && it.ownershipFilter == OwnershipFilter.OWNED }
        vm.setAllOwned("zora", owned = false)!!.join()
        assertEquals(setOf("phantom_helm", "midna_helm"), owned())
    }

    @Test
    fun undoPutsBackExactlyWhatWasThere() = runTest {
        val prior = mapOf("zora_helm" to PieceProgress(owned = true, level = 3), "zora_armor" to PieceProgress(owned = false, level = 1))
        storage.updatePieces("normal_1") { prior }
        val vm = viewModel()

        vm.setAllOwned("zora", owned = true)!!.join()
        val change = vm.bulkChange.first { it != null }!!
        vm.setOwned("hylian_hood", true)!!.join()
        vm.undoBulkChange(change)!!.join()
        assertEquals(prior + ("hylian_hood" to PieceProgress(owned = true)), pieces(), "the set back, the later edit kept")
        assertNull(vm.undoBulkChange(change), "an undo only works once")
    }

    @Test
    fun onlyTheActiveFileChangesAndUndoGoesBackToIt() = runTest {
        // A mark only Save File 2 has, so the switch can be waited for by its progress.
        storage.updatePieces("normal_2") { mapOf("midna_helm" to PieceProgress(owned = true)) }
        val vm = viewModel()
        vm.setSaveFile("normal_2").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_2" && it.progress.of("midna_helm").owned }

        vm.setAllOwned("phantom", owned = true)!!.join()
        assertEquals(setOf("midna_helm", "phantom_helm", "phantom_armor"), owned("normal_2"))
        assertEquals(emptyMap(), pieces("normal_1"))
        val change = vm.bulkChange.first { it != null }!!
        assertEquals("Marked 2 pieces complete on Save File 2", change.message)

        vm.setSaveFile("normal_1").join()
        vm.state.filterNotNull().first { it.saveFile.id == "normal_1" }
        vm.undoBulkChange(change)!!.join()
        assertEquals(mapOf("midna_helm" to PieceProgress(owned = true)), pieces("normal_2"))
        assertEquals(emptyMap(), pieces("normal_1"))
    }

    /** [ArmorViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: ArmorViewModel) = launch { vm.state.collect {} }
}
