package gx.zelda.overview

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.Game
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.activeSaveFileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where one overview row stands. */
sealed interface RowStatus {
    data object Loading : RowStatus

    /** The tracker's dataset or storage couldn't be read; the other rows are unaffected. */
    data object Unavailable : RowStatus

    data class Ready(val summary: ProgressSummary) : RowStatus
}

data class OverviewRow(val kind: TrackerKind, val title: String, val status: RowStatus)

data class OverviewUiState(
    val saveFile: SaveFile,
    val saveFiles: List<SaveFile>,
    val rows: List<OverviewRow>,
) {
    /**
     * What the loaded rows add toward the in-game map completion %: Korok seeds, shrines and
     * Divine Beasts, each worth the same. Named locations count too, but no tracker records them,
     * so this is a count of items, not a percentage. Null while any row is still loading, or
     * when no row counts toward it.
     */
    val mapItems: List<ItemCount>?
        get() {
            if (rows.any { it.status == RowStatus.Loading }) return null
            return rows.mapNotNull { (it.status as? RowStatus.Ready)?.summary?.mapItems }.takeIf { it.isNotEmpty() }
        }
}

/** The Progress Overview: one row per [ProgressProvider], all for the game's active save file. */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    dataStore: DataStore<Preferences>,
    private val game: Game,
    private val providers: List<ProgressProvider> = overviewProviders(dataStore, game),
    /** Where datasets are parsed: off the main thread, since the overview loads every tracker's at once. */
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val activeStore = activeSaveFileStore(dataStore, game)

    private val activeFile: Flow<SaveFile> = activeStore.data.map(game::saveFile).distinctUntilChanged()

    val state: StateFlow<OverviewUiState?> = activeFile
        .flatMapLatest { file -> rows(file).map { OverviewUiState(file, game.saveFiles, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSaveFile(id: String): Job = viewModelScope.launch { activeStore.update { game.saveFile(id).id } }

    private fun rows(file: SaveFile): Flow<List<OverviewRow>> =
        if (providers.isEmpty()) flowOf(emptyList()) else combine(providers.map { row(it, file) }) { it.toList() }

    /** One provider's row. Its failure, even thrown before the flow starts, only marks this row unavailable. */
    private fun row(provider: ProgressProvider, file: SaveFile): Flow<OverviewRow> =
        flow { emitAll(provider.summary(file)) }
            .map<ProgressSummary, RowStatus> { RowStatus.Ready(it) }
            .flowOn(loadDispatcher)
            .onStart { emit(RowStatus.Loading) }
            .catch { emit(RowStatus.Unavailable) }
            .map { OverviewRow(provider.kind, provider.title, it) }
}
