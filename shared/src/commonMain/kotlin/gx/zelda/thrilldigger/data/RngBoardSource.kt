package gx.zelda.thrilldigger.data

import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.RngBoards
import gx.zelda.thrilldigger.model.RngLoop
import gx.zelda.thrilldigger.model.RngLoops
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where a difficulty's RNG boards stand. */
sealed interface RngTable {
    data object NotStarted : RngTable

    /** Being dealt; [progress] runs from 0 to 1. */
    data class Generating(val progress: Float) : RngTable

    data class Ready(val boards: RngBoards) : RngTable
}

/**
 * Deals the RNG's boards for a difficulty the first time they're asked for, in the background, and
 * keeps them for as long as the app runs: about 2.6 million boards, 21 MB, a few seconds on a phone.
 * Nothing is bundled or saved. [shared] is the app's one source, so a rotation or reopening the
 * screen doesn't deal them again.
 */
class RngBoardSource(
    private val loops: List<RngLoop> = RngLoops.all,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val tables = Difficulty.entries.associateWith { MutableStateFlow<RngTable>(RngTable.NotStarted) }

    fun table(difficulty: Difficulty): StateFlow<RngTable> = tables.getValue(difficulty)

    /** Starts dealing [difficulty]'s boards unless they're ready or on their way. */
    fun ensure(difficulty: Difficulty) {
        val flow = tables.getValue(difficulty)
        if (!flow.compareAndSet(RngTable.NotStarted, RngTable.Generating(0f))) return
        scope.launch {
            val boards = RngBoards.generate(
                difficulty,
                loops,
                onProgress = { flow.value = RngTable.Generating(it) },
                checkCancelled = { ensureActive() },
            )
            flow.value = RngTable.Ready(boards)
        }
    }

    companion object {
        val shared: RngBoardSource by lazy { RngBoardSource() }
    }
}
