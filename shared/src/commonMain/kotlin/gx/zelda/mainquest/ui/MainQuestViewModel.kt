package gx.zelda.mainquest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.SaveFile
import gx.zelda.core.SetSnapshot
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestCatalog
import gx.zelda.mainquest.model.MainQuestProgress
import gx.zelda.mainquest.model.MainQuestSection
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

/** The tab's name. */
internal val MainQuestSection.label: String
    get() = when (this) {
        MainQuestSection.TOWERS -> "Towers"
        MainQuestSection.DIVINE_BEASTS -> "Divine Beasts"
        MainQuestSection.MEMORIES -> "Memories"
        MainQuestSection.DUNGEONS -> "Dungeons"
        MainQuestSection.ITEMS -> "Items"
        MainQuestSection.TIGER_SCROLLS -> "Tiger Scrolls"
    }

/** What a bulk toggle counts in, one and many. */
private val MainQuestSection.nouns: Pair<String, String>
    get() = when (this) {
        MainQuestSection.TOWERS -> "tower" to "towers"
        MainQuestSection.DIVINE_BEASTS -> "Divine Beast" to "Divine Beasts"
        MainQuestSection.MEMORIES -> "memory" to "memories"
        MainQuestSection.DUNGEONS -> "dungeon" to "dungeons"
        MainQuestSection.ITEMS -> "item" to "items"
        MainQuestSection.TIGER_SCROLLS -> "technique" to "techniques"
    }

/** The Tiger Scrolls tab's heading over the technique upgrades. */
internal const val UPGRADES_GROUP = "Technique upgrades"

/** A save file as the picker shows it: how far it is through each checklist. */
data class MainQuestSaveSummary(val file: SaveFile, val counts: Map<MainQuestSection, Int>)

data class MainQuestUiState(
    val catalog: MainQuestCatalog,
    /** What the active save file has done. */
    val progress: MainQuestProgress,
    val saveFile: SaveFile,
    val saveFiles: List<MainQuestSaveSummary>,
) {
    fun isDone(section: MainQuestSection, id: String): Boolean = id in progress.done(section)

    fun doneCount(section: MainQuestSection): Int = catalog.doneCount(section, progress.done(section))

    val albumCount: Int get() = catalog.albumCount(progress.memories)

    val elementCount: Int get() = catalog.elementCount(progress.dungeons)

    val upgradeCount: Int get() = catalog.upgradeCount(progress.tigerScrolls)

    /**
     * The ids a "mark all complete" toggle acts on: everything [section]'s tab lists (the Tiger
     * Scrolls tab's technique upgrades too), or only what's under its [group] heading: an item
     * category, or [UPGRADES_GROUP]. The tabs have no filters, so it's always the whole list.
     */
    fun bulkIds(section: MainQuestSection, group: String? = null): List<String> = when {
        group == null && section == MainQuestSection.TIGER_SCROLLS -> catalog.tigerScrolls.map { it.id }
        group == null -> catalog.ids(section)
        section == MainQuestSection.ITEMS -> catalog.itemsByCategory.firstOrNull { it.first.label == group }?.second.orEmpty().map { it.id }
        section == MainQuestSection.TIGER_SCROLLS && group == UPGRADES_GROUP -> catalog.techniqueUpgrades.map { it.id }
        else -> emptyList()
    }

    /** The toggle for [section]'s tab, or for its [group] heading. */
    fun bulkScope(section: MainQuestSection, group: String? = null): BulkScope {
        val (noun, plural) = section.nouns
        val done = progress.done(section)
        val scope = BulkScope.of(
            label = group ?: section.label,
            items = bulkIds(section, group),
            noun = noun,
            plural = plural,
            isDone = { it in done },
            isStarted = { it in done },
        )
        val upgrades = group == null && section == MainQuestSection.TIGER_SCROLLS && catalog.techniqueUpgrades.isNotEmpty()
        val note = when {
            !upgrades -> null
            scope.action == BulkAction.MARK -> "The technique upgrades are marked too."
            else -> "The technique upgrades are cleared too."
        }
        return scope.copy(note = note)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainQuestViewModel(
    private val storage: MainQuestStorage,
    private val loadCatalog: suspend () -> MainQuestCatalog = { MainQuestRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<MainQuestCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's progress. */
    private val activeProgress: Flow<Pair<SaveFile, MainQuestProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, MainQuestProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<MainQuestUiState?> = combine(
        catalog.filterNotNull(),
        activeProgress,
        progressByFile,
    ) { catalog, (file, progress), byFile ->
        MainQuestUiState(
            catalog = catalog,
            progress = progress,
            saveFile = file,
            saveFiles = byFile.map { (f, p) ->
                MainQuestSaveSummary(f, catalog.sections.associateWith { catalog.doneCount(it, p.done(it)) })
            },
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
    fun setDone(section: MainQuestSection, id: String, done: Boolean): Job? {
        val file = state.value?.saveFile?.id ?: return null
        return viewModelScope.launch { storage.setDone(file, section, id, done) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<Pair<MainQuestSection, SetSnapshot<String>>>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Marks everything [MainQuestUiState.bulkIds] gives for [section] and [group] done, or none of
     * it, on the file on screen. It's one write, and what it replaced is kept in [bulkChange] for
     * an undo. Null when there's nothing to change.
     */
    fun setAllDone(section: MainQuestSection, group: String?, done: Boolean): Job? {
        val state = state.value ?: return null
        val ids = state.bulkIds(section, group).takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope(section, group)
        return viewModelScope.launch {
            val snapshot = storage.setAllDone(state.saveFile.id, section, ids, done)
            val changed = ids.count { (it in snapshot.members) != done }
            if (changed > 0) {
                val action = if (done) BulkAction.MARK else BulkAction.CLEAR
                _bulkChange.value = BulkChange(action, changed, scope.noun, scope.plural, state.saveFile, section to snapshot)
            }
        }
    }

    /** Puts back what [change] replaced, on the file it was made on, if it's still the last one. */
    fun undoBulkChange(change: BulkChange<*>): Job? {
        val last = _bulkChange.value?.takeIf { it == change } ?: return null
        _bulkChange.value = null
        val (section, snapshot) = last.snapshot
        return viewModelScope.launch { storage.restore(last.saveFile.id, section, snapshot) }
    }

    /** Its snackbar has gone, so the undo with it. */
    fun dismissBulkChange(change: BulkChange<*>) {
        _bulkChange.update { if (it == change) null else it }
    }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }
}
