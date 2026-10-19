package gx.zelda.bosses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.bosses.data.BossRepository
import gx.zelda.bosses.data.BossStorage
import gx.zelda.bosses.model.BossData
import gx.zelda.bosses.model.BossKind
import gx.zelda.bosses.model.BossKindTally
import gx.zelda.bosses.model.BossPrefs
import gx.zelda.bosses.model.BossProgress
import gx.zelda.bosses.model.BossSpawn
import gx.zelda.bosses.model.tallies
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.DiscoveryState
import gx.zelda.core.SaveFile
import gx.zelda.core.SetSnapshot
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
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

/** A save file as the picker shows it: how many of its bosses are defeated. */
data class BossSaveSummary(val file: SaveFile, val defeated: Int, val total: Int)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class BossFilters(
    val query: String = "",
    val incompleteOnly: Boolean = false,
    /** Variant ids to show; a tab with none of its own variants here shows all of them. */
    val variants: Set<String> = emptySet(),
    /** Unfound bosses the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
)

/** One region's tallies within a tab, counting every spawn in it whatever the filters show. */
data class BossRegionCount(val found: Int, val defeated: Int, val total: Int)

/** One kind's tab: what the filters leave showing, and its tallies. */
data class BossPage(
    val tally: BossKindTally,
    /** Every spawn of this kind on the save file, in dataset order. */
    val spawns: List<BossSpawn>,
    val groups: List<Pair<String, List<BossSpawn>>>,
    val regionCounts: Map<String, BossRegionCount>,
    /** Spawns listed but not counted toward the medal, e.g. the Molduking. */
    val uncounted: List<BossSpawn>,
) {
    val kind: BossKind get() = tally.kind

    /** The variant ids this tab's filter chips offer: the kind's variants that have a spawn here. */
    val variants: List<String> get() = kind.variants.filter { v -> spawns.any { it.variant == v } }
}

data class BossUiState(
    val data: BossData,
    /** The active save file's bosses. */
    val progress: BossProgress,
    val prefs: BossPrefs,
    val saveFile: SaveFile,
    val saveFiles: List<BossSaveSummary>,
    val filters: BossFilters,
) {
    val catalog get() = data.catalog

    /** The bosses this save file has: Master Mode's extra Lynel only on the Master Mode file. */
    val spawns: List<BossSpawn> = catalog.spawnsFor(saveFile)

    /**
     * True when [spawn]'s row shows only its region: spoilers are hidden, it hasn't been found and
     * the user hasn't tapped to reveal it.
     */
    fun isHidden(spawn: BossSpawn): Boolean =
        isSpoilerHidden(prefs.hideUndiscovered, progress.isFound(spawn.id), spawn.id in filters.revealed)

    /** The tabs, one per kind in the dataset's order, each worked out once per state. */
    val pages: List<BossPage> = catalog.tallies(saveFile, progress).map { tally -> page(tally) }

    private fun page(tally: BossKindTally): BossPage {
        val kind = tally.kind
        val all = spawns.filter { it.kind == kind.id }
        val variants = filters.variants.intersect(kind.variants.toSet())
        // A hidden spawn only matches on its region and never on a variant filter, so neither
        // can leak what the row hides.
        val matching = all.filter { spawn ->
            val hidden = isHidden(spawn)
            (!filters.incompleteOnly || !progress.isDefeated(spawn.id)) &&
                (variants.isEmpty() || (!hidden && spawn.variant in variants)) &&
                matchesQuery(filters.query, hidden, spawn.region) {
                    listOf(data.variantName(spawn.variant), spawn.nearestPlace, spawn.region, spawn.id)
                }
        }
        return BossPage(
            tally = tally,
            spawns = all,
            groups = catalog.byRegion(matching),
            regionCounts = all.groupBy { it.region }.mapValues { (_, inRegion) ->
                BossRegionCount(
                    found = inRegion.count { progress.isFound(it.id) },
                    defeated = inRegion.count { progress.isDefeated(it.id) },
                    total = inRegion.size,
                )
            },
            uncounted = all.filterNot { it.countsForMedal },
        )
    }

    /** True when a search or chip is on for [page], so a bulk toggle acts on the bosses shown only. */
    fun isFiltered(page: BossPage): Boolean =
        filters.query.isNotBlank() || filters.incompleteOnly || filters.variants.any { it in page.kind.variants }

    /**
     * What a "mark all complete" toggle acts on: the spawns the filters leave on [kindId]'s tab
     * (hidden ones included), or only those under [region]. They're this save file's spawns, so a
     * Master Mode file's own Lynels are in and a Normal Mode file never touches them.
     */
    fun bulkSpawns(kindId: String, region: String? = null): List<BossSpawn> {
        val groups = pages.firstOrNull { it.kind.id == kindId }?.groups.orEmpty()
        return if (region == null) groups.flatMap { it.second } else groups.firstOrNull { it.first == region }?.second.orEmpty()
    }

    /**
     * The toggle for [kindId]'s tab, or for [region]'s header on it. A mark defeats every boss; a
     * clear puts each back to not found, found ones too. Its text names the tab or region, never
     * a boss.
     */
    fun bulkScope(kindId: String, region: String? = null): BulkScope {
        val page = pages.first { it.kind.id == kindId }
        val scope = BulkScope.of(
            label = region ?: page.kind.plural,
            items = bulkSpawns(kindId, region),
            noun = "boss",
            plural = "bosses",
            filtered = isFiltered(page),
            isDone = { progress.isDefeated(it.id) },
            isStarted = { progress.isFound(it.id) },
        )
        val leftOut = !saveFile.masterMode && catalog.spawns.any { it.kind == kindId && it.masterModeOnly && (region == null || it.region == region) }
        val note = listOfNotNull(
            if (scope.action == BulkAction.MARK) "Each is marked defeated." else "Each goes back to not found, found ones too.",
            if (leftOut) "Master Mode's own spawns aren't on this file, so they don't change." else null,
        )
        return scope.copy(note = note.joinToString(" "))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BossViewModel(
    private val storage: BossStorage,
    private val loadData: suspend () -> BossData = { BossRepository.loadData(storage.game) },
) : ViewModel() {
    private val data = MutableStateFlow<BossData?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(BossFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's bosses. */
    private val activeProgress: Flow<Pair<SaveFile, BossProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, BossProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<BossUiState?> = combine(
        data.filterNotNull(),
        activeProgress,
        storage.prefs,
        progressByFile,
        filters,
    ) { data, (file, progress), prefs, byFile, filters ->
        BossUiState(
            data = data,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) ->
                val spawns = data.catalog.spawnsFor(f)
                BossSaveSummary(f, spawns.count { p.isDefeated(it.id) }, spawns.size)
            },
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
    fun setDefeated(spawnId: String, defeated: Boolean): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setDefeated(file, spawnId, defeated) }
    }

    fun setState(spawnId: String, bossState: DiscoveryState): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setState(file, spawnId, bossState) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<Pair<SetSnapshot<String>, SetSnapshot<String>>>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Defeats every boss [BossUiState.bulkSpawns] gives for [kindId] and [region], or puts them all
     * back to not found, on the file on screen. It's one write, and each spawn's state before it
     * is kept in [bulkChange] for an undo. Null when there's nothing to change.
     */
    fun setAllDefeated(kindId: String, region: String?, defeated: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.bulkSpawns(kindId, region).map { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(kindId, region)
        val target = if (defeated) DiscoveryState.COMPLETED else DiscoveryState.UNDISCOVERED
        return viewModelScope.launch {
            val snapshot = storage.setAllStates(state.saveFile.id, ids, target)
            val (before, found) = snapshot
            val changed = ids.count { DiscoveryState.of(it, before.members, found.members) != target }
            if (changed > 0) {
                val action = if (defeated) BulkAction.MARK else BulkAction.CLEAR
                _bulkChange.value = BulkChange(action, changed, scope.noun, scope.plural, state.saveFile, snapshot)
            }
        }
    }

    /** Puts back each spawn's state before [change], on the file it was made on, if it's still the last one. */
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

    /** Shows one unfound boss's row until the tracker is closed; nothing is saved. */
    fun reveal(spawnId: String) = filters.update { it.copy(revealed = it.revealed + spawnId) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setIncompleteOnly(on: Boolean) = filters.update { it.copy(incompleteOnly = on) }

    fun toggleVariant(variant: String) = filters.update {
        it.copy(variants = if (variant in it.variants) it.variants - variant else it.variants + variant)
    }
}
