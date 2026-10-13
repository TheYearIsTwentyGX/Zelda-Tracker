package gx.zelda.botw.armor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.botw.armor.data.ArmorRepository
import gx.zelda.botw.armor.data.ArmorStorage
import gx.zelda.botw.armor.logic.TotalsMode
import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorPrefs
import gx.zelda.botw.armor.model.ArmorProgress
import gx.zelda.botw.armor.model.ArmorSort
import gx.zelda.botw.armor.model.PieceProgress
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
)

private data class ViewOptions(val totalsMode: TotalsMode, val ownershipFilter: OwnershipFilter, val query: String)

@OptIn(ExperimentalCoroutinesApi::class)
class ArmorViewModel(
    private val storage: ArmorStorage,
    private val loadCatalog: suspend () -> ArmorCatalog = ArmorRepository::load,
) : ViewModel() {
    private val catalog = MutableStateFlow<ArmorCatalog?>(null)
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
            catalog.value = loadCatalog()
        }
    }

    fun setOwned(pieceId: String, owned: Boolean) = editPiece(pieceId) { it.copy(owned = owned) }

    fun setLevel(pieceId: String, level: Int) = editPiece(pieceId) { it.copy(owned = true, level = level) }

    fun setShowDlc(show: Boolean) = editPrefs { it.copy(showDlc = show) }

    fun setShowAmiibo(show: Boolean) = editPrefs { it.copy(showAmiibo = show) }

    fun setSort(sort: ArmorSort) = editPrefs { it.copy(sort = sort) }

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
