package gx.zelda.mainquest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.SaveFile
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestCatalog
import gx.zelda.mainquest.model.MainQuestProgress
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** A save file as the picker shows it: how far it is through each checklist. */
data class MainQuestSaveSummary(val file: SaveFile, val counts: Map<MainQuestSection, Int>)

data class MainQuestUiState(
    val catalog: MainQuestCatalog,
    /** What the active save file has done. */
    val progress: MainQuestProgress,
    val saveFile: SaveFile,
    val saveFiles: List<MainQuestSaveSummary>,
) {
    fun isDone(section: MainQuestSection, id: String): Boolean = id in progress.done(section)

    fun doneCount(section: MainQuestSection): Int = catalog.doneCount(section, progress.done(section))

    val albumCount: Int get() = catalog.albumCount(progress.memories)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainQuestViewModel(
    private val storage: MainQuestStorage,
    private val loadCatalog: suspend () -> MainQuestCatalog = { MainQuestRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<MainQuestCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    private val activeProgress: Flow<MainQuestProgress> = activeFile.flatMapLatest { storage.progress(it.id) }

    private val progressByFile: Flow<List<Pair<SaveFile, MainQuestProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<MainQuestUiState?> = combine(
        catalog.filterNotNull(),
        activeFile,
        activeProgress,
        progressByFile,
    ) { catalog, file, progress, byFile ->
        MainQuestUiState(
            catalog = catalog,
            progress = progress,
            saveFile = file,
            saveFiles = byFile.map { (f, p) ->
                MainQuestSaveSummary(f, MainQuestSection.entries.associateWith { catalog.doneCount(it, p.done(it)) })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { loadCatalog() }
                .onSuccess { catalog.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    /** Edits always target the file that was on screen when the user acted. */
    fun setDone(section: MainQuestSection, id: String, done: Boolean) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setDone(file, section, id, done) }
    }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }
}
