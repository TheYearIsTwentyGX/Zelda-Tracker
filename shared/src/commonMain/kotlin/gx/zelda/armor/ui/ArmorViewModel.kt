package gx.zelda.armor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.armor.data.ArmorRepository
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.logic.TotalsMode
import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.armor.model.ArmorPrefs
import gx.zelda.armor.model.ArmorProgress
import gx.zelda.armor.model.ArmorGroupBy
import gx.zelda.armor.model.ArmorOrder
import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
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

    private val activePieces: Flow<Map<String, PieceProgress>> = activeFile.flatMapLatest { storage.pieces(it.id) }

    private val summaries: Flow<List<SaveFileSummary>> = combine(
        storage.game.saveFiles.map { file ->
            storage.pieces(file.id).map { pieces -> SaveFileSummary(file, pieces.values.count { it.owned }) }
        },
    ) { it.toList() }

    private val viewOptions = combine(totalsMode, ownershipFilter, query, ::ViewOptions)

    val state: StateFlow<ArmorUiState?> = combine(
        catalog.filterNotNull(),
        combine(activeFile, activePieces, ::Pair),
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
    fun setLevels(levels: Map<String, Int>) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch {
            storage.updatePieces(file) { pieces ->
                pieces + levels.mapValues { (id, level) -> (pieces[id] ?: PieceProgress()).copy(owned = true, level = level) }
            }
        }
    }

    fun setShowDlc(show: Boolean) = editPrefs { it.copy(showDlc = show) }

    fun setShowAmiibo(show: Boolean) = editPrefs { it.copy(showAmiibo = show) }

    fun setOrder(order: ArmorOrder) = editPrefs { it.copy(order = order) }

    fun setGroup(group: ArmorGroupBy) = editPrefs { it.copy(group = group) }

    fun setMaterialSort(sort: MaterialSort) = editPrefs { it.copy(materialSort = sort) }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }

    fun setTotalsMode(mode: TotalsMode) { totalsMode.value = mode }

    fun setOwnershipFilter(filter: OwnershipFilter) { ownershipFilter.value = filter }

    fun setQuery(text: String) { query.value = text }

    /** Edits always target the file that was on screen when the user acted. */
    private fun editPiece(pieceId: String, transform: (PieceProgress) -> PieceProgress) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch {
            storage.updatePieces(file) { pieces -> pieces + (pieceId to transform(pieces[pieceId] ?: PieceProgress())) }
        }
    }

    private fun editPrefs(transform: (ArmorPrefs) -> ArmorPrefs) {
        viewModelScope.launch { storage.updatePrefs(transform) }
    }
}
