package gx.zelda.botw.armor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.botw.armor.data.ArmorRepository
import gx.zelda.botw.armor.logic.TotalsMode
import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.botw.armor.model.ArmorProgress
import gx.zelda.botw.armor.model.ArmorSort
import gx.zelda.core.JsonStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OwnershipFilter(val label: String) { ALL("All"), OWNED("Owned"), MISSING("Missing") }

data class ArmorUiState(
    val catalog: ArmorCatalog,
    val progress: ArmorProgress,
    val totalsMode: TotalsMode,
    val ownershipFilter: OwnershipFilter,
    val query: String,
)

class ArmorViewModel(
    private val store: JsonStore<ArmorProgress>,
    private val loadCatalog: suspend () -> ArmorCatalog = ArmorRepository::load,
) : ViewModel() {
    private val catalog = MutableStateFlow<ArmorCatalog?>(null)
    private val totalsMode = MutableStateFlow(TotalsMode.COLLECTED)
    private val ownershipFilter = MutableStateFlow(OwnershipFilter.ALL)
    private val query = MutableStateFlow("")

    val state: StateFlow<ArmorUiState?> = combine(
        catalog.filterNotNull(), store.data, totalsMode, ownershipFilter, query,
    ) { catalog, progress, mode, filter, query ->
        ArmorUiState(catalog, progress, mode, filter, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { catalog.value = loadCatalog() }
    }

    fun setOwned(pieceId: String, owned: Boolean) = edit { it.withPiece(pieceId) { p -> p.copy(owned = owned) } }

    fun setLevel(pieceId: String, level: Int) =
        edit { it.withPiece(pieceId) { p -> p.copy(owned = true, level = level) } }

    fun setShowDlc(show: Boolean) = edit { it.copy(showDlc = show) }

    fun setShowAmiibo(show: Boolean) = edit { it.copy(showAmiibo = show) }

    fun setSort(sort: ArmorSort) = edit { it.copy(sort = sort) }

    fun setTotalsMode(mode: TotalsMode) { totalsMode.value = mode }

    fun setOwnershipFilter(filter: OwnershipFilter) { ownershipFilter.value = filter }

    fun setQuery(text: String) { query.value = text }

    private fun edit(transform: (ArmorProgress) -> ArmorProgress) {
        viewModelScope.launch { store.update(transform) }
    }
}
