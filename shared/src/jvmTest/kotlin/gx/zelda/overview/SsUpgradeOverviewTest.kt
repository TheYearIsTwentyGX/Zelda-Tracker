package gx.zelda.overview

import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.Games
import gx.zelda.core.ProgressSummary
import gx.zelda.core.TestMain
import gx.zelda.core.TrackerKind
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.data.UpgradeStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Skyward Sword's overview has an Item Upgrades row, from the bundled data, following the save file and the potion chip. */
@OptIn(ExperimentalCoroutinesApi::class)
class SsUpgradeOverviewTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val main = TestMain(dispatcher)

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val storage = UpgradeStorage(dataStore, Games.SS)

    private fun OverviewUiState?.upgrades(): ProgressSummary? =
        (this?.rows?.singleOrNull { it.kind == TrackerKind.ITEM_UPGRADES }?.status as? RowStatus.Ready)?.summary

    @Test
    fun theRowCountsItemsUpgradesAndWhatsLeft() = runTest {
        storage.update("file_2") { p ->
            p.withCopy("beetle") { it.copy(owned = true, level = 1) }
                .withCopy("hylian_shield") { it.copy(owned = true) }
        }
        val vm = main.track(OverviewViewModel(dataStore, Games.SS, loadDispatcher = dispatcher))
        val fresh = vm.state.first { it.upgrades() != null }.upgrades()!!
        assertEquals("Item Upgrades", fresh.title)
        assertEquals(0 to 16, fresh.done to fresh.total)
        assertEquals("Upgrades 0 / 26 · 195 materials and 1,510 Rupees to go", fresh.secondary)

        vm.setSaveFile("file_2").join()
        val two = vm.state.first { s -> s?.saveFile?.id == "file_2" && s.upgrades()?.done == 2 }.upgrades()!!
        assertEquals("Upgrades 1 / 26 · 195 materials and 1,510 Rupees to go", two.secondary, "the Hook Beetle is free")
        assertEquals("Quick Beetle (Gondo's Scrap Shop)", two.next)

        storage.updatePrefs { it.copy(hiddenSections = setOf("potions")) }
        val noPotions = vm.state.first { it.upgrades()?.total == 11 }.upgrades()!!
        assertEquals("Upgrades 1 / 19 · 145 materials and 1,330 Rupees to go", noPotions.secondary)
        assertEquals(PieceProgress(), storage.progress("file_1").first().of("beetle"))
    }
}
