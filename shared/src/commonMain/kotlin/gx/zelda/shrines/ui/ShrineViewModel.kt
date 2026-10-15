package gx.zelda.shrines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.DiscoveryState
import gx.zelda.core.SaveFile
import gx.zelda.core.isSpoilerHidden
import gx.zelda.core.matchesQuery
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.Lightroot
import gx.zelda.shrines.model.OrbPlan
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
import gx.zelda.shrines.model.UpgradeKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** A save file as the picker shows it: how many base-game shrines (and Lightroots) it has completed. */
data class ShrineSaveSummary(val file: SaveFile, val completed: Int, val lightrootsLit: Int = 0)

/** The list filters that aren't saved: they reset each time the tracker opens. */
data class ShrineFilters(
    val query: String = "",
    val incompleteOnly: Boolean = false,
    val questsOnly: Boolean = false,
    /** Undiscovered shrines the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
    /** Undiscovered Lightroots the user tapped to see while spoilers are hidden. */
    val revealedLightroots: Set<String> = emptySet(),
)

data class ShrineUiState(
    val catalog: ShrineCatalog,
    /** The active save file's shrines and Spirit Orb purchases. */
    val progress: ShrineProgress,
    val prefs: ShrinePrefs,
    val saveFile: SaveFile,
    val saveFiles: List<ShrineSaveSummary>,
    val filters: ShrineFilters,
    /**
     * Divine Beasts the active save file has freed, from the Main Quest tracker; null when no
     * tracker records the game's bosses, so their Heart Containers are entered by hand.
     */
    val beastsFreed: Int? = 0,
) {
    val orbs: OrbPlan get() = OrbPlan.of(catalog, progress, includeDlc = prefs.showDlc, beastsFreed = beastsFreed)

    /** Whether boss Heart Containers are entered on the planner card rather than counted from another tracker. */
    val bossHeartsByHand: Boolean get() = beastsFreed == null

    /** Shrines the DLC filter allows, before search and the other filters. */
    val shown: List<Shrine> get() = if (prefs.showDlc) catalog.shrines else catalog.base

    /**
     * True when [shrine]'s row shows only its region: spoilers are hidden, it hasn't been found
     * and the user hasn't tapped to reveal it.
     */
    fun isHidden(shrine: Shrine): Boolean =
        isSpoilerHidden(prefs.hideUndiscovered, progress.isFound(shrine.id), shrine.id in filters.revealed)

    /** Like [isHidden], for a Lightroot: it hides until the Lightroot itself is found or revealed. */
    fun isHidden(lightroot: Lightroot): Boolean =
        isSpoilerHidden(prefs.hideUndiscovered, progress.lightroots.isFound(lightroot.id), lightroot.id in filters.revealedLightroots)

    /**
     * [shown] narrowed by search and filters. A hidden shrine only matches on its region and never
     * on the Shrine Quests filter, so neither can leak what the row hides.
     */
    val matching: List<Shrine>
        get() = shown.filter { shrine ->
            val hidden = isHidden(shrine)
            (!filters.incompleteOnly || shrine.id !in progress.completed) &&
                (!filters.questsOnly || (!hidden && shrine.quest != null)) &&
                matchesQuery(filters.query, hidden, shrine.region) {
                    listOf(shrine.name, shrine.trial, shrine.quest, shrine.region, shrine.area, catalog.lightrootOf(shrine)?.name)
                }
        }

    /**
     * Every Lightroot narrowed by search and the incomplete filter (unlit only). A hidden one only
     * matches on its region. A visible one also matches its shrine's name, which its own name
     * already spells backwards, but not the shrine's trial or location.
     */
    val matchingLightroots: List<Lightroot>
        get() = catalog.lightroots.filter { root ->
            (!filters.incompleteOnly || root.id !in progress.lightroots.completed) &&
                matchesQuery(filters.query, isHidden(root), catalog.regionOf(root)) {
                    listOf(root.name, catalog.shrineOf(root).name, catalog.regionOf(root))
                }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShrineViewModel(
    private val storage: ShrineStorage,
    /** Where the Divine Beasts freed per save file are kept; null for a game without the tracker. */
    private val mainQuest: MainQuestStorage? = null,
    private val loadCatalog: suspend () -> ShrineCatalog = { ShrineRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<ShrineCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val filters = MutableStateFlow(ShrineFilters())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    private val activeProgress: Flow<ShrineProgress> = activeFile.flatMapLatest { storage.progress(it.id) }

    private val activeBeastsFreed: Flow<Int?> = activeFile.flatMapLatest { file ->
        mainQuest?.divineBeastsFreed(file.id)?.map { it.size } ?: flowOf(null)
    }

    private val progressByFile: Flow<List<Pair<SaveFile, ShrineProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    val state: StateFlow<ShrineUiState?> = combine(
        catalog.filterNotNull(),
        combine(activeFile, activeProgress, activeBeastsFreed, ::Triple),
        storage.prefs,
        progressByFile,
        filters,
    ) { catalog, (file, progress, beastsFreed), prefs, byFile, filters ->
        ShrineUiState(
            catalog = catalog,
            progress = progress,
            prefs = prefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) ->
                ShrineSaveSummary(f, catalog.baseCompleted(p.completed), catalog.lightrootCount(p.lightroots.completed))
            },
            filters = filters,
            beastsFreed = beastsFreed,
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

    fun setBossHearts(hearts: Int) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setBossHearts(file, hearts) }
    }

    fun setFreeUpgrade(kind: UpgradeKind?) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setFreeUpgrade(file, kind) }
    }

    fun setLightrootState(lightrootId: String, lightrootState: DiscoveryState) {
        val file = state.value?.saveFile?.id ?: return
        viewModelScope.launch { storage.setLightrootState(file, lightrootId, lightrootState) }
    }

    fun setShowDlc(show: Boolean) {
        viewModelScope.launch { storage.updatePrefs { it.copy(showDlc = show) } }
    }

    fun setHideUndiscovered(hide: Boolean) {
        viewModelScope.launch { storage.updatePrefs { it.copy(hideUndiscovered = hide) } }
    }

    /** Shows one undiscovered shrine's row until the tracker is closed; nothing is saved. */
    fun reveal(shrineId: String) = filters.update { it.copy(revealed = it.revealed + shrineId) }

    /** Shows one undiscovered Lightroot's row until the tracker is closed; nothing is saved. */
    fun revealLightroot(lightrootId: String) = filters.update { it.copy(revealedLightroots = it.revealedLightroots + lightrootId) }

    fun setSaveFile(id: String) {
        viewModelScope.launch { storage.setActiveSaveFile(id) }
    }

    fun setQuery(text: String) = filters.update { it.copy(query = text) }

    fun setIncompleteOnly(on: Boolean) = filters.update { it.copy(incompleteOnly = on) }

    fun setQuestsOnly(on: Boolean) = filters.update { it.copy(questsOnly = on) }
}
