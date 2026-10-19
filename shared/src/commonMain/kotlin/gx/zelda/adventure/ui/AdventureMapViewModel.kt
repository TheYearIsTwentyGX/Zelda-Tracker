package gx.zelda.adventure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.adventure.data.AdventureRepository
import gx.zelda.adventure.data.AdventureSnapshot
import gx.zelda.adventure.data.AdventureStorage
import gx.zelda.adventure.model.AdventureInventory
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.AdventureMapPrefs
import gx.zelda.adventure.model.AdventurePrefs
import gx.zelda.adventure.model.AdventureProgress
import gx.zelda.adventure.model.AdventureReach
import gx.zelda.adventure.model.AdventureSpoilers
import gx.zelda.adventure.model.AdventureTab
import gx.zelda.adventure.model.AdventureTotals
import gx.zelda.adventure.model.GridViewport
import gx.zelda.adventure.model.EdgeStyle
import gx.zelda.adventure.model.MapEdge
import gx.zelda.adventure.model.Side
import gx.zelda.adventure.model.MapRegion
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.SquareFilter
import gx.zelda.adventure.model.SquareLook
import gx.zelda.adventure.model.SquareStatus
import gx.zelda.adventure.model.Tally
import gx.zelda.adventure.model.Warrior
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.SaveFile
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

/** A save file as the picker shows it: everything done of all on this map. */
data class AdventureSaveSummary(val file: SaveFile, val all: Tally)

/** What isn't saved: it resets each time the map opens. */
data class AdventureSession(
    val query: String = "",
    /** Unexplored squares the user tapped to see while spoiler-safe mode is on. */
    val revealed: Set<String> = emptySet(),
    /** Squares the user has used the Compass on, so their secrets show. */
    val compassed: Set<String> = emptySet(),
)

data class AdventureUiState(
    val map: AdventureMap,
    /** The active save file's progress. */
    val progress: AdventureProgress,
    val prefs: AdventurePrefs,
    val mapPrefs: AdventureMapPrefs,
    val saveFile: SaveFile,
    val saveFiles: List<AdventureSaveSummary>,
    val session: AdventureSession,
) {
    val spoilers = AdventureSpoilers(map, progress, prefs.spoilerSafe, session.revealed, session.compassed)
    val inventory = AdventureInventory(map, progress)
    val totals = AdventureTotals(map, progress)

    /** The squares that can be played now; the rest are drawn dim. */
    val challengeable: Set<String> by lazy { AdventureReach.challengeable(map, progress) }

    /** The line between each pair of neighbouring squares: wall, open, or opened by a search. */
    val edges: List<MapEdge> by lazy { AdventureReach.edges(map, progress) }

    /** [edges] by square and side (east or south), for drawing. */
    val edgeStyles: Map<Pair<String, Side>, EdgeStyle> by lazy { edges.associate { (it.square.id to it.side) to it.style } }

    /** True when a chip, the warrior choice or a search narrows the squares, so mark-all acts on those only. */
    val filtered: Boolean get() = mapPrefs.filters.isNotEmpty() || mapPrefs.warrior != null || session.query.isNotBlank()

    /** Warriors with rewards on this map, for the "Rewards for" choice. */
    val warriors: List<Warrior> = map.warriors.filter { w -> map.squares.any { s -> s.rewards.any { it.warrior == w.id } } }

    /**
     * Whether [square] passes the chips, the warrior choice and the search. An Unexplored square
     * only ever matches on whether it's cleared and on its colour, so a filter can't give away what
     * it hides; nor do a square's searches count while its secrets are hidden, but for the one that
     * lifts its seal.
     */
    fun matches(square: Square): Boolean {
        val hidden = spoilers.isHidden(square)
        val searches = spoilers.visibleSearches(square).filterNot { progress.isDone(it.id) }
        val warrior = mapPrefs.warrior
        // With a warrior chosen, the reward chips count that warrior's rewards only.
        fun rewardsLeft(type: RewardType) =
            !hidden && square.rewards.any { it.type == type && !progress.isDone(it.id) && (warrior == null || it.warrior == warrior) }
        val chips = mapPrefs.filters.all { f ->
            when (f) {
                SquareFilter.NOT_CLEARED -> !progress.isCleared(square)
                SquareFilter.NOT_A_RANKED -> !progress.isARanked(square)
                SquareFilter.HEART_CONTAINERS -> rewardsLeft(RewardType.HEART_CONTAINER)
                SquareFilter.HEART_PIECES -> rewardsLeft(RewardType.HEART_PIECE)
                SquareFilter.SKULLTULAS -> !hidden && square.skulltulas.any { !progress.isDone(it.id) }
                SquareFilter.SEARCHES -> searches.isNotEmpty()
                SquareFilter.SEARCHABLE -> searches.any(inventory::canDo)
                SquareFilter.CHALLENGEABLE -> square.id in challengeable
            }
        }
        val forWarrior = warrior?.let { w -> !hidden && square.rewards.any { it.warrior == w && !progress.isDone(it.id) } } ?: true
        return chips && forWarrior && matchesQuery(session.query, hidden, map.regionName(square.region)) { searchText(square) }
    }

    /**
     * The squares the filters leave, in reading order; with *Not cleared* on, the ones that can be
     * challenged now come first.
     */
    val matching: List<Square> = map.squares.filter(::matches).let { squares ->
        if (SquareFilter.NOT_CLEARED in mapPrefs.filters) squares.sortedBy { it.id !in challengeable } else squares
    }

    /** [matching] by colour, easiest first, for the list. */
    val groups: List<Pair<MapRegion, List<Square>>> = map.regions.mapNotNull { r ->
        matching.filter { it.region == r.id }.takeIf { it.isNotEmpty() }?.let { r to it }
    }

    /** Squares complete of all in [region], whatever the filters show. */
    fun regionTally(region: MapRegion): Tally = map.squares.filter { it.region == region.id }.let { sq -> Tally(sq.count(progress::isComplete), sq.size) }

    fun look(square: Square, selected: Boolean): SquareLook =
        SquareLook.of(map, square, progress, spoilers, matches = !filtered || square in matchingSet, selected = selected, challengeable = square.id in challengeable)

    private val matchingSet: Set<Square> by lazy { matching.toSet() }

    /** What a mark-all acts on: the squares showing, or only those in [region]. */
    fun bulkSquares(region: String? = null): List<Square> = if (region == null) matching else matching.filter { it.region == region }

    /** The toggle for the whole map, or for [region]'s card. Its text names the map or colour, never a square. */
    fun bulkScope(region: String? = null): BulkScope {
        val squares = bulkSquares(region)
        val scope = BulkScope.of(
            label = region?.let { "the ${map.regionName(it)} squares" } ?: map.name,
            items = squares,
            noun = "square",
            filtered = filtered,
            isDone = progress::isComplete,
            isStarted = progress::isStarted,
        )
        val note = when (scope.action) {
            BulkAction.MARK -> "Each is marked A-ranked, with every reward, Gold Skulltula and search on it ticked."
            BulkAction.CLEAR -> "Each goes back to not cleared, with its rewards, Gold Skulltulas and searches unticked."
        }
        return scope.copy(note = note)
    }

    private fun searchText(square: Square): List<String?> =
        listOf(square.label, square.altLabel, square.title, square.mission, square.rule, square.battle?.label, map.regionName(square.region)) +
            square.rewards.flatMap { listOf(it.name, it.keep, it.warrior?.let(map::warriorName)) } +
            square.warriors.map { map.warriorName(it.warrior) } +
            square.givesCards.mapNotNull { map.card(it)?.name } +
            spoilers.visibleSearches(square).mapNotNull { map.card(it.card)?.name }
}

/** One Hyrule Warriors Adventure Mode map: the grid, its list, a sheet per square and the item cards. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdventureMapViewModel(
    private val storage: AdventureStorage,
    private val loadMap: suspend () -> AdventureMap = { AdventureRepository.load(storage.dataFile) },
) : ViewModel() {
    private val map = MutableStateFlow<AdventureMap?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled map is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed
    private val session = MutableStateFlow(AdventureSession())

    private val activeFile: Flow<SaveFile> = storage.activeSaveFile.map(storage.game::saveFile)

    /** The file and its progress together, so a switch never pairs the new file with the old file's ticks. */
    private val activeProgress: Flow<Pair<SaveFile, AdventureProgress>> =
        activeFile.flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val progressByFile: Flow<List<Pair<SaveFile, AdventureProgress>>> = combine(
        storage.game.saveFiles.map { file -> storage.progress(file.id).map { file to it } },
    ) { it.toList() }

    private val prefs: Flow<Pair<AdventurePrefs, AdventureMapPrefs>> = combine(storage.prefs, storage.mapPrefs) { a, b -> a to b }

    val state: StateFlow<AdventureUiState?> = combine(
        map.filterNotNull(),
        activeProgress,
        prefs,
        progressByFile,
        session,
    ) { map, (file, progress), (prefs, mapPrefs), byFile, session ->
        AdventureUiState(
            map = map,
            progress = progress,
            prefs = prefs,
            mapPrefs = mapPrefs,
            saveFile = file,
            saveFiles = byFile.map { (f, p) -> AdventureSaveSummary(f, AdventureTotals(map, p).all) },
            session = session,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selected = MutableStateFlow<String?>(null)

    /** The square whose sheet is open, by id. */
    val selected: StateFlow<String?> = _selected

    private val _viewport = MutableStateFlow<GridViewport?>(null)

    /** Where the map is panned and zoomed to; kept here so it survives rotation and the tab switch. */
    val viewport: StateFlow<GridViewport?> = _viewport

    init {
        viewModelScope.launch {
            runCatching { loadMap() }
                .onSuccess { map.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    /**
     * Edits always target the file that was on screen when the user acted. Each write returns its
     * job, so a test can wait for it to finish before resetting the Main dispatcher.
     */
    fun setStatus(squareId: String, status: SquareStatus): Job? {
        val state = state.value ?: return null
        val square = state.map.square(squareId) ?: return null
        return viewModelScope.launch { storage.setStatus(state.saveFile.id, square, status) }
    }

    /** Ticks or unticks a reward, Gold Skulltula or search. */
    fun setDone(id: String, done: Boolean): Job? {
        val state = state.value ?: return null
        if (id !in state.map.checkIds) return null
        return viewModelScope.launch { storage.setDone(state.saveFile.id, id, done) }
    }

    /** Sets whether the file has [cardId]; null lets the cleared squares decide again. */
    fun setCard(cardId: String, owned: Boolean?): Job? {
        val state = state.value ?: return null
        if (state.map.card(cardId) == null) return null
        return viewModelScope.launch { storage.setCard(state.saveFile.id, cardId, owned) }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<AdventureSnapshot>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /**
     * Makes every square [AdventureUiState.bulkSquares] gives for [region] complete, or clears them
     * all, on the file on screen, in one write; what it replaced is kept in [bulkChange] for an
     * undo. Null when there's nothing to change.
     */
    fun setAllDone(region: String?, complete: Boolean): Job? {
        val state = state.value ?: return null
        return setSquaresDone(state, state.bulkSquares(region), complete)
    }

    /** The same for one square, from its sheet. */
    fun setSquareDone(squareId: String, complete: Boolean): Job? {
        val state = state.value ?: return null
        return setSquaresDone(state, listOfNotNull(state.map.square(squareId)), complete)
    }

    private fun setSquaresDone(state: AdventureUiState, squares: List<Square>, complete: Boolean): Job? {
        if (squares.isEmpty()) return null
        return viewModelScope.launch {
            val snapshot = storage.setAll(state.saveFile.id, squares, complete)
            val before = AdventureProgress(snapshot.status.before.filterValues { it != null }.mapValues { it.value!! }, snapshot.done.members)
            val changed = squares.count { if (complete) !before.isComplete(it) else before.isStarted(it) }
            if (changed > 0) {
                val action = if (complete) BulkAction.MARK else BulkAction.CLEAR
                _bulkChange.value = BulkChange(action, changed, "square", "squares", state.saveFile, snapshot)
            }
        }
    }

    /** Puts back what [change] replaced, on the file it was made on, if it's still the last one. */
    fun undoBulkChange(change: BulkChange<*>): Job? {
        val last = _bulkChange.value?.takeIf { it == change } ?: return null
        _bulkChange.value = null
        return viewModelScope.launch { storage.restore(last.saveFile.id, last.snapshot) }
    }

    /** Its snackbar has gone, so the undo with it. */
    fun dismissBulkChange(change: BulkChange<*>) {
        _bulkChange.update { if (it == change) null else it }
    }

    fun setSpoilerSafe(on: Boolean): Job = viewModelScope.launch { storage.updatePrefs { it.copy(spoilerSafe = on) } }

    fun setTab(tab: AdventureTab): Job = viewModelScope.launch { storage.updateMapPrefs { it.copy(tab = tab) } }

    fun setFilter(filter: SquareFilter, on: Boolean): Job = viewModelScope.launch {
        storage.updateMapPrefs { it.copy(filters = if (on) it.filters + filter else it.filters - filter) }
    }

    fun setWarrior(id: String?): Job = viewModelScope.launch { storage.updateMapPrefs { it.copy(warrior = id) } }

    fun clearFilters(): Job = viewModelScope.launch { storage.updateMapPrefs { it.copy(filters = emptySet(), warrior = null) } }

    fun setQuery(text: String) = session.update { it.copy(query = text) }

    /** Shows an Unexplored square until the map is closed; nothing is saved. */
    fun reveal(squareId: String) = session.update { it.copy(revealed = it.revealed + squareId) }

    /** Shows a square's search spots, quiz answers and Skulltula locations until the map is closed. */
    fun useCompass(squareId: String) = session.update { it.copy(compassed = it.compassed + squareId) }

    fun select(squareId: String?) {
        _selected.value = squareId
    }

    fun setViewport(viewport: GridViewport) {
        _viewport.value = viewport
    }

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }
}
