package gx.zelda.upgrades.ui

import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.ui.ChecklistViewModel
import gx.zelda.core.BulkAction
import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.data.UpgradeStorage
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

/**
 * The user's case, on the real data: upgrading the Beetle to the Quick Beetle in Items & Equipment
 * shows in Item Upgrades, and the other way round, on both screens at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedProgressViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())

    private suspend fun TestScope.upgradeViewModel(): UpgradeViewModel {
        val vm = main.track(UpgradeViewModel(UpgradeStorage(dataStore, Games.SS)))
        backgroundScope.launch { vm.state.collect {} }
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun TestScope.checklistViewModel(dataFile: String): ChecklistViewModel {
        val vm = main.track(ChecklistViewModel(ChecklistStorage(dataStore, Games.SS, dataFile)))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    @Test
    fun theQuickBeetleInItemsAndEquipmentShowsInItemUpgrades() = runTest {
        val items = checklistViewModel("ss/items.json")
        val upgrades = upgradeViewModel()

        items.setLevel("beetle", 3)!!.join()
        val state = upgrades.state.filterNotNull().first { it.progress.of("beetle").owned }
        val beetle = state.visible.single { it.key == "beetle" }
        assertEquals("Quick Beetle", beetle.item.levelName(state.progress.level(beetle)))
    }

    @Test
    fun theQuickBeetleInItemUpgradesShowsInItemsAndEquipment() = runTest {
        val items = checklistViewModel("ss/items.json")
        val shields = checklistViewModel("ss/shields.json")
        val upgrades = upgradeViewModel()

        upgrades.setLevel("beetle", 2)!!.join()
        upgrades.setLevel("sacred_shield", 2)!!.join()
        val state = items.state.filterNotNull().first { it.progress.levels["beetle"] != null }
        val beetle = state.catalog.item("beetle")!!
        assertEquals(3, state.progress.level(beetle))
        assertEquals("Quick Beetle", beetle.tiers[state.progress.level(beetle) - 1].name)
        val shield = shields.state.filterNotNull().first { it.progress.levels["sacred_shield"] != null }
        assertEquals(3, shield.progress.level(shield.catalog.item("sacred_shield")!!), "the Goddess Shield, the top tier")
    }

    /** Each screen's Mark all shows on the other. */
    @Test
    fun markAllWorksAcrossTheSharedProgress() = runTest {
        val items = checklistViewModel("ss/items.json")
        val upgrades = upgradeViewModel()

        items.setSection("capacity")
        items.state.filterNotNull().first { it.section?.id == "capacity" }
        items.setAllDone(group = null, done = true)!!.join()
        val marked = upgrades.state.filterNotNull().first { s -> s.bulkScope("pouch").done == 3 }
        assertEquals(BulkAction.CLEAR, marked.bulkScope("pouch").action)
        assertEquals(listOf(2, 2, 2), marked.groups.single { it.section.id == "pouch" }.copies.map(marked.progress::level))

        upgrades.setAllOwned("items", owned = true)!!.join()
        val ticked = items.state.filterNotNull().first { s -> s.progress.levels["slingshot"] != null }
        listOf("slingshot", "beetle", "bug_net", "bow").forEach { assertEquals(1, ticked.progress.levels[it], it) }
    }

    /** [ChecklistViewModel.state] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: ChecklistViewModel) = launch { vm.state.collect {} }
}
