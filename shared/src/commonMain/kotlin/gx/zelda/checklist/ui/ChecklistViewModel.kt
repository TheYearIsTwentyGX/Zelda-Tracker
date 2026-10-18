package gx.zelda.checklist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistCount
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.ChecklistSection
import gx.zelda.checklist.model.SummaryValue
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
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

/** A save file as the picker shows it: steps done of all. */
data class ChecklistSaveSummary(val file: SaveFile, val count: ChecklistCount)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class ChecklistFilters(
    /** The section tab showing, by id; null is "All" (and the only choice for a one-section file). */
    val section: String? = null,
    val query: String = "",
    val incompleteOnly: Boolean = false,
    val missableOnly: Boolean = false,
    /** Rows with nothing done that the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
)

data class ChecklistUiState(
    val catalog: ChecklistCatalog,
    /** The active save file's progress. */
    val progress: ChecklistProgress,
    val prefs: ChecklistPrefs,
    val saveFile: SaveFile,
    val saveFiles: List<ChecklistSaveSummary>,
    val filters: ChecklistFilters,
) {
    /** The section on screen; null for "All". A stale id (the file changed) falls back to "All". */
    val section: ChecklistSection? = filters.section?.let(catalog::section)

    /** The items of the section on screen, whatever the other filters leave. "All" leaves out separate sections, as its count does. */
    val sectionItems: List<ChecklistItem> = section?.items ?: catalog.counted

    /** Tabs only when there's more than one section. */
    val showTabs: Boolean get() = catalog.sections.size > 1

    val count: ChecklistCount = catalog.count(progress)

    /** The file's summaries, then the section's own on its tab. */
    val summaries: List<SummaryValue> =
        (catalog.summaries + listOfNotNull(section?.summary)).map { catalog.evaluate(it, progress) }

    /**
     * True when [item]'s row shows only its heading: spoilers are hidden, nothing on it is done and
     * the user hasn't tapped to reveal it.
     */
    fun isHidden(item: ChecklistItem): Boolean =
        isSpoilerHidden(prefs.hideUnchecked, progress.isStarted(item), item.id in filters.revealed)

    /** What the filters leave showing, by heading. */
    val groups: List<Pair<String, List<ChecklistItem>>> = catalog.grouped(
        sectionItems.filter { item ->
            val hidden = isHidden(item)
            // A hidden row never matches the missable chip, so the chip can't give away which ones are.
            (!filters.incompleteOnly || !progress.isDone(item)) &&
                (!filters.missableOnly || (!hidden && item.missable)) &&
                matchesQuery(filters.query, hidden, catalog.groupOf(item)) { searchText(item) }
        },
    )

    /** Each heading's tallies over the section on screen, whatever the other filters show. */
    val groupCounts: Map<String, ChecklistCount> =
        sectionItems.groupBy(catalog::groupOf).mapValues { (_, items) -> catalog.count(progress, items) }

    fun sectionCount(section: ChecklistSection): ChecklistCount = catalog.count(progress, section.items)

    /** The names of [item]'s requirements, as its row and page show them; a hidden one isn't named. */
    fun requirements(item: ChecklistItem): List<Pair<String, Boolean>> = item.requires.mapNotNull(catalog::item).map {
        (if (isHidden(it)) catalog.hiddenLabelOf(it) else it.name) to progress.isDone(it)
    }

    private fun searchText(item: ChecklistItem): List<String?> =
        listOf(item.name, item.area, item.region, item.howTo, item.after, item.reward, item.notes, catalog.sectionOf(item).title) +
            item.tags + item.tiers.map { it.name }
}

/** Any checklist tracker: one screen and one detail page for every [gx.zelda.core.TrackerKind.CHECKLIST]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistViewModel(
    private val storage: ChecklistStorage,
    private val loadCatalog: suspend () -> ChecklistCatalog = { ChecklistRepository.load(storage.dataFile) },
) : ViewModel() {
    private val catalog = MutableStateFlow<ChecklistCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(ChecklistFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's ticks. */
    private val activeProgress: Flow<Pair<SaveFile, ChecklistProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, ChecklistProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<ChecklistUiState?> = combine(
        catalog.filterNotNull(),
        activeProgress,
        storage.prefs,
        progressByFile,
        filters,
    ) { catalog, (file, progress), prefs, byFile, filters ->
        ChecklistUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> ChecklistSaveSummary(f, catalog.count(p)) },
            filters = filters,
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
    fun setDone(itemId: String, done: Boolean): Job? {
        val state = state.value ?: return null
        val item = state.catalog.item(itemId) ?: return null
        return viewModelScope.launch { storage.setDone(state.saveFile.id, item, done) }
    }

    /** Sets how many of a tiered item's tiers are done (0 clears it). */
    fun setLevel(itemId: String, level: Int): Job? {
        val state = state.value ?: return null
        val item = state.catalog.item(itemId) ?: return null
        return viewModelScope.launch { storage.setLevel(state.saveFile.id, item, level) }
    }

    fun setHideUnchecked(hide: Boolean): Job = viewModelScope.launch { storage.updatePrefs { it.copy(hideUnchecked = hide) } }

    /** Shows one row until the tracker is closed; nothing is saved. */
    fun reveal(itemId: String) = filters.update { it.copy(revealed = it.revealed + itemId) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setSection(id: String?) = filters.update { it.copy(section = id) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setIncompleteOnly(on: Boolean) = filters.update { it.copy(incompleteOnly = on) }

    fun setMissableOnly(on: Boolean) = filters.update { it.copy(missableOnly = on) }
}
