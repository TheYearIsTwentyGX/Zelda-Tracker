package gx.zelda.thrilldigger.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The solver's saved state: the difficulty on screen, and each difficulty's board and undo history
 * as [DiggerBoard.encode] text, so a board survives rotation, leaving the screen and the app being
 * closed. It's a tool, not progress, so it isn't kept per save file.
 */
@Serializable
data class ThrillDiggerSave(
    val difficulty: Difficulty = Difficulty.EXPERT,
    val boards: Map<Difficulty, String> = emptyMap(),
    /** Earlier boards, oldest first, at most [HISTORY] of them. */
    val history: Map<Difficulty, List<String>> = emptyMap(),
) {
    fun board(d: Difficulty = difficulty): DiggerBoard = DiggerBoard.decode(d, boards[d])

    fun canUndo(d: Difficulty = difficulty): Boolean = !history[d].isNullOrEmpty()

    /** [board] in place of its difficulty's board, with the old one kept for undo. Unchanged boards aren't kept. */
    fun with(board: DiggerBoard): ThrillDiggerSave {
        val d = board.difficulty
        val before = board(d)
        if (before == board) return this
        return copy(
            boards = boards + (d to board.encode()),
            history = history + (d to (history[d].orEmpty() + before.encode()).takeLast(HISTORY)),
        )
    }

    /** The board before the last change on [d], or this unchanged when there's none. */
    fun undo(d: Difficulty = difficulty): ThrillDiggerSave {
        val past = history[d].orEmpty()
        if (past.isEmpty()) return this
        return copy(boards = boards + (d to past.last()), history = history + (d to past.dropLast(1)))
    }

    companion object {
        const val HISTORY = 200
    }
}

/** Persists the Thrill Digger solver under `<game>_thrill_digger`. */
class ThrillDiggerStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val store = JsonStore(dataStore, "${game.id}_thrill_digger", ThrillDiggerSave.serializer(), ThrillDiggerSave())

    val save: Flow<ThrillDiggerSave> = store.data

    /** Changes the saved state in one write, from what's stored, so quick taps can't undo each other. */
    suspend fun update(transform: (ThrillDiggerSave) -> ThrillDiggerSave) = store.update(transform)
}
