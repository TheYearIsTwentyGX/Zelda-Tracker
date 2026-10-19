package gx.zelda.koroks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.DiscoverySnapshot
import gx.zelda.core.DiscoveryState
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.HestuPlan
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokData
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
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

/** A save file as the picker shows it: how many seeds it has collected. */
data class KorokSaveSummary(val file: SaveFile, val collected: Int)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class KorokFilters(
    val query: String = "",
    val incompleteOnly: Boolean = false,
    /** Puzzle type ids to show; empty shows every type. */
    val types: Set<String> = emptySet(),
    /** Undiscovered Koroks the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
)

/** One region's tallies, counting every Korok in it whatever the filters show. */
data class RegionCount(val found: Int, val collected: Int, val total: Int)

data class KorokUiState(
    val data: KorokData,
    /** The active save file's seeds and Hestu purchases. */
    val progress: KorokProgress,
    val prefs: KorokPrefs,
    val saveFile: SaveFile,
    val saveFiles: List<KorokSaveSummary>,
    val filters: KorokFilters,
) {
    val catalog get() = data.catalog

    val hestu: HestuPlan get() = HestuPlan.of(catalog, progress)

    val collected: Int get() = catalog.count(progress.completed)

    val found: Int get() = catalog.koroks.count { progress.isFound(it.id) }

    /**
     * True when [korok]'s row shows only its region: spoilers are hidden, it hasn't been found
     * and the user hasn't tapped to reveal it.
     */
    fun isHidden(korok: Korok): Boolean =
        isSpoilerHidden(prefs.hideUndiscovered, progress.isFound(korok.id), korok.id in filters.revealed)

    /**
     * Every Korok narrowed by search and filters, worked out once per state. A hidden Korok only
     * matches on its region and never on a puzzle-type filter, so neither can leak what the row hides.
     */
    val matching: List<Korok> = catalog.koroks.filter { korok ->
        val hidden = isHidden(korok)
        (!filters.incompleteOnly || korok.id !in progress.completed) &&
            (filters.types.isEmpty() || (!hidden && korok.puzzleType in filters.types)) &&
            matchesQuery(filters.query, hidden, korok.region) {
                listOf(data.typeOf(korok).name, korok.nearestPlace, korok.region, korok.internalType, korok.id)
            }
    }

    /** [matching] in region order. */
    val groups: List<Pair<String, List<Korok>>> = catalog.byRegion(matching)

    val regionCounts: Map<String, RegionCount> = catalog.koroks.groupBy { it.region }.mapValues { (_, koroks) ->
        RegionCount(
            found = koroks.count { progress.isFound(it.id) },
            collected = koroks.count { it.id in progress.completed },
            total = koroks.size,
        )
    }

    /** True when a search or filter chip is on, so a toggle acts on the rows shown only. */
    val filtered: Boolean get() = filters.query.isNotBlank() || filters.incompleteOnly || filters.types.isNotEmpty()

    /** What a toggle acts on: the Koroks the filters leave showing (hidden ones included), or only those under [region]. */
    fun bulkKoroks(region: String? = null): List<Korok> =
        if (region == null) matching else groups.firstOrNull { it.first == region }?.second.orEmpty()

    /**
     * The toggle for [bulkKoroks]: collecting each seed, which finds its Korok too, or clearing it
     * back to found, as unticking a row does. Its text names the list or region, never a Korok.
     */
    fun bulkScope(region: String? = null): BulkScope {
        val scope = BulkScope.of(
            label = region?.let { "$it Tower" } ?: "Korok Seeds",
            items = bulkKoroks(region),
            noun = "seed",
            filtered = filtered,
            isDone = { it.id in progress.completed },
            isStarted = { it.id in progress.completed },
        )
        return scope.copy(note = "Each Korok stays marked found.".takeIf { scope.action == BulkAction.CLEAR })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KorokViewModel(
    private val storage: KorokStorage,
    private val loadData: suspend () -> KorokData = { KorokRepository.loadData(storage.game) },
) : ViewModel() {
    private val data = MutableStateFlow<KorokData?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(KorokFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's seeds. */
    private val activeProgress: Flow<Pair<SaveFile, KorokProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, KorokProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<KorokUiState?> = combine(
        data.filterNotNull(),
        activeProgress,
        storage.prefs,
        progressByFile,
        filters,
    ) { data, (file, progress), prefs, byFile, filters ->
        KorokUiState(
            data = data,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> KorokSaveSummary(f, data.catalog.count(p.completed)) },
            filters = filters,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { loadData() }
                .onSuccess { data.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    /**
     * Edits always target the file that was on screen when the user acted. Each write returns its
     * job, so a test can wait for it to finish before resetting the Main dispatcher.
     */
    fun setCompleted(korokId: String, completed: Boolean): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setCompleted(file, korokId, completed) }
    }

    fun setState(korokId: String, korokState: DiscoveryState): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setState(file, korokId, korokState) }
    }

    fun setBought(pouchId: String, upgrades: Int): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setBought(file, pouchId, upgrades) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<DiscoverySnapshot>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Collects every seed [KorokUiState.bulkKoroks] gives for [region], or clears them back to
     * found, on the file on screen. It's one write, and what it replaced is kept in [bulkChange]
     * for an undo. Null when there's nothing to change.
     */
    fun setAllCompleted(region: String?, completed: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.bulkKoroks(region).map { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(region)
        return viewModelScope.launch {
            val snapshot = storage.setAllCompleted(state.saveFile.id, ids, completed)
            val changed = ids.count { (it in snapshot.completed.members) != completed }
            if (changed > 0) {
                val action = if (completed) BulkAction.MARK else BulkAction.CLEAR
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

    fun setHideUndiscovered(hide: Boolean): Job = viewModelScope.launch { storage.updatePrefs { it.copy(hideUndiscovered = hide) } }

    /** Shows one undiscovered Korok's row until the tracker is closed; nothing is saved. */
    fun reveal(korokId: String) = filters.update { it.copy(revealed = it.revealed + korokId) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setIncompleteOnly(on: Boolean) = filters.update { it.copy(incompleteOnly = on) }

    fun toggleType(typeId: String) = filters.update {
        it.copy(types = if (typeId in it.types) it.types - typeId else it.types + typeId)
    }

    fun clearTypes() = filters.update { it.copy(types = emptySet()) }
}
