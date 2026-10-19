package gx.zelda.armor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.armor.data.ArmorRepository
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.logic.ArmorGroup
import gx.zelda.armor.logic.ArmorGrouping
import gx.zelda.armor.logic.TotalsMode
import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.armor.model.ArmorPrefs
import gx.zelda.armor.model.ArmorProgress
import gx.zelda.armor.model.ArmorGroupBy
import gx.zelda.armor.model.ArmorOrder
import gx.zelda.armor.model.ArmorPiece
import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.MapSnapshot
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OwnershipFilter(val label: String) { ALL("All"), OWNED("Owned"), MISSING("Missing") }

/** A save file as the picker shows it. */
data class SaveFileSummary(val file: SaveFile, val collected: Int)

data class ArmorUiState(
    val catalog: ArmorCatalog,
    /** The active save file's pieces plus the shared viewing preferences. */
    val progress: ArmorProgress,
    val saveFile: SaveFile,
    val saveFiles: List<SaveFileSummary>,
    val totalsMode: TotalsMode,
    val ownershipFilter: OwnershipFilter,
    val query: String,
) {
    /** The orders this game's data supports. */
    val orders: List<ArmorOrder> get() = if (catalog.hasGameOrder) ArmorOrder.entries else listOf(ArmorOrder.ALPHABETICAL)

    /** The saved order, or A–Z when the game has no in-game sort data. */
    val order: ArmorOrder get() = progress.order.takeIf { it in orders } ?: ArmorOrder.ALPHABETICAL

    /** The pieces the DLC and amiibo filters leave in the list, which its count is out of. */
    val visible: List<ArmorPiece> = catalog.pieces.filter { progress.isVisible(it.source) }

    /** What the list shows: [visible] narrowed by the ownership filter and search, under its headers. */
    val groups: List<ArmorGroup> = run {
        val q = query.trim()
        val matching = visible.filter { piece ->
            val owned = progress.of(piece.id).owned
            val ownershipOk = when (ownershipFilter) {
                OwnershipFilter.ALL -> true
                OwnershipFilter.OWNED -> owned
                OwnershipFilter.MISSING -> !owned
            }
            val setName = catalog.set(piece.setId)?.name.orEmpty()
            ownershipOk && (q.isEmpty() || piece.name.contains(q, true) || setName.contains(q, true))
        }
        ArmorGrouping.group(catalog, matching, order, progress.group)
    }

    /** True when a search or the Owned/Missing filter is on, so a toggle acts on the rows shown only. */
    val filtered: Boolean get() = query.isNotBlank() || ownershipFilter != OwnershipFilter.ALL

    /**
     * What a "mark all collected" toggle acts on: the pieces the list shows, or only those under
     * the header of the group keyed [group]. Pieces the DLC and amiibo filters hide are never in it.
     */
    fun bulkPieces(group: String? = null): List<ArmorPiece> =
        if (group == null) groups.flatMap { it.pieces } else groups.firstOrNull { it.key == group }?.pieces.orEmpty()

    /**
     * The toggle for [bulkPieces]: collecting each piece, or uncollecting it, as its row's box
     * does. Upgrade levels are left alone; the set header's stars already raise those. Its text
     * names the list or set, never a piece.
     */
    fun bulkScope(group: String? = null): BulkScope {
        val hidden = catalog.pieces.size - visible.size
        val note = listOfNotNull(
            "Armor the DLC and amiibo filters hide isn't changed.".takeIf { group == null && hidden > 0 },
            "Upgrade levels aren't changed.",
        )
        return BulkScope.of(
            label = group?.let { key -> groups.firstOrNull { it.key == key }?.title } ?: "Armor",
            items = bulkPieces(group),
            noun = "piece",
            filtered = filtered,
            note = note.joinToString(" "),
            isDone = { progress.of(it.id).owned },
            isStarted = { progress.of(it.id).owned },
        )
    }
}

private data class ViewOptions(val totalsMode: TotalsMode, val ownershipFilter: OwnershipFilter, val query: String)

@OptIn(ExperimentalCoroutinesApi::class)
class ArmorViewModel(
    private val storage: ArmorStorage,
    private val loadCatalog: suspend () -> ArmorCatalog = { ArmorRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<ArmorCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val totalsMode = MutableStateFlow(TotalsMode.COLLECTED)
    private val ownershipFilter = MutableStateFlow(OwnershipFilter.ALL)
    private val query = MutableStateFlow("")

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its pieces together, so a switch never pairs the new file with the old file's armor. */
    private val activePieces: Flow<Pair<SaveFile, Map<String, PieceProgress>>> =
        activeFile.flatMapLatest { file -> storage.pieces(file.id).map { file to it } }

    private val summaries: Flow<List<SaveFileSummary>> = combine(
        storage.game.saveFiles.map { file ->
            storage.pieces(file.id).map { pieces -> SaveFileSummary(file, pieces.values.count { it.owned }) }
        },
    ) { it.toList() }

    private val viewOptions = combine(totalsMode, ownershipFilter, query, ::ViewOptions)

    val state: StateFlow<ArmorUiState?> = combine(
        catalog.filterNotNull(),
        activePieces,
        storage.prefs,
        summaries,
        viewOptions,
    ) { catalog, (file, pieces), prefs, summaries, options ->
        ArmorUiState(
            catalog = catalog,
            progress = ArmorProgress.of(pieces, prefs),
            saveFile = file,
            saveFiles = summaries,
            totalsMode = options.totalsMode,
            ownershipFilter = options.ownershipFilter,
            query = options.query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            storage.migrateLegacy()
            runCatching { loadCatalog() }
                .onSuccess { catalog.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    fun setOwned(pieceId: String, owned: Boolean) = editPiece(pieceId) { it.copy(owned = owned) }

    fun setLevel(pieceId: String, level: Int) = setLevels(mapOf(pieceId to level))

    /** Sets several pieces' levels in one write (collecting them), e.g. a whole set from its header. */
    fun setLevels(levels: Map<String, Int>): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch {
            storage.updatePieces(file) { pieces ->
                pieces + levels.mapValues { (id, level) -> (pieces[id] ?: PieceProgress()).copy(owned = true, level = level) }
            }
        }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<MapSnapshot<String, PieceProgress>>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Marks every piece [ArmorUiState.bulkPieces] gives for [group] collected, or uncollected, on
     * the file on screen, leaving levels alone. It's one write, and what it replaced is kept in
     * [bulkChange] for an undo. Null when there's nothing to change.
     */
    fun setAllOwned(group: String?, owned: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.bulkPieces(group).map { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(group)
        return viewModelScope.launch {
            val snapshot = storage.setAllOwned(state.saveFile.id, ids, owned)
            val changed = ids.count { (snapshot.before[it]?.owned ?: false) != owned }
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

    fun setShowDlc(show: Boolean) = editPrefs { it.copy(showDlc = show) }

    fun setShowAmiibo(show: Boolean) = editPrefs { it.copy(showAmiibo = show) }

    fun setOrder(order: ArmorOrder) = editPrefs { it.copy(order = order) }

    fun setGroup(group: ArmorGroupBy) = editPrefs { it.copy(group = group) }

    fun setMaterialSort(sort: MaterialSort) = editPrefs { it.copy(materialSort = sort) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setTotalsMode(mode: TotalsMode) { totalsMode.value = mode }

    fun setOwnershipFilter(filter: OwnershipFilter) { ownershipFilter.value = filter }

    fun setQuery(text: String) { query.value = text }

    /**
     * Edits always target the file that was on screen when the user acted. Each write returns its
     * job, so a test can wait for it to finish before resetting the Main dispatcher.
     */
    private fun editPiece(pieceId: String, transform: (PieceProgress) -> PieceProgress): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch {
            storage.updatePieces(file) { pieces -> pieces + (pieceId to transform(pieces[pieceId] ?: PieceProgress())) }
        }
    }

    private fun editPrefs(transform: (ArmorPrefs) -> ArmorPrefs): Job = viewModelScope.launch { storage.updatePrefs(transform) }
}
