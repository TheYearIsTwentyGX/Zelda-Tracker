package gx.zelda.fairies.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.SaveFile
import gx.zelda.core.SetSnapshot
import gx.zelda.fairies.data.FairyRepository
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.fairies.model.FairyCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A save file as the picker shows it: how many fountains it has opened. */
data class FairySaveSummary(val file: SaveFile, val unlocked: Int)

data class FairyUiState(
    val catalog: FairyCatalog,
    /** Ids of the fountains opened on the active save file. */
    val unlocked: Set<String>,
    val saveFile: SaveFile,
    val saveFiles: List<FairySaveSummary>,
) {
    val unlockedCount: Int get() = catalog.unlockedCount(unlocked)

    /**
     * The one "mark all" toggle: every fountain open (BotW) or every fairy freed (TotK). In BotW,
     * where each fountain costs more than the last, the note says the costs stop once all are open.
     */
    val bulkScope: BulkScope
        get() {
            val paid = catalog.isPaid
            val scope = BulkScope.of(
                label = if (paid) "Great Fairy Fountains" else "Great Fairies",
                items = catalog.fairies,
                noun = if (paid) "fountain" else "Great Fairy",
                plural = if (paid) "fountains" else "Great Fairies",
                isDone = { it.id in unlocked },
                isStarted = { it.id in unlocked },
            )
            val note = when {
                !paid -> null
                scope.action == BulkAction.MARK ->
                    "Once every fountain is open there are no opening costs left: the ${rupees(catalog.remainingCost(unlockedCount))} still to pay drops to nothing."
                else -> "With none open, the next fountain costs ${rupees(catalog.unlockCosts.first())} again."
            }
            return scope.copy(note = note)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FairyViewModel(
    private val storage: FairyStorage,
    private val loadCatalog: suspend () -> FairyCatalog = { FairyRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<FairyCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its fountains together, so a switch never pairs the new file with the old file's. */
    private val activeUnlocked: Flow<Pair<SaveFile, Set<String>>> =
        activeFile.flatMapLatest { file -> storage.unlocked(file.id).map { file to it } }

    private val unlockedByFile: Flow<List<Pair<SaveFile, Set<String>>>> = combine(
        storage.game.saveFiles.map { file -> storage.unlocked(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<FairyUiState?> = combine(
        catalog.filterNotNull(),
        activeUnlocked,
        unlockedByFile,
    ) { catalog, (file, unlocked), byFile ->
        FairyUiState(
            catalog = catalog,
            unlocked = unlocked,
            saveFile = file,
            saveFiles = byFile.map { (f, ids) -> FairySaveSummary(f, catalog.unlockedCount(ids)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { loadCatalog() }
                .onSuccess { catalog.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    /**
     * Edits always target the file that was on screen when the user acted. Each write returns its
     * job, so a test can wait for it to finish before resetting the Main dispatcher.
     */
    fun setUnlocked(fairyId: String, unlocked: Boolean): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setUnlocked(file, fairyId, unlocked) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<SetSnapshot<String>>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Opens every fountain, or closes them all, on the file on screen. It's one write, and what it
     * replaced is kept in [bulkChange] for an undo.
     */
    fun setAllUnlocked(unlocked: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.catalog.fairies.map { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope
        return viewModelScope.launch {
            val snapshot = storage.setAllUnlocked(state.saveFile.id, ids, unlocked)
            val changed = ids.count { (it in snapshot.members) != unlocked }
            if (changed > 0) {
                val action = if (unlocked) BulkAction.MARK else BulkAction.CLEAR
                _bulkChange.value = BulkChange(action, changed, scope.noun, scope.plural, state.saveFile, snapshot)
            }
        }
    }

    /** Puts back what [change] replaced, on the file it was made on, if it's still the last one. */
    fun undoBulkChange(change: BulkChange<*>): Job? {
        val last = _bulkChange.value?.takeIf { it == change } ?: return null
        _bulkChange.value = null
        return viewModelScope.launch { storage.restore(last.saveFile.id, last.snapshot) }
    }

    /** Its snackbar has gone, so the undo with it. */
    fun dismissBulkChange(change: BulkChange<*>) {
        _bulkChange.update { if (it == change) null else it }
    }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }
}
