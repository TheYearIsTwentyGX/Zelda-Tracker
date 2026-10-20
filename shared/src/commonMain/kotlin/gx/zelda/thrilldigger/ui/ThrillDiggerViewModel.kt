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
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.HoleOdds
import gx.zelda.thrilldigger.model.RngBoards
import gx.zelda.thrilldigger.model.RngSolver
import gx.zelda.thrilldigger.model.ThrillDiggerSolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
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
}

/**
 * A board as text to send in, so real games can confirm the RNG port. [states] is how many RNG
 * states deal a board agreeing with it, or null when it isn't finished (or can't be) and so wasn't
 * checked.
 */
data class BoardCheck(val text: String, val states: Int?)

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

    /** Rupees dug so far, Rupoors taken off. */
    val haul: Int get() = board.holes.sumOf { if (it.isRupee || it == Hole.RUPOOR) it.rupees else 0 }

    /** Holes still hiding a Rupee. */
    val rupeesLeft: Int get() {
        val d = difficulty
        return board.holes.count { it == Hole.UNKNOWN } - (d.hazards - board.holes.count { it.isHazard })
    }

    /** A finished, possible board: every Rupee dug, so it can be checked against the RNG port. */
    val finished: Boolean get() = rupeesLeft == 0 && analysis is DiggerAnalysis.Solved

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

    private fun solve(save: ThrillDiggerSave, board: DiggerBoard, table: RngTable?): Solved = when (table) {
        null, RngTable.NotStarted -> Solved(save, board, uniform(board), OddsSource.EveryBoard)
        is RngTable.Generating -> Solved(save, board, uniform(board), OddsSource.Listing(table.progress))
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        return viewModelScope.launch { storage.update { it.with(it.board(d).with(index, hole)) } }
    }

    fun undo(): Job? {
        val state = state.value?.takeIf { it.canUndo } ?: return null
        selection.value = null
        return viewModelScope.launch { storage.update { it.undo(state.difficulty) } }
    }

    /** Clears the board for a new round; undo brings it back. */
    fun reset(): Job? {
        val state = state.value?.takeIf { !it.board.isEmpty } ?: return null
        selection.value = null
        return viewModelScope.launch { storage.update { it.with(DiggerBoard.empty(state.difficulty)) } }
    }

    fun setDifficulty(difficulty: Difficulty): Job {
        selection.value = null
        return viewModelScope.launch { storage.update { it.copy(difficulty = difficulty) } }
    }

    /** Turns the RNG odds on or off for the difficulty on screen. */
    fun setRng(on: Boolean): Job? {
        val state = state.value?.takeIf { it.rngAvailable } ?: return null
        return viewModelScope.launch { storage.update { it.copy(rng = it.rng + (state.difficulty to on)) } }
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
        return viewModelScope.launch { storage.update { it.copy(lockedLoop = it.lockedLoop + (state.difficulty to anchor)) } }
    }

    fun unlockLoop(): Job? {
        val state = state.value ?: return null
        return viewModelScope.launch { storage.update { it.copy(lockedLoop = it.lockedLoop - state.difficulty) } }
    }

    /**
     * Exports the board as text to send in, any board but an empty one, impossible ones included (a
     * real board the solver calls impossible is worth knowing about). A finished board is also
     * checked against the RNG port: how many RNG states deal a board agreeing with every hole
     * entered. It lists the RNG's boards first if they aren't yet.
     */
    fun exportBoard(): Job? {
        val state = state.value?.takeIf { !it.board.isEmpty } ?: return null
        val d = state.difficulty
        return viewModelScope.launch {
            val states = if (!state.finished) {
                null
            } else {
                rngSource.ensure(d)
                val boards: RngBoards = rngSource.table(d).filterIsInstance<RngTable.Ready>().first().boards
                withContext(work) { RngSolver.dealing(state.board, boards).size }
            }
            _check.value = BoardCheck(boardText(state.board, states), states)
        }
    }

    fun dismissCheck() {
        _check.value = null
    }

    companion object {
        /** A board as text to paste into a report: a row of [Hole.code]s per row, a key, and the check. */
        fun boardText(board: DiggerBoard, states: Int?): String = buildString {
            val d = board.difficulty
            appendLine("Thrill Digger, ${d.label}")
            for (r in 0 until d.height) appendLine(board.holes.subList(r * d.width, (r + 1) * d.width).joinToString("") { it.code.toString() })
            appendLine("G green, B blue, R red, S silver, Y gold, P Rupoor, X Bomb, ? not dug")
            append(
                when (states) {
                    null -> "Not finished, so not checked against the RNG."
                    0 -> "No RNG state deals this board."
                    1 -> "1 RNG state deals this board."
                    else -> "$states RNG states deal this board."
                },
            )
        }.trimEnd()
    }
}
