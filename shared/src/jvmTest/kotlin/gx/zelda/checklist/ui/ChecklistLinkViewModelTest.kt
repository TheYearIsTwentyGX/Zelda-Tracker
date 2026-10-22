package gx.zelda.checklist.ui

import gx.zelda.checklist.LinkFixture
import gx.zelda.checklist.data.ChecklistProgressProvider
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.data.StoryProgress
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.BulkAction
import gx.zelda.core.CachedLoad
import gx.zelda.core.CountingDataStore
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
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
import kotlin.test.assertTrue

/**
 * Two checklists sharing progress through `sameAs` links, live through their view models: a tick in
 * either shows in both, in one write, and mark-all, undo, save files, story gating and the overview
 * row all go through the links.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistLinkViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val game = LinkFixture.game
    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val rewards = ChecklistStorage(dataStore, game, "link/rewards.json")
    private val items = ChecklistStorage(dataStore, game, "link/items.json")
    private val story = ChecklistStorage(dataStore, game, "link/main_quest.json")

    private suspend fun TestScope.viewModel(storage: ChecklistStorage, catalog: ChecklistCatalog): ChecklistViewModel {
        val storyGate = StoryProgress(dataStore, game, storage.dataFile) { LinkFixture.story }
        val vm = main.track(ChecklistViewModel(storage, storyGate::gate) { catalog })
        backgroundScope.launch { vm.state.collect {} }
        // Wait for the first state before writing (see ChecklistViewModelTest).
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun TestScope.rewardsVm() = viewModel(rewards, LinkFixture.rewards)
    private suspend fun TestScope.itemsVm() = viewModel(items, LinkFixture.items)

    private suspend fun ChecklistViewModel.whenState(predicate: (ChecklistUiState) -> Boolean) = state.filterNotNull().first(predicate)

    private fun ChecklistUiState.done(id: String) = progress.isDone(catalog.item(id)!!)
    private fun ChecklistUiState.level(id: String) = progress.level(catalog.item(id)!!)

    private suspend fun stored(storage: ChecklistStorage, file: String = "file_1") = storage.progress(file).first().levels

    @Test
    fun aRewardTickedShowsAsTheWalletTierInOneWrite() = runTest {
        val rewardsVm = rewardsVm()
        val itemsVm = itemsVm()

        val before = dataStore.writes
        rewardsVm.setDone("reward_30", true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit")
        assertEquals(mapOf("wallet" to 2), stored(items), "stored once, on the Wallet")
        assertEquals(emptyMap(), stored(rewards), "the reward keeps no level of its own")

        itemsVm.whenState { it.level("wallet") == 2 }
        rewardsVm.whenState { it.done("reward_5") && it.done("reward_30") && !it.done("reward_50") }
    }

    @Test
    fun theWalletTierShowsAsTheRewardsTicked() = runTest {
        val rewardsVm = rewardsVm()
        val itemsVm = itemsVm()

        itemsVm.setLevel("wallet", 3)!!.join()
        rewardsVm.whenState { s -> listOf("reward_5", "reward_30", "reward_50").all { s.done(it) } }

        // Unticking the Big Wallet's reward takes the wallet back below it, and the Giant Wallet with it.
        rewardsVm.setDone("reward_30", false)!!.join()
        itemsVm.whenState { it.level("wallet") == 1 }
        rewardsVm.whenState { it.done("reward_5") && !it.done("reward_30") && !it.done("reward_50") }

        itemsVm.setDone("harp", true)!!.join()
        rewardsVm.whenState { it.done("reward_10") }
        itemsVm.whenState { it.done("harp_song") }
    }

    @Test
    fun spoilerHiddenRowsShowOnceTheirLinkIsTicked() = runTest {
        val rewardsVm = rewardsVm()
        rewardsVm.setHideUnchecked(true).join()
        val hidden = rewardsVm.whenState { it.prefs.hideUnchecked }
        assertTrue(hidden.isHidden(hidden.catalog.item("reward_10")!!))

        items.setDone("file_1", LinkFixture.item("harp"), true)
        val shown = rewardsVm.whenState { it.done("reward_10") }
        assertFalse(shown.isHidden(shown.catalog.item("reward_10")!!))
    }

    @Test
    fun markAllAndUndoGoThroughTheLinksInOneWriteEach() = runTest {
        items.setLevel("file_1", LinkFixture.item("wallet"), 1)
        items.setDone("file_1", LinkFixture.item("sword"), true)
        val rewardsVm = rewardsVm()
        rewardsVm.whenState { it.done("reward_5") }

        val mark = rewardsVm.whenState { it.bulkScope().action == BulkAction.MARK }.bulkScope()
        assertTrue("Rows shared with another tracker change there too." in mark.note.orEmpty(), mark.note)
        var before = dataStore.writes
        rewardsVm.setAllDone(null, true)!!.join()
        assertEquals(1, dataStore.writes - before, "one DataStore edit across both files")
        assertEquals(mapOf("wallet" to 3, "harp" to 1, "sword" to 2), stored(items))
        assertEquals(mapOf("gold" to 1, "after_trial" to 1), stored(rewards))
        val change = rewardsVm.bulkChange.first { it != null }!!
        assertEquals("Marked 5 rewards complete on File 1", change.message, "the 5-crystal reward was done already")

        // A later change outside the toggle survives its undo.
        items.setLevel("file_1", LinkFixture.item("sword"), 1)
        before = dataStore.writes
        rewardsVm.undoBulkChange(change)!!.join()
        assertEquals(1, dataStore.writes - before)
        assertEquals(mapOf("wallet" to 1, "sword" to 1), stored(items), "the wallet and harp exactly as they were")
        assertEquals(emptyMap(), stored(rewards))

        rewardsVm.setAllDone(null, true)!!.join()
        rewardsVm.whenState { it.bulkScope().action == BulkAction.CLEAR }
        rewardsVm.setAllDone(null, false)!!.join()
        assertEquals(mapOf("sword" to 1), stored(items), "clearing takes every linked tier and the harp")
        assertEquals("Cleared 6 rewards on File 1", rewardsVm.bulkChange.first { it?.action == BulkAction.CLEAR }!!.message)
    }

    @Test
    fun eachSaveFileHasItsOwnLinkedProgress() = runTest {
        items.setLevel("file_2", LinkFixture.item("wallet"), 2)
        items.setLevel("file_1", LinkFixture.item("wallet"), 1)
        val rewardsVm = rewardsVm()
        rewardsVm.whenState { it.done("reward_5") && !it.done("reward_30") }

        rewardsVm.setSaveFile("file_2").join()
        val two = rewardsVm.whenState { it.saveFile.id == "file_2" && it.done("reward_30") }
        assertEquals(listOf(1, 2), two.saveFiles.map { it.count.done }, "the picker's counts follow each file's wallet")

        // A write after the switch lands on File 2, and its Wallet.
        rewardsVm.setDone("reward_50", true)!!.join()
        assertEquals(mapOf("wallet" to 3), stored(items, "file_2"))
        assertEquals(mapOf("wallet" to 1), stored(items, "file_1"))
    }

    @Test
    fun storyGatingReadsMainQuestStepsThroughLinks() = runTest {
        AppSettingsStorage(dataStore).setStoryGating(true)
        val rewardsVm = rewardsVm()
        val afterTrial = LinkFixture.item("after_trial")
        val reward30 = LinkFixture.item("reward_30")
        val locked = rewardsVm.whenState { it.isLocked(afterTrial) && it.isLocked(reward30) }
        assertEquals(3, locked.bulkScope().changes, "mark-all leaves the three locked rows alone")

        // The trial is the Sword's second tier, in Items: reaching it there unlocks what the trial gates.
        items.setLevel("file_1", LinkFixture.item("sword"), 2)
        rewardsVm.whenState { !it.isLocked(afterTrial) && it.isLocked(reward30) }
        assertTrue(story.progress("file_1").first().isDone(LinkFixture.story.item("trial")!!))

        // The Big Wallet ticked in Items shows its reward, though the temple isn't done.
        items.setLevel("file_1", LinkFixture.item("wallet"), 2)
        rewardsVm.whenState { !it.isLocked(reward30) && it.done("reward_30") }
    }

    @Test
    fun theOverviewRowCountsThroughLinks() = runTest {
        items.setLevel("file_1", LinkFixture.item("wallet"), 2)
        rewards.setDone("file_1", LinkFixture.item("gold"), true)
        val provider = ChecklistProgressProvider(rewards, "Rewards", CachedLoad { LinkFixture.rewards })
        val row = provider.summary(game.saveFiles.first()).first()
        assertEquals(3 to 6, row.done to row.total)
        assertEquals("Rewards 3 / 6", row.secondary)
        assertEquals("10 crystals, in Town", row.next)
    }
}
