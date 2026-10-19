package gx.zelda.sidequests.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.DiscoverySnapshot
import gx.zelda.core.DiscoveryState
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
import gx.zelda.sidequests.data.SideQuestData
import gx.zelda.sidequests.data.SideQuestRepository
import gx.zelda.sidequests.data.SideQuestStorage
import gx.zelda.sidequests.model.QuestKind
import gx.zelda.sidequests.model.QuestState
import gx.zelda.sidequests.model.SideQuest
import gx.zelda.sidequests.model.SideQuestPrefs
import gx.zelda.sidequests.model.SideQuestProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A save file as the picker shows it: how many quests of each kind it has completed. */
data class SideQuestSaveSummary(val file: SaveFile, val completed: Map<QuestKind, Int>)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class SideQuestFilters(
    val query: String = "",
    val incompleteOnly: Boolean = false,
    /** Quests not started that the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
)

/** One region's tallies on one tab, counting every quest in it whatever the filters show. */
data class QuestRegionCount(val started: Int, val completed: Int, val total: Int)

data class SideQuestUiState(
    val data: SideQuestData,
    /** The active save file's quests. */
    val progress: SideQuestProgress,
    val prefs: SideQuestPrefs,
    val saveFile: SaveFile,
    val saveFiles: List<SideQuestSaveSummary>,
    val filters: SideQuestFilters,
    /** The active save file's shrines, from the shrine tracker; null for a game without one. */
    val shrineProgress: ShrineProgress? = null,
) {
    val catalog get() = data.catalog

    /** Tabs with at least one quest, in [QuestKind] order. */
    val kinds: List<QuestKind> = QuestKind.entries.filter { kind -> catalog.quests.any { it.kind == kind } }

    fun total(kind: QuestKind): Int = catalog.quests.count { it.kind == kind }

    fun completedCount(kind: QuestKind): Int = catalog.completedCount(kind, progress.completed)

    /**
     * True when [quest]'s row shows only its region: spoilers are hidden, it hasn't been started
     * and the user hasn't tapped to reveal it.
     */
    fun isHidden(quest: SideQuest): Boolean =
        isSpoilerHidden(prefs.hideUndiscovered, progress.isStarted(quest.id), quest.id in filters.revealed)

    /** Every quest narrowed by search and filters. A hidden quest only matches on its region. */
    val matching: List<SideQuest> = catalog.quests.filter { quest ->
        (!filters.incompleteOnly || quest.id !in progress.completed) &&
            matchesQuery(filters.query, isHidden(quest), quest.region) {
                listOf(quest.name, quest.giver, quest.giverLocation, quest.region, quest.reward, shrineName(quest))
            }
    }

    /** [matching] quests of [kind], in region order. */
    fun groups(kind: QuestKind): List<Pair<String, List<SideQuest>>> = catalog.byRegion(matching.filter { it.kind == kind })

    /** Region tallies per tab. */
    val regionCounts: Map<QuestKind, Map<String, QuestRegionCount>> =
        catalog.quests.groupBy { it.kind }.mapValues { (_, quests) ->
            quests.groupBy { it.region }.mapValues { (_, inRegion) ->
                QuestRegionCount(
                    started = inRegion.count { progress.isStarted(it.id) },
                    completed = inRegion.count { it.id in progress.completed },
                    total = inRegion.size,
                )
            }
        }

    /** The linked shrine's state in the shrine tracker, for a Shrine Quest; null when unknown. */
    fun shrineState(quest: SideQuest): ShrineState? {
        val id = quest.shrineId ?: return null
        return shrineProgress?.state(id)
    }

    fun shrineName(quest: SideQuest): String? = quest.shrineId?.let { data.shrineNames[it] }

    /** True when a search or the Incomplete chip is on, so a bulk toggle acts on the quests shown only. */
    val filtered: Boolean get() = filters.query.isNotBlank() || filters.incompleteOnly

    /**
     * What a "mark all complete" toggle acts on: the quests the filters leave on [kind]'s tab
     * (hidden ones included), or only those under [region].
     */
    fun bulkQuests(kind: QuestKind, region: String? = null): List<SideQuest> = groups(kind).let { groups ->
        if (region == null) groups.flatMap { it.second } else groups.firstOrNull { it.first == region }?.second.orEmpty()
    }

    /**
     * The toggle for [kind]'s tab, or for [region]'s header on it. A mark completes every quest; a
     * clear puts each back to not started, in progress ones too. Its text names the tab or region,
     * never a quest.
     */
    fun bulkScope(kind: QuestKind, region: String? = null): BulkScope {
        val scope = BulkScope.of(
            label = region ?: kind.label,
            items = bulkQuests(kind, region),
            noun = kind.noun,
            plural = kind.noun + "s",
            filtered = filtered,
            isDone = { it.id in progress.completed },
            isStarted = { progress.isStarted(it.id) },
        )
        val note = listOfNotNull(
            if (scope.action == BulkAction.CLEAR) "Each goes back to not started, in progress ones too." else null,
            if (kind == QuestKind.SHRINE) "Their shrines stay as they are in the Shrine tracker." else null,
        )
        return scope.copy(note = note.joinToString(" ").ifEmpty { null })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SideQuestViewModel(
    private val storage: SideQuestStorage,
    /** Where the shrines found and completed per save file are kept; null for a game without the tracker. */
    private val shrines: ShrineStorage? = null,
    private val loadData: suspend () -> SideQuestData = { SideQuestRepository.loadData(storage.game) },
) : ViewModel() {
    private val data = MutableStateFlow<SideQuestData?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(SideQuestFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The active file with its quests and shrines, switched together so they never mix files. */
    private val active: Flow<Triple<SaveFile, SideQuestProgress, ShrineProgress?>> = activeFile.flatMapLatest { file ->
        combine(storage.progress(file.id), shrines?.progress(file.id) ?: flowOf(null)) { quests, shrineProgress ->
            Triple(file, quests, shrineProgress)
        }
    }

    private val progressByFile: Flow<List<Pair<SaveFile, SideQuestProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<SideQuestUiState?> = combine(
        data.filterNotNull(),
        active,
        storage.prefs,
        progressByFile,
        filters,
    ) { data, (file, progress, shrineProgress), prefs, byFile, filters ->
        SideQuestUiState(
            data = data,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) ->
                SideQuestSaveSummary(f, QuestKind.entries.associateWith { data.catalog.completedCount(it, p.completed) })
            },
            filters = filters,
            shrineProgress = shrineProgress,
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
    fun setState(questId: String, questState: QuestState): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setState(file, questId, questState) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<DiscoverySnapshot>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Completes every quest [SideQuestUiState.bulkQuests] gives for [kind] and [region], or puts
     * them all back to not started, on the file on screen. It's one write, and each quest's state
     * before it is kept in [bulkChange] for an undo. Null when there's nothing to change.
     */
    fun setAllDone(kind: QuestKind, region: String?, done: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.bulkQuests(kind, region).map { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(kind, region)
        val target = if (done) QuestState.COMPLETED else QuestState.UNDISCOVERED
        return viewModelScope.launch {
            val snapshot = storage.setAllStates(state.saveFile.id, ids, target)
            val changed = ids.count { DiscoveryState.of(it, snapshot.completed.members, snapshot.found.members) != target }
            if (changed > 0) {
                val action = if (done) BulkAction.MARK else BulkAction.CLEAR
                _bulkChange.value = BulkChange(action, changed, scope.noun, scope.plural, state.saveFile, snapshot)
            }
        }
    }

    /** Puts back each quest's state before [change], on the file it was made on, if it's still the last one. */
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

    /** Shows one quest's row until the tracker is closed; nothing is saved. */
    fun reveal(questId: String) = filters.update { it.copy(revealed = it.revealed + questId) }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setIncompleteOnly(on: Boolean) = filters.update { it.copy(incompleteOnly = on) }
}
