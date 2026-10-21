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
import gx.zelda.thrilldigger.model.RupeeTotal
import gx.zelda.thrilldigger.model.TotalInference
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.thrilldigger.ui.BoardCheck
import gx.zelda.thrilldigger.ui.RngCheck
import gx.zelda.thrilldigger.ui.ThrillDiggerUiState
import gx.zelda.thrilldigger.ui.ThrillDiggerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    private suspend fun TestScope.viewModel(source: RngBoardSource = rngSource): ThrillDiggerViewModel {
        val vm = main.track(ThrillDiggerViewModel(storage, rngSource = source))
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
        assertEquals(RupeeTotal(1, 0), second.total)
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
        assertEquals(RupeeTotal(10, 0), state.total, "a Red Rupee less a Rupoor")
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

    /** The export once its RNG check is over. */
    private suspend fun ThrillDiggerViewModel.checked(): BoardCheck = check.filterNotNull().first { it.rng != RngCheck.Checking }

    /** [truth] with only its Rupees, and these hazards, entered: a round played to the end. */
    private fun rupeesOnly(truth: DiggerBoard, keep: (Hole) -> Boolean = { false }) =
        truth.copy(holes = truth.holes.map { if (it.isRupee || keep(it)) it else Hole.UNKNOWN })

    /**
     * The crash: every Rupee dug and some Rupoors, the other holes left undug. The view model called
     * it finished, but RngSolver.dealing wanted all 40 holes and threw on Main, taking the app down.
     */
    @Test
    fun exportingARoundWithItsHazardsUndugChecksItWithoutCrashing() = runTest {
        val truth = dealt(Difficulty.EXPERT, 5)
        val board = rupeesOnly(truth) { it == Hole.RUPOOR }
        storage.update { ThrillDiggerSave().with(board) }
        val vm = viewModel()
        val state = vm.ready { it.board == board && it.source is OddsSource.Rng }
        assertEquals(0, state.rupeesLeft)
        assertTrue(state.finished)
        assertEquals(board.isFinished, state.finished, "one definition of finished")
        vm.exportBoard()!!.join()
        val check = vm.checked()
        val rng = assertIs<RngCheck.Dealt>(check.rng)
        assertTrue(rng.states >= 1)
        assertTrue("RNG state" in check.text && "deal" in check.text && "with these Rupees and hazards" in check.text, check.text)
        // The undug holes are written as hazards of either kind.
        val firstRow = board.holes.subList(0, 8).joinToString("") { if (it == Hole.UNKNOWN) "H" else "${it.code}" }
        assertTrue(firstRow in check.text, check.text)
        if (rng.states == 1) {
            assertEquals(truth, rng.dealt, "one fitting state says which hazard was which")
            assertTrue("Bombs (X) and Rupoors (P)" in check.text)
        }
    }

    @Test
    fun aPartialBoardExportsWithoutACheck() = runTest {
        val truth = dealt(Difficulty.EXPERT, 5)
        val rupees = truth.holes.indices.filter { truth.holes[it].isRupee }
        val board = DiggerBoard(Difficulty.EXPERT, truth.holes.mapIndexed { i, h -> if (i in rupees.take(10)) h else Hole.UNKNOWN })
        storage.update { ThrillDiggerSave().with(board) }
        val vm = viewModel()
        assertFalse(vm.ready { it.board == board }.finished)
        vm.exportBoard()!!.join()
        val check = vm.checked()
        assertEquals(RngCheck.NotFinished, check.rng)
        assertTrue("?" in check.text && "H" !in check.text.lines()[1])
        assertTrue(check.text.endsWith("Not finished, so not checked against the RNG."))
    }

    @Test
    fun aFinishedImpossibleBoardIsCheckedAndFitsNothing() = runTest {
        // A Rupee entered where the dealt board has a hazard: too few undug holes left for the hazards.
        val truth = dealt(Difficulty.EXPERT, 9)
        val board = rupeesOnly(truth).with(truth.holes.indexOfFirst { it.isHazard }, Hole.GOLD)
        storage.update { ThrillDiggerSave().with(board) }
        val vm = viewModel()
        val state = vm.ready { it.board == board }
        assertIs<DiggerAnalysis.Impossible>(state.analysis)
        assertTrue(state.finished)
        vm.exportBoard()!!.join()
        val check = vm.checked()
        assertEquals(0, check.states)
        assertTrue(check.text.endsWith("No RNG state deals a board with these Rupees and hazards."), check.text)
    }

    @Test
    fun finishedBoardsOnTheOtherDifficultiesAreChecked() = runTest {
        val vm = viewModel()
        for (d in listOf(Difficulty.INTERMEDIATE, Difficulty.BEGINNER)) {
            val truth = dealt(d, 3)
            for (board in listOf(truth, rupeesOnly(truth))) {
                storage.update { it.copy(difficulty = d).with(board) }
                vm.ready { it.board == board }
                vm.exportBoard()!!.join()
                val check = vm.checked()
                assertTrue(check.text.startsWith("Thrill Digger, ${d.label}"))
                assertTrue(check.states!! >= 1, "${d.label}: ${check.text}")
                vm.dismissCheck()
            }
        }
    }

    @Test
    fun exportingBeforeTheRngIsListedShowsTheTextAtOnceThenTheCheck() = runTest {
        // The table is listed on a dispatcher nothing runs until the test says so.
        val held = StandardTestDispatcher(TestCoroutineScheduler())
        val source = RngBoardSource(listOf(loop), held)
        val truth = dealt(Difficulty.EXPERT, 5)
        storage.update { ThrillDiggerSave().with(truth) }
        val vm = viewModel(source)
        assertIs<OddsSource.Listing>(vm.ready { it.board == truth }.source)
        val job = vm.exportBoard()!!
        val waiting = assertNotNull(vm.check.value, "the dialog opens at once")
        assertEquals(RngCheck.Checking, waiting.rng)
        assertTrue(waiting.text.startsWith("Thrill Digger, Expert") && waiting.text.endsWith("Checking against the RNG…"))
        held.scheduler.advanceUntilIdle()
        job.join()
        assertTrue(vm.checked().states!! >= 1)
    }

    @Test
    fun anRngTableThatFailsIsShownNotThrown() = runTest {
        // A loop of negative length can't be listed: generating it throws.
        val broken = RngBoardSource(listOf(RngLoop(1L, -1, 1.0)))
        val truth = dealt(Difficulty.EXPERT, 5)
        storage.update { ThrillDiggerSave().with(truth) }
        val vm = viewModel(broken)
        val state = vm.ready { it.source is OddsSource.RngFailed }
        assertIs<DiggerAnalysis.Solved>(state.analysis, "the odds over every board")
        vm.exportBoard()!!.join()
        val check = vm.checked()
        assertIs<RngCheck.Failed>(check.rng)
        assertTrue(check.text.startsWith("Thrill Digger, Expert"))
        assertTrue("Can't check this board: " in check.text, check.text)
        // The rest still works.
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.UNKNOWN)!!.join()
        vm.ready { it.board.holes[0] == Hole.UNKNOWN }
        assertNull(vm.problem.value)
    }

    @Test
    fun aDismissedCheckDoesntComeBack() = runTest {
        val held = StandardTestDispatcher(TestCoroutineScheduler())
        val truth = dealt(Difficulty.EXPERT, 5)
        storage.update { ThrillDiggerSave().with(truth) }
        val vm = viewModel(RngBoardSource(listOf(loop), held))
        vm.ready { it.board == truth }
        val job = vm.exportBoard()!!
        vm.dismissCheck()
        held.scheduler.advanceUntilIdle()
        job.join()
        assertNull(vm.check.value)
    }

    /** A skipped hole: dug, no odds, never recommended, a "?" in the total, and undo and reset as usual. */
    @Test
    fun aSkippedHoleIsDugWithoutAColourAndUndoes() = runTest {
        val vm = viewModel()
        vm.setDifficulty(Difficulty.BEGINNER).join()
        vm.ready { it.difficulty == Difficulty.BEGINNER }
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.GREEN)!!.join()
        vm.tapHole(12)
        vm.ready { it.selected == 12 && it.board.holes[0] == Hole.GREEN }
        vm.enter(Hole.SKIPPED)!!.join()
        val state = vm.ready { it.board.holes[12] == Hole.SKIPPED }
        assertEquals('D', saved().boards.getValue(Difficulty.BEGINNER)[12])
        assertNull(state.odds(12), "no odds on a skipped hole")
        assertTrue(state.recommended != 12)
        assertEquals(RupeeTotal(1, 1), state.total)
        assertEquals("2–301", state.total.text)
        assertEquals(14, state.rupeesLeft, "16 Rupees, one dug and one skipped, so 14")

        vm.reset()!!.join()
        vm.ready { it.board.isEmpty }
        vm.undo()!!.join()
        assertEquals(Hole.SKIPPED, vm.ready { !it.board.isEmpty }.board.holes[12])
        vm.undo()!!.join()
        val before = vm.ready { it.board.holes[12] == Hole.UNKNOWN }
        assertEquals(Hole.GREEN, before.board.holes[0])
        assertEquals(RupeeTotal(1, 0), before.total)
    }

    @Test
    fun theInGameTotalFillsInASkippedHole() = runTest {
        val vm = viewModel()
        vm.setDifficulty(Difficulty.BEGINNER).join()
        vm.ready { it.difficulty == Difficulty.BEGINNER }
        assertNull(vm.checkGameTotal(6), "nothing skipped to work out")
        vm.tapHole(0)
        vm.ready { it.selected == 0 }
        vm.enter(Hole.GREEN)!!.join()
        vm.tapHole(12)
        vm.ready { it.selected == 12 && it.board.holes[0] == Hole.GREEN }
        vm.enter(Hole.SKIPPED)!!.join()
        val skipped = vm.ready { it.board.holes[12] == Hole.SKIPPED }

        vm.checkGameTotal(4)!!.join()
        assertEquals(TotalInference.NoMatch(3), vm.gameTotal.value!!.result)
        assertNull(vm.fillFromGameTotal(), "nothing to fill")
        vm.checkGameTotal(6)!!.join()
        val check = vm.gameTotal.value!!
        assertEquals(skipped.board, check.board)
        assertEquals(TotalInference.Fill(mapOf(12 to Hole.BLUE)), check.result)
        vm.fillFromGameTotal()!!.join()
        assertNull(vm.gameTotal.value)
        val filled = vm.ready { it.board.holes[12] == Hole.BLUE }
        assertEquals(RupeeTotal(6, 0), filled.total)
        // A clue now: a Blue Rupee has a hazard around it, so its neighbours get odds.
        assertTrue(listOf(6, 7, 8, 11, 13, 16, 17, 18).sumOf { filled.odds(it)!!.hazard } >= 1.0 - 1e-9)
        vm.undo()!!.join()
        vm.ready { it.board.holes[12] == Hole.SKIPPED }
    }

    /** A save from before skipped holes still loads as it was. */
    @Test
    fun aSaveFromBeforeSkippedHolesLoads() = runTest {
        val board = "??????" + "?R????" + "??P???" + "??????" + "?????G"
        val old = """{"difficulty":"INTERMEDIATE","boards":{"INTERMEDIATE":"$board"},"history":{"INTERMEDIATE":["${"?".repeat(30)}"]},"rng":{"EXPERT":false}}"""
        dataStore.edit { it[stringPreferencesKey("${Games.SS.id}_thrill_digger")] = old }
        val vm = viewModel()
        val state = vm.ready { it.difficulty == Difficulty.INTERMEDIATE }
        assertEquals(DiggerBoard.decode(Difficulty.INTERMEDIATE, board), state.board)
        assertEquals(Hole.RED, state.board.holes[7])
        assertTrue(state.canUndo)
        assertEquals(RupeeTotal(11, 0), state.total)
        assertFalse(saved().usesRng(Difficulty.EXPERT))
    }

    @Test
    fun aSkippedHoleIsExportedAsD() = runTest {
        val truth = dealt(Difficulty.EXPERT, 5)
        val first = truth.holes.indexOfFirst { it.isRupee }
        val unfinished = DiggerBoard.empty(Difficulty.EXPERT).with(first, Hole.SKIPPED)
        val text = ThrillDiggerViewModel.boardText(unfinished, RngCheck.NotFinished)
        assertEquals('D', text.lines()[1 + first / 8][first % 8])
        assertTrue("D dug, colour not seen" in text.lines()[6], text)
        assertFalse("D dug" in ThrillDiggerViewModel.boardText(DiggerBoard.empty(Difficulty.EXPERT).with(first, Hole.GREEN), RngCheck.NotFinished))

        // A finished round with a hole skipped is still dealt by its state.
        val board = rupeesOnly(truth).with(first, Hole.SKIPPED)
        storage.update { ThrillDiggerSave().with(board) }
        val vm = viewModel()
        assertTrue(vm.ready { it.board == board }.finished, "a skipped hole counts as dug")
        vm.exportBoard()!!.join()
        val check = vm.checked()
        assertTrue(check.states!! >= 1, check.text)
        assertTrue("D" in check.text.lines()[1 + first / 8], check.text)
    }
}
