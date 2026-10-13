package gx.zelda.shrines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.SaveFile
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.OrbPlan
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A save file as the picker shows it: how many base-game shrines it has completed. */
data class ShrineSaveSummary(val file: SaveFile, val completed: Int)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class ShrineFilters(
    val query: String = "",
    val incompleteOnly: Boolean = false,
    val questsOnly: Boolean = false,
    /** Undiscovered shrines the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
)

data class ShrineUiState(
    val catalog: ShrineCatalog,
    /** The active save file's shrines and Spirit Orb purchases. */
    val progress: ShrineProgress,
    val prefs: ShrinePrefs,
    val saveFile: SaveFile,
    val saveFiles: List<ShrineSaveSummary>,
    val filters: ShrineFilters,
) {
    val orbs: OrbPlan get() = OrbPlan.of(catalog, progress, includeDlc = prefs.showDlc)

    /** Shrines the DLC filter allows, before search and the other filters. */
    val shown: List<Shrine> get() = if (prefs.showDlc) catalog.shrines else catalog.base

    /**
     * True when [shrine]'s row shows only its region: spoilers are hidden, it hasn't been found
     * and the user hasn't tapped to reveal it.
     */
    fun isHidden(shrine: Shrine): Boolean =
        prefs.hideUndiscovered && !progress.isFound(shrine.id) && shrine.id !in filters.revealed

    /**
     * [shown] narrowed by search and filters. A hidden shrine only matches on its region and never
     * on the Shrine Quests filter, so neither can leak what the row hides.
     */
    val matching: List<Shrine>
        get() {
            val q = filters.query.trim()
            return shown.filter { shrine ->
                val hidden = isHidden(shrine)
                val searchable = if (hidden) listOf(shrine.region) else listOfNotNull(shrine.name, shrine.trial, shrine.quest, shrine.region)
                (!filters.incompleteOnly || shrine.id !in progress.completed) &&
                    (!filters.questsOnly || (!hidden && shrine.quest != null)) &&
                    (q.isEmpty() || searchable.any { it.contains(q, true) })
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShrineViewModel(
    private val storage: ShrineStorage,
    private val loadCatalog: suspend () -> ShrineCatalog = { ShrineRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<ShrineCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(ShrineFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    private val activeProgress: Flow<ShrineProgress> = activeFile.flatMapLatest { storage.progress(it.id) }

    private val progressByFile: Flow<List<Pair<SaveFile, ShrineProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<ShrineUiState?> = combine(
        catalog.filterNotNull(),
        combine(activeFile, activeProgress, ::Pair),
        storage.prefs,
        progressByFile,
        filters,
    ) { catalog, (file, progress), prefs, byFile, filters ->
        ShrineUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> ShrineSaveSummary(f, catalog.baseCompleted(p.completed)) },
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

    /** Edits always target the file that was on screen when the user acted. */
    fun setCompleted(shrineId: String, completed: Boolean) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setCompleted(file, shrineId, completed) }
    }

    fun setState(shrineId: String, shrineState: ShrineState) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setState(file, shrineId, shrineState) }
    }

    fun setBought(hearts: Int, stamina: Int) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setBought(file, hearts, stamina) }
    }

    fun setShowDlc(show: Boolean) {
        viewModelScope.launch { storage.updatePrefs { it.copy(showDlc = show) } }
    }

    fun setHideUndiscovered(hide: Boolean) {
        viewModelScope.launch { storage.updatePrefs { it.copy(hideUndiscovered = hide) } }
    }

    /** Shows one undiscovered shrine's row until the tracker is closed; nothing is saved. */
    fun reveal(shrineId: String) = filters.update { it.copy(revealed = it.revealed + shrineId) }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setIncompleteOnly(on: Boolean) = filters.update { it.copy(incompleteOnly = on) }

    fun setQuestsOnly(on: Boolean) = filters.update { it.copy(questsOnly = on) }
}
