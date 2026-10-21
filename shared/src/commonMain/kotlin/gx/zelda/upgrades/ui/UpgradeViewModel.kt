package gx.zelda.upgrades.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
import gx.zelda.armor.ui.OwnershipFilter
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.MapSnapshot
import gx.zelda.core.SaveFile
import gx.zelda.core.groupByRegion
import gx.zelda.upgrades.data.UpgradeRepository
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.model.ItemCopy
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import gx.zelda.upgrades.model.UpgradeSection
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

/** A save file as the picker shows it. */
data class UpgradeFileSummary(val file: SaveFile, val obtained: Int)

/** One section card on the list: its header and the copies under it. */
data class UpgradeGroup(val section: UpgradeSection, val copies: List<ItemCopy>)

data class UpgradeUiState(
    val catalog: UpgradeCatalog,
    val progress: UpgradeProgress,
    val prefs: UpgradePrefs,
    val saveFile: SaveFile,
    val saveFiles: List<UpgradeFileSummary>,
    val totalsMode: UpgradeTotalsMode,
    val ownershipFilter: OwnershipFilter,
    val query: String,
) {
    /** Every copy the section chips leave in, which the list's count is out of. */
    val visible: List<ItemCopy> = UpgradeCalculator.visibleItems(catalog, prefs).flatMap(progress::copiesOf)

    /** What the list shows: [visible] narrowed by the ownership filter and search, under section headers. */
    val groups: List<UpgradeGroup> = run {
        val q = query.trim()
        val matching = visible.filter { copy ->
            val owned = progress.owned(copy)
            val ownershipOk = when (ownershipFilter) {
                OwnershipFilter.ALL -> true
                OwnershipFilter.OWNED -> owned
                OwnershipFilter.MISSING -> !owned
            }
            ownershipOk && (q.isEmpty() || copy.label.contains(q, true) || copy.item.levels.any { it.name.contains(q, true) })
        }
        groupByRegion(catalog.sections.map { it.id }, matching) { it.item.section }
            .map { (id, copies) -> UpgradeGroup(catalog.section(id)!!, copies) }
    }

    /** True when a search or the Owned/Missing filter is on, so a toggle acts on the rows shown only. */
    val filtered: Boolean get() = query.isNotBlank() || ownershipFilter != OwnershipFilter.ALL

    /** What a "mark all collected" toggle acts on: the copies the list shows, or one section's. */
    fun bulkCopies(section: String? = null): List<ItemCopy> =
        if (section == null) groups.flatMap { it.copies } else groups.firstOrNull { it.section.id == section }?.copies.orEmpty()

    /**
     * The toggle for [bulkCopies]: collecting each copy, or uncollecting it, as its row's box does.
     * Upgrade levels are left alone, both ways.
     */
    fun bulkScope(section: String? = null): BulkScope {
        val hidden = catalog.optionalSections.filter { !prefs.shows(it.id) }.mapNotNull { it.optional }
        val note = listOfNotNull(
            "${hidden.joinToString(" and ")} the filters hide ${if (hidden.size == 1) "isn't" else "aren't"} changed."
                .takeIf { section == null && hidden.isNotEmpty() },
            "Upgrade levels aren't changed.",
        )
        return BulkScope.of(
            label = section?.let { id -> catalog.section(id)?.title } ?: "Item Upgrades",
            items = bulkCopies(section),
            noun = "item",
            filtered = filtered,
            note = note.joinToString(" "),
            isDone = progress::owned,
            isStarted = progress::owned,
        )
    }
}

private data class ViewOptions(val totalsMode: UpgradeTotalsMode, val ownershipFilter: OwnershipFilter, val query: String)

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeViewModel(
    private val storage: UpgradeStorage,
    private val loadCatalog: suspend () -> UpgradeCatalog = { UpgradeRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<UpgradeCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val totalsMode = MutableStateFlow(UpgradeTotalsMode.COLLECTED)
    private val ownershipFilter = MutableStateFlow(OwnershipFilter.ALL)
    private val query = MutableStateFlow("")

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's progress. */
    private val activeProgress: Flow<Pair<SaveFile, UpgradeProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val summaries: Flow<List<UpgradeFileSummary>> = combine(
        storage.game.saveFiles.map { file ->
            // Items with any copy owned: a copy's key is its item's id, then "#n" after the first.
            storage.progress(file.id).map { p ->
                UpgradeFileSummary(file, p.items.filterValues { it.owned }.keys.map { it.substringBefore('#') }.toSet().size)
            }
        },
    ) { it.toList() }

    private val viewOptions = combine(totalsMode, ownershipFilter, query, ::ViewOptions)

    val state: StateFlow<UpgradeUiState?> = combine(
        catalog.filterNotNull(),
        activeProgress,
        storage.prefs,
        summaries,
        viewOptions,
    ) { catalog, (file, progress), prefs, summaries, options ->
        UpgradeUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = summaries,
            totalsMode = options.totalsMode,
            ownershipFilter = options.ownershipFilter,
            query = options.query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { loadCatalog() }
                .onSuccess { catalog.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    fun setOwned(key: String, owned: Boolean): Job? = edit { it.withCopy(key) { p -> p.copy(owned = owned) } }

    /** Sets a copy's level, collecting it, as the armor list's stars do. */
    fun setLevel(key: String, level: Int): Job? = edit { it.withCopy(key) { p -> p.copy(owned = true, level = level) } }

    /** Tracks one more copy of a pouch item, e.g. "Seed Satchel #2". */
    fun addCopy(itemId: String): Job? {
        val item = state.value?.catalog?.item(itemId)?.takeIf { it.multiple } ?: return null
        return edit { it.withCopyAdded(item) }
    }

    /** Stops tracking copy [number] (2 or more) of a pouch item; later copies move down one. */
    fun removeCopy(itemId: String, number: Int): Job? {
        val item = state.value?.catalog?.item(itemId) ?: return null
        return edit { it.withCopyRemoved(item, number) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<MapSnapshot<String, PieceProgress>>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Marks every copy [UpgradeUiState.bulkCopies] gives for [section] collected, or uncollected,
     * on the file on screen, leaving levels alone. One write; what it replaced is kept in
     * [bulkChange] for an undo. Null when there's nothing to change.
     */
    fun setAllOwned(section: String?, owned: Boolean): Job? {
        val state = state.value ?: return null
        val keys = state.bulkCopies(section).map { it.key }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(section)
        return viewModelScope.launch {
            val snapshot = storage.setAllOwned(state.saveFile.id, keys, owned)
            val changed = keys.count { (snapshot.before[it]?.owned ?: false) != owned }
            if (changed > 0) {
                val action = if (owned) BulkAction.MARK else BulkAction.CLEAR
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

    /** Shows or hides an optional section (the potions) in the list, the totals and the overview. */
    fun setSectionShown(sectionId: String, shown: Boolean): Job = editPrefs {
        it.copy(hiddenSections = if (shown) it.hiddenSections - sectionId else it.hiddenSections + sectionId)
    }

    fun setMaterialSort(sort: MaterialSort): Job = editPrefs { it.copy(materialSort = sort) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setTotalsMode(mode: UpgradeTotalsMode) { totalsMode.value = mode }

    fun setOwnershipFilter(filter: OwnershipFilter) { ownershipFilter.value = filter }

    fun setQuery(text: String) { query.value = text }

    /**
     * Edits always target the file that was on screen when the user acted, in one write. Each
     * returns its job, so a test can wait for it before resetting the Main dispatcher.
     */
    private fun edit(transform: (UpgradeProgress) -> UpgradeProgress): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.update(file, transform) }
    }

    private fun editPrefs(transform: (UpgradePrefs) -> UpgradePrefs): Job = viewModelScope.launch { storage.updatePrefs(transform) }
}
