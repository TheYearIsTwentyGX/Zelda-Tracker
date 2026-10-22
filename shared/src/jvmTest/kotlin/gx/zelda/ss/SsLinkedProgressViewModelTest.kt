package gx.zelda.ss

import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.ui.ChecklistUiState
import gx.zelda.checklist.ui.ChecklistViewModel
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.ui.UpgradeViewModel
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

/**
 * The user's report, on the real data: "the wallets still don't sync over; some of them are rewards
 * from Batreaux". Ticking a Batreaux wallet reward shows the Wallet tier in Items & Equipment, and the
 * other way round, on both screens at once; and the Hylian Shield, linked to Item Upgrades by #79,
 * stays linked now that the Lightning Round holds it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SsLinkedProgressViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))

    private suspend fun TestScope.checklist(file: String): ChecklistViewModel {
        val vm = main.track(ChecklistViewModel(ChecklistStorage(dataStore, Games.SS, "ss/$file.json")))
        backgroundScope.launch { vm.state.collect {} }
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun ChecklistViewModel.whenState(predicate: (ChecklistUiState) -> Boolean) = state.filterNotNull().first(predicate)

    private fun ChecklistUiState.level(id: String) = progress.level(catalog.item(id)!!)

    private fun ChecklistUiState.done(id: String) = progress.isDone(catalog.item(id)!!)

    private fun ChecklistUiState.batreaux() = listOf(5, 10, 30, 40, 50, 70, 80).filter { done("batreaux_$it") }

    @Test
    fun aBatreauxWalletRewardShowsAsTheWalletTier() = runTest {
        val crystals = checklist("gratitude_crystals")
        val items = checklist("items")

        val before = dataStore.writes
        crystals.setDone("batreaux_30", true)!!.join()
        assertEquals(1, dataStore.writes - before, "one write")

        val state = items.whenState { it.level("wallet") == 2 }
        assertEquals("Big Wallet", state.catalog.item("wallet")!!.tiers[1].name)
        assertEquals("Rupees you can carry 1000 / 9900", state.summaries.single { it.label == "Rupees you can carry" }.text)
        // The Medium Wallet came first, so its reward shows too; the Heart Piece between them isn't a wallet.
        assertEquals(listOf(5, 30), crystals.whenState { it.done("batreaux_30") }.batreaux())
    }

    @Test
    fun theWalletTierShowsAsBatreauxsRewards() = runTest {
        val crystals = checklist("gratitude_crystals")
        val items = checklist("items")

        items.setLevel("wallet", 4)!!.join()
        assertEquals(listOf(5, 30, 50, 80), crystals.whenState { it.done("batreaux_80") }.batreaux(), "the Gold Rupees aren't wallets")

        items.setLevel("wallet", 2)!!.join()
        assertEquals(listOf(5, 30), crystals.whenState { !it.done("batreaux_50") }.batreaux())
    }

    @Test
    fun eachSaveFileKeepsItsOwnWallet() = runTest {
        val crystals = checklist("gratitude_crystals")
        val items = checklist("items")
        crystals.setDone("batreaux_50", true)!!.join()
        items.whenState { it.level("wallet") == 3 }

        // The active file is the game's, so both screens switch.
        crystals.setSaveFile("file_2").join()
        crystals.whenState { it.saveFile.id == "file_2" && it.batreaux().isEmpty() }
        items.whenState { it.saveFile.id == "file_2" && it.level("wallet") == 0 }

        crystals.setDone("batreaux_5", true)!!.join()
        items.whenState { it.saveFile.id == "file_2" && it.level("wallet") == 1 }
        assertEquals(3, ChecklistStorage(dataStore, Games.SS, "ss/items.json").progress("file_1").first().levels["wallet"])
    }

    /** A Piece of Heart in a Goddess Chest is the cube's second tier: one tick shows on both lists. */
    @Test
    fun aGoddessChestPieceIsItsCubesChest() = runTest {
        val hearts = checklist("heart_pieces")
        val cubes = checklist("goddess_cubes")

        hearts.setDone("goddess_chest_bug_rock", true)!!.join()
        assertEquals(2, cubes.whenState { it.level("cube_summit_waterfall") == 2 }.level("cube_summit_waterfall"))

        cubes.setLevel("cube_summit_waterfall", 1)!!.join()
        hearts.whenState { !it.done("goddess_chest_bug_rock") }
    }

    @Test
    fun theHylianShieldStaysLinkedToItemUpgrades() = runTest {
        val shields = checklist("shields")
        val sideQuests = checklist("side_quests")
        val hearts = checklist("heart_pieces")
        val upgrades = main.track(UpgradeViewModel(UpgradeStorage(dataStore, Games.SS)))
        backgroundScope.launch { upgrades.state.collect {} }
        upgrades.state.filterNotNull().first()

        shields.setDone("hylian_shield", true)!!.join()
        sideQuests.whenState { it.level("thunder_dragon_boss_rush") == 2 }
        hearts.whenState { it.done("lightning_round_four_wins") }
        upgrades.state.filterNotNull().first { it.progress.of("hylian_shield").owned }

        upgrades.setOwned("hylian_shield", false)!!.join()
        shields.whenState { !it.done("hylian_shield") }
        assertEquals(1, sideQuests.whenState { it.level("thunder_dragon_boss_rush") == 1 }.level("thunder_dragon_boss_rush"), "the 4-win Piece of Heart stays")

        sideQuests.setLevel("thunder_dragon_boss_rush", 3)!!.join()
        upgrades.state.filterNotNull().first { it.progress.of("hylian_shield").owned }
        shields.whenState { it.done("hylian_shield") }
    }
}
