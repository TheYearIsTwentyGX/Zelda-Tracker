package gx.zelda.thrilldigger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.thrilldigger.data.RngBoardSource
import gx.zelda.thrilldigger.data.RngTable
import gx.zelda.thrilldigger.data.ThrillDiggerSave
import gx.zelda.thrilldigger.data.ThrillDiggerStorage
import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerAnalysis
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.GameTotal
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.HoleOdds
import gx.zelda.thrilldigger.model.PackedBoard
import gx.zelda.thrilldigger.model.RngSolver
import gx.zelda.thrilldigger.model.RupeeTotal
import gx.zelda.thrilldigger.model.ThrillDiggerSolver
import gx.zelda.thrilldigger.model.TotalInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the odds come from. */
sealed interface OddsSource {
    /** Every board the dealing rules allow (Beginner always; the others when the RNG is off). */
    data object EveryBoard : OddsSource

    /** The RNG's boards are being listed, [progress] of the way; meanwhile the odds are [EveryBoard]'s. */
    data class Listing(val progress: Float) : OddsSource

    /**
     * The boards the RNG deals: [states] of them fit, on [loops] (indices into [loopCount] loops).
     * [locked] is the loop the board is locked to, if any; [lockMissed] says nothing on it fitted,
     * so every loop was used.
     */
    data class Rng(val states: Int, val loops: Set<Int>, val loopCount: Int, val locked: Int?, val lockMissed: Boolean) : OddsSource

    /** No board the RNG deals fits (it may not have settled into a loop yet), so it's [EveryBoard]'s odds. */
    data object NoRngFit : OddsSource

    /** Listing or searching the RNG's boards failed, for [reason], so it's [EveryBoard]'s odds. */
    data class RngFailed(val reason: String) : OddsSource
}

/** How an exported board stands against the RNG port. */
sealed interface RngCheck {
    /** Some Rupee isn't dug yet ([DiggerBoard.isFinished]), so it isn't checked. */
    data object NotFinished : RngCheck

    /** Waiting for the RNG's boards to be listed, or searching them. */
    data object Checking : RngCheck

    /**
     * [states] RNG states deal a board with these Rupees and hazards; when it's one, [dealt] is its
     * board, which says which undug holes held Bombs and which Rupoors.
     */
    data class Dealt(val states: Int, val dealt: DiggerBoard? = null) : RngCheck

    /** The check went wrong, for [reason]; the board is still exported. */
    data class Failed(val reason: String) : RngCheck
}

/** A board as text to send in, so real games can confirm the RNG port, and how it [rng] checks out. */
data class BoardCheck(val board: DiggerBoard, val rng: RngCheck) {
    val text: String get() = ThrillDiggerViewModel.boardText(board, rng)

    /** How many RNG states deal the board, once it's been checked. */
    val states: Int? get() = (rng as? RngCheck.Dealt)?.states
}

/** The round's total in the game, [gameTotal], checked against [board]'s skipped holes. */
data class GameTotalCheck(val board: DiggerBoard, val gameTotal: Int, val result: TotalInference)

data class ThrillDiggerUiState(
    val board: DiggerBoard,
    /** The solver's answer for [board]. */
    val analysis: DiggerAnalysis,
    val canUndo: Boolean,
    /** The hole the user tapped, if any; otherwise the next icon goes in the recommended hole. */
    val selected: Int?,
    val source: OddsSource = OddsSource.EveryBoard,
    /** Whether the RNG switch is on for this difficulty. */
    val rngOn: Boolean = false,
) {
    val difficulty: Difficulty get() = board.difficulty

    /** Beginner has no RNG switch: the RNG covers its boards evenly. */
    val rngAvailable: Boolean get() = difficulty != Difficulty.BEGINNER

    val recommended: Int? get() = (analysis as? DiggerAnalysis.Solved)?.recommended

    /** Where the next icon tapped goes: the tapped hole, or else the recommended one. */
    val target: Int? get() = selected ?: recommended

    val conflict: Set<Int> get() = (analysis as? DiggerAnalysis.Impossible)?.conflict.orEmpty()

    fun odds(index: Int): HoleOdds? = (analysis as? DiggerAnalysis.Solved)?.odds?.get(index)

    /** Rupees dug so far, Rupoors taken off, with the skipped holes' unknown worth. */
    val total: RupeeTotal get() = RupeeTotal.of(board)

    /** Holes still hiding a Rupee. */
    val rupeesLeft: Int get() = board.rupeesLeft

    /** No Rupee left to dig, so Export board also checks it against the RNG port. */
    val finished: Boolean get() = board.isFinished

    /** Exactly one loop fits and none is locked: offer to lock it. */
    val canLockLoop: Boolean get() = (source as? OddsSource.Rng)?.let { it.locked == null && it.loops.size == 1 } == true
}

/**
 * The Thrill Digger solver: one board per difficulty, saved, with undo.
 *
 * Entering a hole: tap it, then tap what it showed in the picker. With no hole tapped, the picker
 * fills the recommended hole, and after each entry the selection goes back to the new
 * recommendation, so following the advice is one tap per dig. Tapping the selected hole again
 * deselects it. The solvers run on [work], off the main thread.
 *
 * With the RNG switch on, the odds come from the boards the game's RNG can deal ([RngSolver]),
 * listed by [rngSource] in the background the first time; until then, and whenever none fits, they
 * come from every board the rules allow ([ThrillDiggerSolver]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThrillDiggerViewModel(
    private val storage: ThrillDiggerStorage,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val rngSource: RngBoardSource = RngBoardSource.shared,
) : ViewModel() {
    private val selection = MutableStateFlow<Int?>(null)

    private val _problem = MutableStateFlow<String?>(null)

    /** What went wrong in the background, if anything did, until the next action. */
    val problem: StateFlow<String?> = _problem

    /**
     * [viewModelScope], but an exception that escapes a coroutine is shown as [problem] rather than
     * taking the app down from Main.
     */
    private val scope = CoroutineScope(viewModelScope.coroutineContext + CoroutineExceptionHandler { _, e -> _problem.value = reason(e) })

    /** Launches an action in [scope], clearing the last [problem]. */
    private fun act(block: suspend CoroutineScope.() -> Unit): Job {
        _problem.value = null
        return scope.launch(block = block)
    }

    private data class Solved(val save: ThrillDiggerSave, val board: DiggerBoard, val analysis: DiggerAnalysis, val source: OddsSource)

    /** The last uniform answer, so listing progress doesn't solve the same board again. */
    private var uniform: Pair<DiggerBoard, DiggerAnalysis>? = null

    private fun uniform(board: DiggerBoard): DiggerAnalysis =
        uniform?.takeIf { it.first == board }?.second ?: ThrillDiggerSolver.solve(board).also { uniform = board to it }

    private val solved = storage.save
        .map { Triple(it, it.board(), it.usesRng()) }
        .distinctUntilChanged()
        .flatMapLatest { (save, board, rng) ->
            if (!rng) {
                flowOf(Triple(save, board, null))
            } else {
                rngSource.ensure(board.difficulty)
                rngSource.table(board.difficulty).map { Triple(save, board, it) }
            }
        }
        .map { (save, board, table) -> solve(save, board, table) }
        .flowOn(work)

    /** The RNG's odds when [table] has them; if anything goes wrong there, every board's, saying why. */
    private fun solve(save: ThrillDiggerSave, board: DiggerBoard, table: RngTable?): Solved = try {
        solveOrThrow(save, board, table)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Solved(save, board, uniform(board), OddsSource.RngFailed(reason(e)))
    }

    private fun solveOrThrow(save: ThrillDiggerSave, board: DiggerBoard, table: RngTable?): Solved = when (table) {
        null, RngTable.NotStarted -> Solved(save, board, uniform(board), OddsSource.EveryBoard)
        is RngTable.Generating -> Solved(save, board, uniform(board), OddsSource.Listing(table.progress))
        is RngTable.Failed -> Solved(save, board, uniform(board), OddsSource.RngFailed(table.reason))
        is RngTable.Ready -> {
            val boards = table.boards
            val locked = save.lockedLoop[board.difficulty]?.let { anchor -> boards.loops.indexOfFirst { it.anchor == anchor } }?.takeIf { it >= 0 }
            val onLocked = locked?.let { RngSolver.solve(board, boards, it) }
            val result = onLocked ?: RngSolver.solve(board, boards)
            if (result == null) {
                Solved(save, board, uniform(board), OddsSource.NoRngFit)
            } else {
                val source = OddsSource.Rng(result.states, result.loops, boards.loops.size, locked, lockMissed = locked != null && onLocked == null)
                Solved(save, board, result.analysis, source)
            }
        }
    }

    val state: StateFlow<ThrillDiggerUiState?> = combine(solved, selection) { s, selected ->
        ThrillDiggerUiState(
            board = s.board,
            analysis = s.analysis,
            canUndo = s.save.canUndo(),
            selected = selected?.takeIf { it < s.board.holes.size },
            source = s.source,
            rngOn = s.save.usesRng(),
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _check = MutableStateFlow<BoardCheck?>(null)

    /** The exported board, while its dialog is open. */
    val check: StateFlow<BoardCheck?> = _check

    /** Selects [index], or deselects it if it's already selected. */
    fun tapHole(index: Int) {
        selection.update { if (it == index) null else index }
    }

    /**
     * Puts [hole] in the target hole (the selected one, or else the recommended one); [Hole.UNKNOWN]
     * clears it. The selection then goes back to following the recommendation. Each write returns
     * its job, so a test can wait for it.
     */
    fun enter(hole: Hole): Job? {
        val state = state.value ?: return null
        val index = state.target ?: return null
        selection.value = null
        if (state.board.holes[index] == hole) return null
        val d = state.difficulty
        return act { storage.update { it.with(it.board(d).with(index, hole)) } }
    }

    fun undo(): Job? {
        val state = state.value?.takeIf { it.canUndo } ?: return null
        selection.value = null
        return act { storage.update { it.undo(state.difficulty) } }
    }

    /** Clears the board for a new round; undo brings it back. */
    fun reset(): Job? {
        val state = state.value?.takeIf { !it.board.isEmpty } ?: return null
        selection.value = null
        return act { storage.update { it.with(DiggerBoard.empty(state.difficulty)) } }
    }

    fun setDifficulty(difficulty: Difficulty): Job {
        selection.value = null
        return act { storage.update { it.copy(difficulty = difficulty) } }
    }

    /** Turns the RNG odds on or off for the difficulty on screen. */
    fun setRng(on: Boolean): Job? {
        val state = state.value?.takeIf { it.rngAvailable } ?: return null
        return act { storage.update { it.copy(rng = it.rng + (state.difficulty to on)) } }
    }

    /**
     * Locks the difficulty to the one loop that fits, so later rounds only count its boards: the RNG
     * never leaves a loop until the game is restarted.
     */
    fun lockLoop(): Job? {
        val state = state.value?.takeIf { it.canLockLoop } ?: return null
        val loop = (state.source as OddsSource.Rng).loops.single()
        val table = rngSource.table(state.difficulty).value as? RngTable.Ready ?: return null
        val anchor = table.boards.loops[loop].anchor
        return act { storage.update { it.copy(lockedLoop = it.lockedLoop + (state.difficulty to anchor)) } }
    }

    fun unlockLoop(): Job? {
        val state = state.value ?: return null
        return act { storage.update { it.copy(lockedLoop = it.lockedLoop - state.difficulty) } }
    }

    /** Lists the RNG's boards again after it failed. */
    fun retryRng() {
        val state = state.value ?: return
        _problem.value = null
        rngSource.ensure(state.difficulty)
    }

    private var exporting: Job? = null

    /**
     * Exports the board as text to send in, any board but an empty one, impossible ones included (a
     * real board the solver calls impossible is worth knowing about). The text shows at once; a
     * [DiggerBoard.isFinished] board is then checked against the RNG port: how many RNG states deal
     * exactly it, listing the RNG's boards first if they aren't yet. The check never throws: what
     * goes wrong is shown as [RngCheck.Failed].
     */
    fun exportBoard(): Job? {
        val board = state.value?.board?.takeIf { !it.isEmpty } ?: return null
        exporting?.cancel()
        _check.value = BoardCheck(board, if (board.isFinished) RngCheck.Checking else RngCheck.NotFinished)
        return act {
            val rng = rngCheck(board)
            _check.update { if (it?.board == board) BoardCheck(board, rng) else it }
        }.also { exporting = it }
    }

    private suspend fun rngCheck(board: DiggerBoard): RngCheck {
        if (!board.isFinished) return RngCheck.NotFinished
        return try {
            val d = board.difficulty
            rngSource.ensure(d)
            when (val table = rngSource.table(d).first { it is RngTable.Ready || it is RngTable.Failed }) {
                is RngTable.Ready -> withContext(work) {
                    val states = RngSolver.dealing(board, table.boards)
                    RngCheck.Dealt(states.size, states.singleOrNull()?.let { PackedBoard.unpack(d, table.boards.boards[it]) })
                }
                is RngTable.Failed -> RngCheck.Failed(table.reason)
                else -> RngCheck.Failed("the RNG's boards aren't listed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            RngCheck.Failed(reason(e))
        }
    }

    private val _gameTotal = MutableStateFlow<GameTotalCheck?>(null)

    /** The last in-game total entered and what it says about the skipped holes, until dismissed. */
    val gameTotal: StateFlow<GameTotalCheck?> = _gameTotal

    /**
     * Works out the skipped holes' colours from [total], the Rupees the game says this round has
     * earned ([GameTotal.infer]), into [gameTotal]. Null when no hole is skipped.
     */
    fun checkGameTotal(total: Int): Job? {
        val board = state.value?.board?.takeIf { Hole.SKIPPED in it.holes } ?: return null
        return act {
            val result = withContext(work) { GameTotal.infer(board, total) }
            _gameTotal.value = GameTotalCheck(board, total, result)
        }
    }

    /**
     * Fills the skipped holes with the colours [gameTotal] found, as one undoable change, so they
     * become clues. Null unless it found one way and the board hasn't changed since.
     */
    fun fillFromGameTotal(): Job? {
        val check = _gameTotal.value ?: return null
        val fill = (check.result as? TotalInference.Fill)?.fill ?: return null
        if (state.value?.board != check.board) return null
        _gameTotal.value = null
        selection.value = null
        val d = check.board.difficulty
        return act {
            storage.update { save ->
                var board = save.board(d)
                for ((i, hole) in fill) if (board.holes[i] == Hole.SKIPPED) board = board.with(i, hole)
                save.with(board)
            }
        }
    }

    fun dismissGameTotal() {
        _gameTotal.value = null
    }

    fun dismissCheck() {
        exporting?.cancel()
        exporting = null
        _check.value = null
    }

    companion object {
        /**
         * A board as text to paste into a report: a row of [Hole.code]s per row, a key, and the check.
         * On a finished board an undug hole is a hazard of either kind, [HAZARD]; when one RNG state
         * fits, its board follows, saying which were which. A skipped hole is `D`: dug, some Rupee.
         */
        fun boardText(board: DiggerBoard, rng: RngCheck): String = buildString {
            val d = board.difficulty
            val undug = if (board.isFinished) HAZARD else Hole.UNKNOWN.code
            fun rows(b: DiggerBoard) {
                for (r in 0 until d.height) appendLine(b.holes.subList(r * d.width, (r + 1) * d.width).joinToString("") { if (it == Hole.UNKNOWN) "$undug" else "${it.code}" })
            }
            appendLine("Thrill Digger, ${d.label}")
            rows(board)
            val skipped = if (Hole.SKIPPED in board.holes) "${Hole.SKIPPED.code} dug, colour not seen, " else ""
            appendLine("G green, B blue, R red, S silver, Y gold, P Rupoor, X Bomb, $skipped" + if (board.isFinished) "$HAZARD Bomb or Rupoor, not dug" else "? not dug")
            val hidden = board.isFinished && Hole.UNKNOWN in board.holes
            val what = if (hidden) "a board with these Rupees and hazards" else "this board"
            append(
                when (rng) {
                    RngCheck.NotFinished -> "Not finished, so not checked against the RNG."
                    RngCheck.Checking -> "Checking against the RNG…"
                    is RngCheck.Failed -> "Can't check this board: ${rng.reason}"
                    is RngCheck.Dealt -> when (rng.states) {
                        0 -> "No RNG state deals $what."
                        1 -> "1 RNG state deals $what."
                        else -> "${rng.states} RNG states deal $what."
                    }
                },
            )
            if (hidden && rng is RngCheck.Dealt && rng.dealt != null) {
                appendLine()
                appendLine("Its board, with the $HAZARD holes' Bombs (X) and Rupoors (P):")
                rows(rng.dealt)
            }
        }.trimEnd()

        /** An undug hole on a finished board, in [boardText]: a Bomb or a Rupoor. */
        const val HAZARD = 'H'

        /** What went wrong, in a few words. */
        fun reason(e: Throwable): String = e.message ?: e::class.simpleName ?: "unknown error"
    }
}
