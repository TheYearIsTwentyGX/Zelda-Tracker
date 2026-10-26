package gx.zelda.potions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.armor.model.MaterialSort
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope
import gx.zelda.core.SaveFile
import gx.zelda.potions.model.PotionBook
import gx.zelda.potions.model.PotionTotals
import gx.zelda.potions.model.RecipeNeeds
import gx.zelda.potions.model.RecipeStep
import gx.zelda.upgrades.data.UpgradeRepository
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradeItem
import gx.zelda.upgrades.model.UpgradeProgress
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

/** A save file as the picker shows it: recipe steps made of all. */
data class PotionFileSummary(val file: SaveFile, val made: Int)

/** One potion on the book's grid. */
data class PotionCard(val potion: UpgradeItem, val made: Int, val steps: Int) {
    val done: Boolean get() = made >= steps

    /** The form to show: the most infused one made, or the base potion. */
    val shownLevel: Int get() = (made - 1).coerceAtLeast(0)
}

data class PotionUiState(
    val catalog: UpgradeCatalog,
    val progress: UpgradeProgress,
    val saveFile: SaveFile,
    val saveFiles: List<PotionFileSummary>,
    val totalsMode: UpgradeTotalsMode = UpgradeTotalsMode.ALL,
    /** Item Upgrades' material sort, which the two totals screens share. */
    val materialSort: MaterialSort = MaterialSort.MOST_NEEDED,
) {
    val potions: List<UpgradeItem> = PotionBook.potions(catalog)

    val cards: List<PotionCard> = potions.map { PotionCard(it, PotionBook.made(it, progress), PotionBook.steps(it)) }

    /** Steps made of all, over every potion. */
    val made: Int get() = cards.sumOf { it.made }
    val steps: Int get() = cards.sumOf { it.steps }

    /** The Totals screen's bugs and Rupees, in [totalsMode]. */
    val totals: PotionTotals by lazy { PotionBook.totals(catalog, progress, totalsMode) }

    fun potion(id: String): UpgradeItem? = potions.firstOrNull { it.id == id }

    fun recipe(potion: UpgradeItem): List<RecipeStep> = PotionBook.recipe(potion, progress)

    fun needs(potion: UpgradeItem): RecipeNeeds = PotionBook.needs(potion, progress)

    /** The book's toggle: every potion bought and fully infused, or none. */
    fun bulkScope(): BulkScope = BulkScope.of(
        label = "Potion Infusions",
        items = potions,
        noun = "potion",
        note = "A potion counts as complete once it's bought and every infusion is made; clearing one clears all its steps.",
        isDone = { PotionBook.isDone(it, progress) },
        isStarted = { PotionBook.made(it, progress) > 0 },
    )
}

/**
 * The Potion Infusions recipe book. Its progress is Item Upgrades' potions ([UpgradeStorage]), so
 * the two always agree, and every change is one write to the file on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PotionViewModel(
    private val storage: UpgradeStorage,
    private val loadCatalog: suspend () -> UpgradeCatalog = { UpgradeRepository.load(storage.game) },
) : ViewModel() {
    private val catalog = MutableStateFlow<UpgradeCatalog?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the bundled dataset is missing or unreadable; [state] then stays null. */
    val loadFailed: StateFlow<Boolean> = _loadFailed

    /** All potions to start with: the book is a plan for every recipe, and Luv sells each one from the start. */
    private val totalsMode = MutableStateFlow(UpgradeTotalsMode.ALL)

    /** The file and its progress together, so a switch never pairs the new file with the old file's progress. */
    private val activeProgress: Flow<Pair<SaveFile, UpgradeProgress>> = storage.activeSaveFile
        .map(storage.game::saveFile)
        .flatMapLatest { file -> storage.progress(file.id).map { file to it } }

    private val summaries: Flow<List<UpgradeProgress>> =
        combine(storage.game.saveFiles.map { storage.progress(it.id) }) { it.toList() }

    val state: StateFlow<PotionUiState?> = combine(
        catalog.filterNotNull(),
        activeProgress,
        summaries,
        totalsMode,
        storage.prefs,
    ) { catalog, (file, progress), all, mode, prefs ->
        val potions = PotionBook.potions(catalog)
        PotionUiState(
            catalog = catalog,
            progress = progress,
            saveFile = file,
            saveFiles = storage.game.saveFiles.zip(all) { f, p -> PotionFileSummary(f, potions.sumOf { PotionBook.made(it, p) }) },
            totalsMode = mode,
            materialSort = prefs.materialSort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { loadCatalog() }
                .onSuccess { catalog.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    /** Makes step [index] of [potionId]'s recipe (0 is buying it), with every step before; or unmakes it and every step after. */
    fun setStepMade(potionId: String, index: Int, made: Boolean): Job? {
        val state = state.value ?: return null
        val potion = state.potion(potionId) ?: return null
        return viewModelScope.launch {
            storage.update(state.saveFile.id) { p -> p.withCopy(potion.id) { PotionBook.withStep(potion, it, index, made) } }
        }
    }

    private val _bulkChange = MutableStateFlow<BulkChange<UpgradeStorage.UpgradeSnapshot>?>(null)

    /** The last mark-all or clear-all, until its snackbar goes or its Undo is tapped. */
    val bulkChange: StateFlow<BulkChange<*>?> = _bulkChange

    /** Makes every step of every potion, or clears them all, on the file on screen, in one write. */
    fun setAllMade(made: Boolean): Job? {
        val state = state.value ?: return null
        val potions = state.potions.takeIf { it.isNotEmpty() } ?: return null
        val scope = state.bulkScope()
        val byId = potions.associateBy { it.id }
        return viewModelScope.launch {
            val snapshot = storage.setAll(state.saveFile.id, byId.keys) { id, _ -> PotionBook.allMade(byId.getValue(id), made) }
            val changed = potions.count { PotionBook.allMade(it, made) != (snapshot.before[it.id] ?: PieceProgress()) }
            if (changed > 0) {
                val action = if (made) BulkAction.MARK else BulkAction.CLEAR
                _bulkChange.value = BulkChange(action, changed, scope.noun, scope.plural, state.saveFile, snapshot)
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

    fun setSaveFile(id: String): Job = viewModelScope.launch { storage.setActiveSaveFile(id) }

    fun setTotalsMode(mode: UpgradeTotalsMode) { totalsMode.value = mode }

    /** Sorts both totals screens' materials, this and Item Upgrades'. */
    fun setMaterialSort(sort: MaterialSort): Job = viewModelScope.launch { storage.updatePrefs { it.copy(materialSort = sort) } }
}
