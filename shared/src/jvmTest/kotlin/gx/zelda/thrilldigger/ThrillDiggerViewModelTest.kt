package gx.zelda.thrilldigger

import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.thrilldigger.data.RngBoardSource
import gx.zelda.thrilldigger.data.ThrillDiggerSave
import gx.zelda.thrilldigger.model.PackedBoard
import gx.zelda.thrilldigger.model.RngLoop
import gx.zelda.thrilldigger.model.ThrillDiggerDeal
import gx.zelda.thrilldigger.ui.OddsSource
import gx.zelda.thrilldigger.data.ThrillDiggerStorage
import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerAnalysis
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.ui.ThrillDiggerUiState
import gx.zelda.thrilldigger.ui.ThrillDiggerViewModel
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The solver screen's state: entering holes, the recommendation, undo, reset and difficulties, all saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThrillDiggerViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val storage = ThrillDiggerStorage(dataStore, Games.SS)

    /** A made-up loop of 3000 states, so the RNG's table is small and quick to list. */
    private val loop = RngLoop(123_456_789L, 3000, 1.0)
    private val rngSource = RngBoardSource(listOf(loop))

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): ThrillDiggerViewModel {
        val vm = main.track(ThrillDiggerViewModel(storage, rngSource = rngSource))
        backgroundScope.launchCollect(vm)
        vm.state.filterNotNull().first()
        return vm
    }

    private suspend fun ThrillDiggerViewModel.ready(check: (ThrillDiggerUiState) -> Boolean) = state.filterNotNull().first(check)

    private fun CoroutineScope.launchCollect(vm: ThrillDiggerViewModel) = launch { vm.state.collect {} }

    private suspend fun saved() = storage.save.first()

    @Test
    fun startsOnAnEmptyExpertBoardWithAHoleRecommended() = runTest {
        val vm = viewModel()
        val state = vm.ready { true }
        assertEquals(Difficulty.EXPERT, state.difficulty)
        assertTrue(state.board.isEmpty)
        assertFalse(state.canUndo)
        assertEquals(40, (state.analysis as DiggerAnalysis.Solved).odds.size)
        assertEquals(state.recommended, state.target, "with nothing tapped, the picker fills the recommended hole")
        assertEquals(24, state.rupeesLeft)
    }

    @Test
    fun aPickedIconGoesInTheRecommendedHoleThenTheNextOne() = runTest {
        val vm = viewModel()
        vm.setDifficulty(Difficulty.BEGINNER).join()
        val first = vm.ready { it.difficulty == Difficulty.BEGINNER }
        val hole = first.recommended!!
        vm.enter(Hole.GREEN)!!.join()
        val second = vm.ready { it.board.holes[hole] == Hole.GREEN }
        assertEquals(Hole.GREEN.code, saved().boards.getValue(Difficulty.BEGINNER)[hole])
        assertTrue(second.recommended != hole, "a dug hole isn't recommended again")
        assertEquals(second.recommended, second.target)
        // A Green Rupee clears its neighbours, so the next recommendation is one of them.
        val r = second.recommended!!
        assertEquals(0.0, second.odds(r)!!.bomb)
        assertEquals(1, second.haul)
    }

    @Test
    fun aTappedHoleTakesTheNextIconAndTheSelectionGoesBack() = runTest {
        val vm = viewModel()
        vm.tapHole(12)
        assertEquals(12, vm.ready { it.selected == 12 }.target)
        vm.enter(Hole.BLUE)!!.join()
        val state = vm.ready { it.board.holes[12] == Hole.BLUE }
        assertNull(state.selected, "back to following the recommendation")
        // Tapping the selected hole again deselects it.
        vm.tapHole(3)
        vm.ready { it.selected == 3 }
        vm.tapHole(3)
        assertNull(vm.ready { it.selected == null }.selected)
        // Clear takes an entry back out.
        vm.tapHole(12)
        vm.ready { it.selected == 12 }
        vm.enter(Hole.UNKNOWN)!!.join()
        assertTrue(vm.ready { it.board.holes[12] == Hole.UNKNOWN }.board.isEmpty)
    }

    @Test
    fun undoStepsBackAndResetIsUndoable() = runTest {
        val vm = viewModel()
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.GREEN)!!.join()
        vm.tapHole(1)
        vm.ready { it.selected == 1 && it.board.holes[0] == Hole.GREEN }
        vm.enter(Hole.BLUE)!!.join()
        vm.ready { it.board.holes[1] == Hole.BLUE && it.canUndo }

        vm.reset()!!.join()
        assertTrue(vm.ready { it.board.isEmpty }.canUndo)
        vm.undo()!!.join()
        vm.ready { it.board.holes[1] == Hole.BLUE }
        vm.undo()!!.join()
        val one = vm.ready { it.board.holes[1] == Hole.UNKNOWN }
        assertEquals(Hole.GREEN, one.board.holes[0])
        vm.undo()!!.join()
        val none = vm.ready { it.board.isEmpty && !it.canUndo }
        assertNull(vm.undo(), "nothing left to undo")
        assertNull(vm.reset(), "an empty board has nothing to reset")
        assertTrue(none.board.isEmpty)
    }

    @Test
    fun eachDifficultyKeepsItsOwnBoard() = runTest {
        val vm = viewModel()
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.GREEN)!!.join()
        vm.ready { it.board.holes[0] == Hole.GREEN }
        vm.setDifficulty(Difficulty.INTERMEDIATE).join()
        val intermediate = vm.ready { it.difficulty == Difficulty.INTERMEDIATE }
        assertTrue(intermediate.board.isEmpty)
        assertFalse(intermediate.canUndo, "undo is per difficulty")
        vm.setDifficulty(Difficulty.EXPERT).join()
        assertEquals(Hole.GREEN, vm.ready { it.difficulty == Difficulty.EXPERT }.board.holes[0])
        assertEquals(Difficulty.EXPERT, saved().difficulty)
    }

    @Test
    fun anImpossibleBoardOutlinesItsConflictAndRecommendsNothing() = runTest {
        val vm = viewModel()
        vm.setDifficulty(Difficulty.BEGINNER).join()
        vm.ready { it.difficulty == Difficulty.BEGINNER }
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.GOLD)!!.join()
        val state = vm.ready { it.board.holes[0] == Hole.GOLD }
        assertIs<DiggerAnalysis.Impossible>(state.analysis)
        assertEquals(setOf(0), state.conflict)
        assertNull(state.target, "no recommendation, and nothing tapped")
        assertNull(vm.enter(Hole.BLUE))
    }

    @Test
    fun aBoardIsSavedAndComesBack() = runTest {
        val board = DiggerBoard.empty(Difficulty.INTERMEDIATE).with(7, Hole.RED).with(8, Hole.RUPOOR)
        storage.update { ThrillDiggerSave(difficulty = Difficulty.INTERMEDIATE).with(board) }
        val vm = viewModel()
        val state = vm.ready { it.difficulty == Difficulty.INTERMEDIATE }
        assertEquals(board, state.board)
        assertTrue(state.canUndo)
        assertEquals(10, state.haul, "a Red Rupee less a Rupoor")
    }

    @Test
    fun theHistoryIsCapped() {
        var save = ThrillDiggerSave()
        repeat(ThrillDiggerSave.HISTORY + 20) { i -> save = save.with(save.board().with(i % 40, if (i / 40 % 2 == 0) Hole.GREEN else Hole.BLUE)) }
        assertEquals(ThrillDiggerSave.HISTORY, save.history.getValue(Difficulty.EXPERT).size)
        assertEquals(save, save.with(save.board()), "an unchanged board isn't kept")
    }

    /** The board the made-up loop's [k]-th state deals. */
    private fun dealt(d: Difficulty, k: Int): DiggerBoard {
        var state = loop.anchor
        repeat(k) { state = gx.zelda.thrilldigger.model.SsRng.next(state) }
        return PackedBoard.unpack(d, ThrillDiggerDeal.deal(d, state))
    }

    @Test
    fun expertUsesTheRngByDefaultAndTheOthersDont() = runTest {
        val vm = viewModel()
        val expert = vm.ready { it.source is OddsSource.Rng }
        assertTrue(expert.rngOn)
        assertEquals(3000, (expert.source as OddsSource.Rng).states, "every state fits an empty board")
        vm.setDifficulty(Difficulty.INTERMEDIATE).join()
        val intermediate = vm.ready { it.difficulty == Difficulty.INTERMEDIATE }
        assertFalse(intermediate.rngOn)
        assertEquals(OddsSource.EveryBoard, intermediate.source)
        vm.setRng(true)!!.join()
        vm.ready { it.difficulty == Difficulty.INTERMEDIATE && it.source is OddsSource.Rng }
        vm.setDifficulty(Difficulty.BEGINNER).join()
        val beginner = vm.ready { it.difficulty == Difficulty.BEGINNER }
        assertFalse(beginner.rngAvailable)
        assertEquals(OddsSource.EveryBoard, beginner.source)
        assertNull(vm.setRng(true), "Beginner has no RNG switch")
        vm.setDifficulty(Difficulty.EXPERT).join()
        vm.ready { it.difficulty == Difficulty.EXPERT && it.source is OddsSource.Rng }
        vm.setRng(false)!!.join()
        assertEquals(OddsSource.EveryBoard, vm.ready { !it.rngOn }.source)
    }

    @Test
    fun aDealtBoardNarrowsTheStatesAndItsLoopCanBeLocked() = runTest {
        val truth = dealt(Difficulty.EXPERT, 7)
        val shown = DiggerBoard(Difficulty.EXPERT, truth.holes.mapIndexed { i, h -> if (h.isRupee && i < 16) h else Hole.UNKNOWN })
        storage.update { ThrillDiggerSave().with(shown) }
        val vm = viewModel()
        val state = vm.ready { it.source is OddsSource.Rng }
        val source = state.source as OddsSource.Rng
        assertTrue(source.states in 1 until 3000)
        // The true board's hazards are never called certainly safe.
        truth.holes.indices.filter { truth.holes[it].isHazard }.forEach { assertTrue(state.odds(it)!!.hazard > 0.0) }
        assertTrue(state.canLockLoop)
        vm.lockLoop()!!.join()
        assertEquals(loop.anchor, saved().lockedLoop[Difficulty.EXPERT])
        assertEquals(0, (vm.ready { (it.source as? OddsSource.Rng)?.locked == 0 }.source as OddsSource.Rng).locked)
        vm.unlockLoop()!!.join()
        vm.ready { (it.source as? OddsSource.Rng)?.locked == null }
        assertTrue(saved().lockedLoop.isEmpty())
    }

    @Test
    fun aBoardTheRngCantDealFallsBackToEveryBoard() = runTest {
        // The same hazards with every Bomb and Rupoor swapped: possible, but no state deals it.
        val truth = dealt(Difficulty.EXPERT, 11)
        val swapped = DiggerBoard(Difficulty.EXPERT, truth.holes.map { when (it) { Hole.BOMB -> Hole.RUPOOR; Hole.RUPOOR -> Hole.BOMB; else -> Hole.UNKNOWN } })
        storage.update { ThrillDiggerSave().with(swapped) }
        val vm = viewModel()
        val state = vm.ready { it.source == OddsSource.NoRngFit }
        assertIs<DiggerAnalysis.Solved>(state.analysis, "the odds over every board")
    }

    @Test
    fun aFinishedBoardIsCheckedAgainstTheRng() = runTest {
        val truth = dealt(Difficulty.EXPERT, 5)
        storage.update { ThrillDiggerSave().with(truth) }
        val vm = viewModel()
        val state = vm.ready { it.board == truth }
        assertTrue(state.finished)
        vm.exportBoard()!!.join()
        val check = vm.check.filterNotNull().first()
        assertTrue(check.states!! >= 1)
        assertTrue(check.text.startsWith("Thrill Digger, Expert"))
        assertTrue(truth.holes.subList(0, 8).joinToString("") { it.code.toString() } in check.text)
        vm.dismissCheck()
        assertNull(vm.check.value)
    }

    @Test
    fun anUnfinishedOrImpossibleBoardStillExports() = runTest {
        val vm = viewModel()
        assertNull(vm.exportBoard(), "nothing to export on an empty board")
        vm.setDifficulty(Difficulty.BEGINNER).join()
        vm.ready { it.difficulty == Difficulty.BEGINNER }
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.GOLD)!!.join()
        vm.ready { it.board.holes[0] == Hole.GOLD }
        vm.exportBoard()!!.join()
        val check = vm.check.filterNotNull().first()
        assertNull(check.states)
        assertEquals("Thrill Digger, Beginner\nY????\n?????\n?????\n?????", check.text.lines().take(5).joinToString("\n"))
        assertTrue(check.text.endsWith("Not finished, so not checked against the RNG."))
    }
}
