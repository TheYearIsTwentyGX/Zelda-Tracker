package gx.zelda.thrilldigger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.thrilldigger.data.ThrillDiggerSave
import gx.zelda.thrilldigger.data.ThrillDiggerStorage
import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerAnalysis
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.HoleOdds
import gx.zelda.thrilldigger.model.ThrillDiggerSolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThrillDiggerUiState(
    val board: DiggerBoard,
    /** The solver's answer for [board]. */
    val analysis: DiggerAnalysis,
    val canUndo: Boolean,
    /** The hole the user tapped, if any; otherwise the next icon goes in the recommended hole. */
    val selected: Int?,
) {
    val difficulty: Difficulty get() = board.difficulty

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
}

/**
 * The Thrill Digger solver: one board per difficulty, saved, with undo.
 *
 * Entering a hole: tap it, then tap what it showed in the picker. With no hole tapped, the picker
 * fills the recommended hole, and after each entry the selection goes back to the new
 * recommendation, so following the advice is one tap per dig. Tapping the selected hole again
 * deselects it. The solver runs on [work], off the main thread.
 */
class ThrillDiggerViewModel(
    private val storage: ThrillDiggerStorage,
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val selection = MutableStateFlow<Int?>(null)

    private data class Solved(val save: ThrillDiggerSave, val board: DiggerBoard, val analysis: DiggerAnalysis)

    private val solved = storage.save
        .map { it to it.board() }
        .distinctUntilChanged()
        .map { (save, board) -> Solved(save, board, ThrillDiggerSolver.solve(board)) }
        .flowOn(work)

    val state: StateFlow<ThrillDiggerUiState?> = combine(solved, selection) { s, selected ->
        ThrillDiggerUiState(
            board = s.board,
            analysis = s.analysis,
            canUndo = s.save.canUndo(),
            selected = selected?.takeIf { it < s.board.holes.size },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
}
