package gx.zelda.compendium.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.compendium.logic.CategoryProgress
import gx.zelda.compendium.logic.CompendiumFilter
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.core.SaveFile
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

/** A save file as the picker shows it: how much of its own Compendium is photographed. */
data class CompendiumSaveSummary(val file: SaveFile, val photographed: Int, val total: Int)

data class CompendiumUiState(
    val catalog: CompendiumCatalog,
    /** Ids photographed on the active save file. */
    val photographed: Set<String>,
    val saveFile: SaveFile,
    val saveFiles: List<CompendiumSaveSummary>,
    /** The selected tab; null is All. */
    val category: CompendiumCategory?,
    val query: String,
    val missingOnly: Boolean,
) {
    /** The active file's entries, in its own order (Master Mode has five more). */
    val entries: List<CompendiumEntry> = catalog.entriesFor(saveFile)

    val progress: List<CategoryProgress> = CompendiumFilter.progress(entries, photographed)

    val photographedCount: Int get() = progress.sumOf { it.photographed }
}

private data class ViewOptions(val category: CompendiumCategory?, val query: String, val missingOnly: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class CompendiumViewModel(
    private val storage: CompendiumStorage,
    private val loadCatalog: suspend () -> CompendiumCatalog = { CompendiumRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<CompendiumCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val category = MutableStateFlow<CompendiumCategory?>(null)
    private val query = MutableStateFlow("")
    private val missingOnly = MutableStateFlow(false)

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    private val activePhotographed: Flow<Set<String>> = activeFile.flatMapLatest { storage.photographed(it.id) }

    private val photographedByFile: Flow<List<Pair<SaveFile, Set<String>>>> = combine(
        storage.game.saveFiles.map { file -> storage.photographed(file.id).map { file to it } },
    ) { it.toList() }

    private val viewOptions = combine(category, query, missingOnly, ::ViewOptions)

    val state: StateFlow<CompendiumUiState?> = combine(
        catalog.filterNotNull(),
        combine(activeFile, activePhotographed, ::Pair),
        photographedByFile,
        viewOptions,
    ) { catalog, (file, photographed), byFile, options ->
        CompendiumUiState(
            catalog = catalog,
            photographed = photographed,
            saveFile = file,
            saveFiles = byFile.map { (f, ids) ->
                CompendiumSaveSummary(f, catalog.photographedCount(f, ids), catalog.entriesFor(f).size)
            },
            category = options.category,
            query = options.query,
            missingOnly = options.missingOnly,
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
    fun setPhotographed(entryId: String, photographed: Boolean) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setPhotographed(file, entryId, photographed) }
    }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }

    fun setCategory(value: CompendiumCategory?) { category.value = value }

    fun setQuery(text: String) { query.value = text }

    fun setMissingOnly(value: Boolean) { missingOnly.value = value }
}
