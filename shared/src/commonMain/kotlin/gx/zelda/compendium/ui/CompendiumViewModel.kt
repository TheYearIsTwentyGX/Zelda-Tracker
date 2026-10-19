package gx.zelda.compendium.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.compendium.logic.CategoryProgress
import gx.zelda.compendium.logic.CompendiumFilter
import gx.zelda.compendium.logic.CompendiumSection
import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry
import gx.zelda.compendium.model.EntrySource
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.SaveFile
import gx.zelda.core.SetSnapshot
import gx.zelda.core.countOf
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

    /** What the tab shows under its headers, after the search and the Missing chip. */
    val sections: List<CompendiumSection> =
        CompendiumFilter.sections(entries, category, query, missingOnly, photographed, saveFile.masterMode)

    /** True when a search or the Missing chip is on, so a toggle acts on the rows shown only. */
    val filtered: Boolean get() = query.isNotBlank() || missingOnly

    /**
     * What a toggle acts on: the entries the search and chip leave showing on this tab, or only
     * those under [section]'s header (by [CompendiumSection.key]). Only this file's own entries are
     * listed, so Master Mode's extra ones are only ever marked on a Master Mode file.
     */
    fun bulkEntries(section: String? = null): List<CompendiumEntry> =
        if (section == null) sections.flatMap { it.entries } else sections.firstOrNull { it.key == section }?.entries.orEmpty()

    /** The toggle for [bulkEntries]. Its text names the tab or header, never an entry. */
    fun bulkScope(section: String? = null): BulkScope {
        val label = section?.let { key -> sections.firstOrNull { it.key == key }?.title } ?: category?.label ?: "Everything"
        // Master Mode's extra entries that this tab or header would hold, whichever file is on screen.
        val extra = catalog.entries.count { entry ->
            entry.source == EntrySource.MASTER_MODE &&
                if (section != null) CompendiumFilter.sectionKey(entry) == section else category == null || entry.category == category
        }
        val note = when {
            extra == 0 -> null
            saveFile.masterMode -> "The counts follow this save file, a Master Mode one, so they take in Master Mode's ${countOf(extra, "extra entry", "extra entries")} here."
            else -> "The counts follow this save file, which isn't in Master Mode: Master Mode's ${countOf(extra, "extra entry", "extra entries")} here aren't on it, so they aren't changed."
        }
        return BulkScope.of(
            label = label,
            items = bulkEntries(section),
            noun = "entry",
            plural = "entries",
            filtered = filtered,
            note = note,
            isDone = { it.id in photographed },
            isStarted = { it.id in photographed },
        )
    }
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

    /** The file and its entries together, so a switch never pairs the new file with the old file's photos. */
    private val activePhotographed: Flow<Pair<SaveFile, Set<String>>> =
        activeFile.flatMapLatest { file -> storage.photographed(file.id).map { file to it } }

    private val photographedByFile: Flow<List<Pair<SaveFile, Set<String>>>> = combine(
        storage.game.saveFiles.map { file -> storage.photographed(file.id).map { file to it } },
    ) { it.toList() }

    private val viewOptions = combine(category, query, missingOnly, ::ViewOptions)

    val state: StateFlow<CompendiumUiState?> = combine(
        catalog.filterNotNull(),
        activePhotographed,
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

    /**
     * Edits always target the file that was on screen when the user acted. Each write returns its
     * job, so a test can wait for it to finish before resetting the Main dispatcher.
     */
    fun setPhotographed(entryId: String, photographed: Boolean): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setPhotographed(file, entryId, photographed) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<SetSnapshot<String>>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Marks every entry [CompendiumUiState.bulkEntries] gives for [section] photographed, or none
     * of them, on the file on screen. It's one write, and what it replaced is kept in [bulkChange]
     * for an undo. Null when there's nothing to change.
     */
    fun setAllPhotographed(section: String?, photographed: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.bulkEntries(section).map { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(section)
        return viewModelScope.launch {
            val snapshot = storage.setAllPhotographed(state.saveFile.id, ids, photographed)
            val changed = ids.count { (it in snapshot.members) != photographed }
            if (changed > 0) {
                val action = if (photographed) BulkAction.MARK else BulkAction.CLEAR
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

    fun setCategory(value: CompendiumCategory?) { category.value = value }

    fun setQuery(text: String) { query.value = text }

    fun setMissingOnly(value: Boolean) { missingOnly.value = value }
}
