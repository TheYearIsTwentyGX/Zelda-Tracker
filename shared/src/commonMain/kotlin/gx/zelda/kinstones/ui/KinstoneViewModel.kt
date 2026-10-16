package gx.zelda.kinstones.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
import gx.zelda.kinstones.data.KinstoneRepository
import gx.zelda.kinstones.data.KinstoneStorage
import gx.zelda.kinstones.model.FusionReward
import gx.zelda.kinstones.model.KinstoneCatalog
import gx.zelda.kinstones.model.KinstoneColor
import gx.zelda.kinstones.model.KinstoneFusion
import gx.zelda.kinstones.model.KinstonePrefs
import gx.zelda.kinstones.model.KinstoneProgress
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

/** A save file as the picker shows it: how many fusions it has done. */
data class KinstoneSaveSummary(val file: SaveFile, val fused: Int, val total: Int)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class KinstoneFilters(
    val query: String = "",
    val notFusedOnly: Boolean = false,
    /** Show only fusions giving any of these; empty shows all. */
    val rewards: Set<FusionReward> = emptySet(),
    /** Show only fusions needing a piece of one of these colours; empty shows all. */
    val colors: Set<KinstoneColor> = emptySet(),
    /** Show only this region's fusions; null shows every region. */
    val region: String? = null,
    /** Fusions not done that the user tapped to see while results are hidden. */
    val revealed: Set<String> = emptySet(),
) {
    val isFiltering: Boolean get() = notFusedOnly || rewards.isNotEmpty() || colors.isNotEmpty() || region != null
}

/** One region's tally, counting every fusion in it whatever the filters show. */
data class KinstoneRegionCount(val fused: Int, val total: Int)

/** Done / total for one group of fusions, e.g. the Gold ones. */
data class FusionTally(val fused: Int, val total: Int)

data class KinstoneUiState(
    val catalog: KinstoneCatalog,
    /** The active save file's fusions. */
    val progress: KinstoneProgress,
    val prefs: KinstonePrefs,
    val saveFile: SaveFile,
    val saveFiles: List<KinstoneSaveSummary>,
    val filters: KinstoneFilters,
) {
    val fused: Int = catalog.fusedCount(progress.fused)
    val total: Int get() = catalog.fusions.size

    /** The nine story fusions. */
    val gold: FusionTally = tally(catalog.fusions.filter { it.isGold })

    /** Fusions with a Piece of Heart or the Heart Container in them. */
    val hearts: FusionTally = tally(catalog.fusions.filter { FusionReward.HEART in it.rewards })

    private fun tally(fusions: List<KinstoneFusion>) = FusionTally(fusions.count { progress.isFused(it.id) }, fusions.size)

    /**
     * True when [fusion]'s result is hidden: results are hidden, it isn't done and the user hasn't
     * tapped to reveal it. Its partner, place and piece still show, since they're how to find it.
     */
    fun isHidden(fusion: KinstoneFusion): Boolean =
        isSpoilerHidden(prefs.hideResults, progress.isFused(fusion.id), fusion.id in filters.revealed)

    /**
     * Every fusion narrowed by search and filters. A hidden fusion never matches on what it gives,
     * by search or by the reward filter, so neither can leak what the row hides.
     */
    val matching: List<KinstoneFusion> = catalog.fusions.filter { fusion ->
        val hidden = isHidden(fusion)
        (!filters.notFusedOnly || !progress.isFused(fusion.id)) &&
            (filters.region == null || fusion.region == filters.region) &&
            (filters.colors.isEmpty() || fusion.color in filters.colors) &&
            (filters.rewards.isEmpty() || (!hidden && fusion.rewards.any { it in filters.rewards })) &&
            // The row shows partner, place and piece even when hidden, so only the result is held back.
            matchesQuery(filters.query, hidden = false, fusion.region) { searchText(fusion, hidden) }
    }

    private fun searchText(fusion: KinstoneFusion, hidden: Boolean): List<String?> {
        val shown = listOf(fusion.partner, fusion.location, fusion.region, fusion.pieceName)
        return if (hidden) shown else shown + listOf(fusion.result, fusion.resultRegion, fusion.note) + fusion.rewards.map { it.label }
    }

    /** [matching] in region order. */
    val groups: List<Pair<String, List<KinstoneFusion>>> = catalog.byRegion(matching)

    val regionCounts: Map<String, KinstoneRegionCount> = catalog.fusions.groupBy { it.region }.mapValues { (_, inRegion) ->
        KinstoneRegionCount(fused = inRegion.count { progress.isFused(it.id) }, total = inRegion.size)
    }

    /** The reward kinds some fusion gives, in [FusionReward] order, for the filter chips. */
    val rewardKinds: List<FusionReward> = FusionReward.entries.filter { r -> catalog.fusions.any { r in it.rewards } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KinstoneViewModel(
    private val storage: KinstoneStorage,
    private val loadCatalog: suspend () -> KinstoneCatalog = { KinstoneRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<KinstoneCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(KinstoneFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The active file with its fusions, switched together so they never mix files. */
    private val active: Flow<Pair<SaveFile, KinstoneProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, KinstoneProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<KinstoneUiState?> = combine(
        catalog.filterNotNull(),
        active,
        storage.prefs,
        progressByFile,
        filters,
    ) { catalog, (file, progress), prefs, byFile, filters ->
        KinstoneUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> KinstoneSaveSummary(f, catalog.fusedCount(p.fused), catalog.fusions.size) },
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
    fun setFused(fusionId: String, done: Boolean) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setFused(file, fusionId, done) }
    }

    fun setHideResults(hide: Boolean) {
        viewModelScope.launch { storage.updatePrefs { it.copy(hideResults = hide) } }
    }

    /** Shows one fusion's result until the tracker is closed; nothing is saved. */
    fun reveal(fusionId: String) = filters.update { it.copy(revealed = it.revealed + fusionId) }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setNotFusedOnly(on: Boolean) = filters.update { it.copy(notFusedOnly = on) }

    fun toggleReward(reward: FusionReward) = filters.update {
        it.copy(rewards = if (reward in it.rewards) it.rewards - reward else it.rewards + reward)
    }

    fun toggleColor(color: KinstoneColor) = filters.update {
        it.copy(colors = if (color in it.colors) it.colors - color else it.colors + color)
    }

    fun setRegion(region: String?) = filters.update { it.copy(region = region) }

    fun clearFilters() = filters.update { KinstoneFilters(query = it.query, revealed = it.revealed) }
}
