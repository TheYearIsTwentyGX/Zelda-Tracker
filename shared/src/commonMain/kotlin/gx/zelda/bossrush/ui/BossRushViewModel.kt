package gx.zelda.bossrush.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.bossrush.data.BossRushData
import gx.zelda.bossrush.data.BossRushProgressProvider
import gx.zelda.bossrush.data.BossRushRepository
import gx.zelda.bossrush.data.BossRushStorage
import gx.zelda.bossrush.model.BossRow
import gx.zelda.bossrush.model.BossRushBoard
import gx.zelda.bossrush.model.BossRushRecords
import gx.zelda.bossrush.model.RecordEntry
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.SaveFile
import gx.zelda.core.SaveFileSettings
import gx.zelda.core.SaveFileSettingsStorage
import gx.zelda.core.todayEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A save file as the picker shows it: its mode and how many of its slots have a record. */
data class BossRushFileSummary(val file: SaveFile, val heroMode: Boolean, val set: Int, val slots: Int)

data class BossRushUiState(
    val data: BossRushData,
    val saveFile: SaveFile,
    val heroMode: Boolean,
    val records: BossRushRecords,
    val saveFiles: List<BossRushFileSummary>,
    /** The boss whose times the panel shows; always one the board shows. */
    val selectedId: String,
    /** Story steps still to do before the round opens, when story gating is on; empty when open. */
    val lockedBy: List<String> = emptyList(),
) {
    val board: BossRushBoard = BossRushBoard(data.catalog, records, heroMode)

    val rows: List<BossRow> get() = board.rows

    val selected: BossRow get() = rows.firstOrNull { it.boss.id == selectedId } ?: rows.first()

    val locked: Boolean get() = lockedBy.isNotEmpty()
}

/** What the last change replaced, until its snackbar goes or its Undo is tapped. */
data class RecordChange(
    val kind: Kind,
    val saveFile: SaveFile,
    val bossId: String,
    val bossTitle: String,
    /** The time added, set or deleted, in hundredths. */
    val time: Int,
    /** For an added time: it's the boss's new best (and not its first). */
    val newBest: Boolean,
    /** The boss's entries before the change, put back by undo. */
    val before: List<RecordEntry>,
) {
    enum class Kind { ADDED, EDITED, DELETED }
}

/**
 * The Lightning Round Records board for the active save file. The file, its records, its Hero Mode
 * setting and its story gate are read together inside one switch, so a new file never shows the old
 * one's records or mode. Every change is one write to that file's records, or to its settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BossRushViewModel(
    private val storage: BossRushStorage,
    private val settings: SaveFileSettingsStorage,
    private val storyGate: (String) -> Flow<StoryGate> = { flowOf(StoryGate.OFF) },
    private val loadData: suspend () -> BossRushData = { BossRushRepository.load(storage.game) },
    private val today: () -> Long = ::todayEpochDay,
) : ViewModel() {
    private val game = storage.game
    private val data = MutableStateFlow<BossRushData?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private val selectedId = MutableStateFlow<String?>(null)

    private data class Active(val file: SaveFile, val records: BossRushRecords, val settings: SaveFileSettings, val gate: StoryGate)

    private val active: Flow<Active> = storage.activeSaveFile
        .map(game::saveFile)
        .flatMapLatest { file ->
            combine(storage.records(file.id), settings.settings(file.id), storyGate(file.id)) { r, s, g -> Active(file, r, s, g) }
        }

    private val summaries: Flow<List<Pair<BossRushRecords, SaveFileSettings>>> = combine(
        game.saveFiles.map { f -> combine(storage.records(f.id), settings.settings(f.id)) { r, s -> r to s } },
    ) { it.toList() }

    val state: StateFlow<BossRushUiState?> = combine(data.filterNotNull(), active, summaries, selectedId) { data, a, all, selected ->
        val shown = data.catalog.bosses(a.settings.heroMode)
        BossRushUiState(
            data = data,
            saveFile = a.file,
            heroMode = a.settings.heroMode,
            records = a.records,
            saveFiles = game.saveFiles.zip(all) { f, (r, s) ->
                val board = BossRushBoard(data.catalog, r, s.heroMode)
                BossRushFileSummary(f, s.heroMode, board.set, board.slots)
            },
            selectedId = selected?.takeIf { id -> shown.any { it.id == id } } ?: shown.first().id,
            lockedBy = BossRushProgressProvider.lockedBy(data, a.gate),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _lastChange = MutableStateFlow<RecordChange?>(null)

    /** The last change, for its snackbar's Undo. */
    val lastChange: StateFlow<RecordChange?> = _lastChange

    init {
        viewModelScope.launch {
            runCatching { loadData() }
                .onSuccess { data.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    fun select(bossId: String) {
        selectedId.value = bossId
    }

    /** Adds [time] (hundredths) for [bossId], dated today, on the file on screen. */
    fun addTime(bossId: String, time: Int): Job? {
        val state = state.value ?: return null
        val boss = state.data.catalog.boss(bossId) ?: return null
        val day = today()
        return change(state.saveFile, bossId) { before ->
            val after = before.withAdded(bossId, time, day)
            val best = before.best(bossId)
            RecordChange(RecordChange.Kind.ADDED, state.saveFile, bossId, boss.title, time, newBest = best != null && time < best.time, before = before.of(bossId)) to after
        }
    }

    /** Sets entry [entryId]'s time to [time]. */
    fun editTime(bossId: String, entryId: Int, time: Int): Job? {
        val state = state.value ?: return null
        val boss = state.data.catalog.boss(bossId) ?: return null
        return change(state.saveFile, bossId) { before ->
            if (before.of(bossId).none { it.id == entryId }) return@change null
            RecordChange(RecordChange.Kind.EDITED, state.saveFile, bossId, boss.title, time, newBest = false, before = before.of(bossId)) to
                before.withEdited(bossId, entryId, time)
        }
    }

    fun deleteEntry(bossId: String, entryId: Int): Job? {
        val state = state.value ?: return null
        val boss = state.data.catalog.boss(bossId) ?: return null
        return change(state.saveFile, bossId) { before ->
            val entry = before.of(bossId).firstOrNull { it.id == entryId } ?: return@change null
            RecordChange(RecordChange.Kind.DELETED, state.saveFile, bossId, boss.title, entry.time, newBest = false, before = before.of(bossId)) to
                before.without(bossId, entryId)
        }
    }

    /**
     * One write: [transform] works on what's stored, so quick entries can't undo each other, and
     * gives the change for undo with the new records, or null to leave them be.
     */
    private fun change(file: SaveFile, bossId: String, transform: (BossRushRecords) -> Pair<RecordChange, BossRushRecords>?): Job =
        viewModelScope.launch {
            var made: RecordChange? = null
            storage.swap(file.id) { before ->
                val result = transform(before)
                made = result?.first
                result?.second ?: before
            }
            made?.let { _lastChange.value = it }
        }

    /** Puts back the boss's times from before [change], on the file it was made on, if it's still the last one. */
    fun undo(change: RecordChange): Job? {
        val last = _lastChange.value?.takeIf { it == change } ?: return null
        _lastChange.value = null
        return viewModelScope.launch { storage.swap(last.saveFile.id) { it.withEntries(last.bossId, last.before) } }
    }

    /** Its snackbar has gone, so the undo with it. */
    fun dismissChange(change: RecordChange) {
        _lastChange.update { if (it == change) null else it }
    }

    /** Marks the file on screen as a Hero Mode file, or not. Records are never touched: Demise's is kept, only hidden. */
    fun setHeroMode(on: Boolean): Job? {
        val state = state.value ?: return null
        return viewModelScope.launch { settings.update(state.saveFile.id) { it.copy(heroMode = on) } }
    }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }
}
