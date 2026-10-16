package gx.zelda.fairies.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.fairies.data.FairyRepository
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.fairies.model.FairyCatalog
import gx.zelda.core.SaveFile
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

    private val activeUnlocked: Flow<Set<String>> = activeFile.flatMapLatest { storage.unlocked(it.id) }

    private val unlockedByFile: Flow<List<Pair<SaveFile, Set<String>>>> = combine(
        storage.game.saveFiles.map { file -> storage.unlocked(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<FairyUiState?> = combine(
        catalog.filterNotNull(),
        activeFile,
        activeUnlocked,
        unlockedByFile,
    ) { catalog, file, unlocked, byFile ->
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

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }
}
