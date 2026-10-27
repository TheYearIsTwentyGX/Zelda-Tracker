package gx.zelda.art.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.art.ArtCatalog
import gx.zelda.art.ArtPackException
import gx.zelda.art.GameArt
import gx.zelda.art.ImportResult
import gx.zelda.art.InstalledPack
import gx.zelda.core.Game
import gx.zelda.core.Games
import gx.zelda.core.PickedDocument
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.FileSystem

/** How much of one game's art a pack supplies. */
data class Coverage(val game: Game, val have: Int, val of: Int)

data class PackRow(val pack: InstalledPack, val coverage: List<Coverage>)

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val progress: Float) : ImportState
    data class Done(val result: ImportResult, val coverage: List<Coverage>) : ImportState
    data class Failed(val message: String) : ImportState
}

/** Settings → Art packs: the installed packs with their coverage, importing and removing. */
class ArtPacksViewModel(
    private val catalog: suspend (Game) -> List<String> = ArtCatalog::ids,
) : ViewModel() {
    /** Each game's asset ids, for games that draw any art; read once. */
    private val ids = viewModelScope.async(start = CoroutineStart.LAZY) {
        Games.all.associateWith { runCatching { catalog(it) }.getOrDefault(emptyList()).toSet() }.filterValues { it.isNotEmpty() }
    }

    val packs: StateFlow<List<PackRow>?> = combine(GameArt.packs, flow { emit(ids.await()) }) { packs, ids ->
        packs.map { PackRow(it, coverage(it.assets, ids)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val import: StateFlow<ImportState> = _import

    val available: Boolean get() = GameArt.store != null

    fun import(document: PickedDocument) {
        val store = GameArt.store ?: return
        if (_import.value is ImportState.Running) return
        _import.value = ImportState.Running(0f)
        viewModelScope.launch {
            val incoming = store.root / "incoming.zip"
            _import.value = try {
                FileSystem.SYSTEM.createDirectories(store.root)
                document.copyTo(incoming.toString())
                val result = GameArt.import(incoming) { _import.value = ImportState.Running(it) }
                ImportState.Done(result, coverage(result.pack.assets, ids.await()))
            } catch (e: ArtPackException) {
                ImportState.Failed(e.message ?: "The pack couldn't be imported.")
            } catch (e: Exception) {
                ImportState.Failed("The pack couldn't be imported (${e.message ?: e::class.simpleName}).")
            } finally {
                FileSystem.SYSTEM.delete(incoming, mustExist = false)
            }
        }
    }

    fun dismissImport() {
        if (_import.value !is ImportState.Running) _import.value = ImportState.Idle
    }

    fun remove(pack: InstalledPack) {
        viewModelScope.launch { runCatching { GameArt.remove(pack) } }
    }

    private fun coverage(assets: List<String>, ids: Map<Game, Set<String>>): List<Coverage> {
        val have = assets.toSet()
        return ids.mapNotNull { (game, wanted) ->
            val n = wanted.count { it in have }
            if (n == 0) null else Coverage(game, n, wanted.size)
        }
    }
}
