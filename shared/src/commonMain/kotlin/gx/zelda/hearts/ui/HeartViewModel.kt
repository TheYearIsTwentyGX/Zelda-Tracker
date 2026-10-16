package gx.zelda.hearts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
import gx.zelda.hearts.data.HeartRepository
import gx.zelda.hearts.data.HeartStorage
import gx.zelda.hearts.model.HeartCatalog
import gx.zelda.hearts.model.HeartEntry
import gx.zelda.hearts.model.HeartKind
import gx.zelda.hearts.model.HeartPrefs
import gx.zelda.hearts.model.HeartProgress
import gx.zelda.hearts.model.HeartTotal
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

/** A save file as the picker shows it: its hearts so far. */
data class HeartSaveSummary(val file: SaveFile, val total: HeartTotal)

/** What an entry needs, as the list's filter chips offer it. */
enum class HeartRequirement(val label: String) {
    CONTAINER("Heart Containers"),
    KINSTONE("Kinstone fusion"),
    MINISH("Minish"),
    ;

    fun matches(entry: HeartEntry): Boolean = when (this) {
        CONTAINER -> entry.kind == HeartKind.CONTAINER
        KINSTONE -> entry.kinstone
        MINISH -> entry.minish
    }
}

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class HeartFilters(
    val query: String = "",
    val missingOnly: Boolean = false,
    /** Entries must match every requirement chip that's on. */
    val requirements: Set<HeartRequirement> = emptySet(),
    /** Uncollected entries the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
)

/** One region's tallies, counting every entry in it whatever the filters show. */
data class HeartRegionCount(val collected: Int, val total: Int)

data class HeartUiState(
    val catalog: HeartCatalog,
    /** The active save file's hearts. */
    val progress: HeartProgress,
    val prefs: HeartPrefs,
    val saveFile: SaveFile,
    val saveFiles: List<HeartSaveSummary>,
    val filters: HeartFilters,
) {
    val total: HeartTotal = catalog.total(progress)

    /**
     * True when [entry]'s row shows only its region: spoilers are hidden, it hasn't been collected
     * and the user hasn't tapped to reveal it.
     */
    fun isHidden(entry: HeartEntry): Boolean =
        isSpoilerHidden(prefs.hideUncollected, progress.isCollected(entry.id), entry.id in filters.revealed)

    /** What the filters leave showing, by region. */
    val groups: List<Pair<String, List<HeartEntry>>> = catalog.byRegion(
        catalog.entries.filter { entry ->
            val hidden = isHidden(entry)
            // A hidden entry never matches a requirement chip, so the chips can't leak what it needs.
            (!filters.missingOnly || !progress.isCollected(entry.id)) &&
                (filters.requirements.isEmpty() || (!hidden && filters.requirements.all { it.matches(entry) })) &&
                matchesQuery(filters.query, hidden, entry.region) {
                    listOf(entry.name, entry.area, entry.region, entry.howTo, entry.fusionNote, entry.kind.label) + entry.requirements
                }
        },
    )

    val regionCounts: Map<String, HeartRegionCount> = catalog.entries.groupBy { it.region }.mapValues { (_, inRegion) ->
        HeartRegionCount(collected = inRegion.count { progress.isCollected(it.id) }, total = inRegion.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HeartViewModel(
    private val storage: HeartStorage,
    private val loadCatalog: suspend () -> HeartCatalog = { HeartRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<HeartCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(HeartFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's hearts. */
    private val activeProgress: Flow<Pair<SaveFile, HeartProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, HeartProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<HeartUiState?> = combine(
        catalog.filterNotNull(),
        activeProgress,
        storage.prefs,
        progressByFile,
        filters,
    ) { catalog, (file, progress), prefs, byFile, filters ->
        HeartUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> HeartSaveSummary(f, catalog.total(p)) },
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
    fun setCollected(entryId: String, collected: Boolean): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setCollected(file, entryId, collected) }
    }

    fun setHideUncollected(hide: Boolean): Job = viewModelScope.launch { storage.updatePrefs { it.copy(hideUncollected = hide) } }

    /** Shows one uncollected entry's row until the tracker is closed; nothing is saved. */
    fun reveal(entryId: String) = filters.update { it.copy(revealed = it.revealed + entryId) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setMissingOnly(on: Boolean) = filters.update { it.copy(missingOnly = on) }

    fun toggleRequirement(requirement: HeartRequirement) = filters.update {
        it.copy(requirements = if (requirement in it.requirements) it.requirements - requirement else it.requirements + requirement)
    }
}
