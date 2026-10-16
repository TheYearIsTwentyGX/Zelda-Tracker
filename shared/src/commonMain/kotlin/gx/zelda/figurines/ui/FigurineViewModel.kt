package gx.zelda.figurines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
import gx.zelda.figurines.data.FigurineRepository
import gx.zelda.figurines.data.FigurineStorage
import gx.zelda.figurines.model.Figurine
import gx.zelda.figurines.model.FigurineCatalog
import gx.zelda.figurines.model.FigurineCheckpoint
import gx.zelda.figurines.model.FigurinePrefs
import gx.zelda.figurines.model.FigurineProgress
import gx.zelda.figurines.model.FigurineTally
import gx.zelda.figurines.model.tally
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

/** A save file as the picker shows it: how many figurines it owns. */
data class FigurineSaveSummary(val file: SaveFile, val owned: Int, val total: Int)

/** Which figurines the list shows, by where the save file stands with them. */
enum class FigurineShow(val label: String) {
    ALL("All"),
    AVAILABLE("Available now"),
    TO_WIN("Still to win"),
    LOCKED("Not yet available"),
}

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class FigurineFilters(
    val query: String = "",
    /** Category ids to show; none means every category. */
    val categories: Set<String> = emptySet(),
    val show: FigurineShow = FigurineShow.ALL,
    /** Hidden figurines the user tapped to see. */
    val revealed: Set<Int> = emptySet(),
)

/** One checkpoint's group: its figurines the filters leave showing, and its counts. */
data class FigurineGroup(
    val checkpoint: FigurineCheckpoint,
    val figurines: List<Figurine>,
    /** Owned in the whole group, whatever the filters show. */
    val owned: Int,
    val total: Int,
    /** Past the save file's checkpoint while spoilers are hidden, so its name is too. */
    val hidden: Boolean,
)

data class FigurineUiState(
    val catalog: FigurineCatalog,
    /** The active save file's collection. */
    val progress: FigurineProgress,
    val prefs: FigurinePrefs,
    val saveFile: SaveFile,
    val saveFiles: List<FigurineSaveSummary>,
    val filters: FigurineFilters,
) {
    val tally: FigurineTally = catalog.tally(progress)

    val reached: FigurineCheckpoint = catalog.reached(progress)

    fun isAvailable(figurine: Figurine): Boolean = catalog.isAvailable(figurine, progress)

    /**
     * True when [figurine]'s row shows only its number: spoilers are hidden, it isn't owned or
     * available yet, and the user hasn't tapped to reveal it.
     */
    fun isHidden(figurine: Figurine): Boolean =
        isSpoilerHidden(prefs.hideUnavailable, isAvailable(figurine), figurine.number in filters.revealed)

    /** A checkpoint past the save file's, whose name is a spoiler while they are hidden. */
    fun isHidden(checkpoint: FigurineCheckpoint): Boolean =
        prefs.hideUnavailable && catalog.checkpointIndex(checkpoint.id) > catalog.checkpointIndex(reached.id)

    /**
     * The checkpoints the story picker offers: all of them, or with spoilers hidden only those
     * reached and the next one, so it can still move on.
     */
    val checkpointChoices: List<FigurineCheckpoint> =
        if (prefs.hideUnavailable) catalog.checkpoints.take(catalog.checkpointIndex(reached.id) + 2) else catalog.checkpoints

    val groups: List<FigurineGroup> = run {
        val matching = catalog.figurines.filter { f ->
            val hidden = isHidden(f)
            // A hidden figurine never matches a category filter or its name, so neither leaks.
            (filters.categories.isEmpty() || (!hidden && f.category in filters.categories)) &&
                when (filters.show) {
                    FigurineShow.ALL -> true
                    FigurineShow.AVAILABLE -> isAvailable(f)
                    FigurineShow.TO_WIN -> isAvailable(f) && !progress.isOwned(f.number)
                    FigurineShow.LOCKED -> !isAvailable(f)
                } &&
                matchesQuery(filters.query, hidden, f.label) {
                    listOf(f.name, f.pictured, f.number.toString(), catalog.category(f.category)?.name)
                }
        }
        val all = catalog.figurines.groupBy { it.checkpoint }
        catalog.byCheckpoint(matching).map { (checkpoint, figurines) ->
            val inGroup = all.getValue(checkpoint.id)
            FigurineGroup(
                checkpoint = checkpoint,
                figurines = figurines,
                owned = inGroup.count { progress.isOwned(it.number) },
                total = inGroup.size,
                hidden = isHidden(checkpoint),
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FigurineViewModel(
    private val storage: FigurineStorage,
    private val loadCatalog: suspend () -> FigurineCatalog = { FigurineRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<FigurineCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(FigurineFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The active file with its own progress, so a switch never pairs the new file with the old file's collection. */
    private val active: Flow<Pair<SaveFile, FigurineProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, FigurineProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<FigurineUiState?> = combine(
        catalog.filterNotNull(),
        active,
        storage.prefs,
        progressByFile,
        filters,
    ) { catalog, (file, progress), prefs, byFile, filters ->
        FigurineUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> FigurineSaveSummary(f, catalog.figurines.count { p.isOwned(it.number) }, catalog.figurines.size) },
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
    private fun edit(block: suspend (fileId: String) -> Unit): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { block(file) }
    }

    fun setOwned(number: Int, owned: Boolean) = edit { storage.setOwned(it, number, owned) }

    fun setRequirementMet(number: Int, met: Boolean) = edit { storage.setRequirementMet(it, number, met) }

    fun setCheckpoint(checkpointId: String) = edit { storage.setCheckpoint(it, checkpointId) }

    fun setShells(shells: Int) = edit { storage.setShells(it, shells) }

    fun setHideUnavailable(hide: Boolean): Job = viewModelScope.launch { storage.updatePrefs { it.copy(hideUnavailable = hide) } }

    /** Shows one hidden figurine's row until the tracker is closed; nothing is saved. */
    fun reveal(number: Int) = filters.update { it.copy(revealed = it.revealed + number) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setShow(show: FigurineShow) = filters.update { it.copy(show = show) }

    fun toggleCategory(id: String) = filters.update {
        it.copy(categories = if (id in it.categories) it.categories - id else it.categories + id)
    }
}
