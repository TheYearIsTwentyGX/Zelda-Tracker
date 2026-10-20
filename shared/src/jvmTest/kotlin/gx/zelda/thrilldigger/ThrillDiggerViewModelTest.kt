package gx.zelda.thrilldigger

import gx.zelda.core.Games
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import gx.zelda.thrilldigger.data.ThrillDiggerSave
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

    /** A view model with its state collected and loaded, so a write never races the first subscription. */
    private suspend fun TestScope.viewModel(): ThrillDiggerViewModel {
        val vm = main.track(ThrillDiggerViewModel(storage))
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
}
