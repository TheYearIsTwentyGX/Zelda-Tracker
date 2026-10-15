package gx.zelda.map.data

import gx.zelda.core.DiscoveryState
import gx.zelda.map.model.MapEntry
import gx.zelda.map.model.MapLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a tracker gives the map: one layer of markers and a way to update them. A new tracker adds
 * a layer by implementing this and listing it in [botwMapLayers]; the map handles drawing,
 * filtering, spoilers and the marker sheet.
 */
interface MapLayerProvider {
    val layer: MapLayer

    /** Every entry with a position, including undiscovered ones, in its state on [saveFileId]. */
    fun entries(saveFileId: String): Flow<List<MapEntry>>

    /** The tracker's own "Hide undiscovered" setting; a layer without one emits false. */
    val hideUndiscovered: Flow<Boolean>

    /** Called from the marker sheet with one of [MapLayer.states]. */
    suspend fun setState(saveFileId: String, id: String, state: DiscoveryState)
}

/** Loads a layer's bundled data once, however many flows ask for it; a failed load is retried next time. */
internal class LoadOnce<T : Any>(private val load: suspend () -> T) {
    private val mutex = Mutex()
    private var value: T? = null

    suspend fun get(): T = mutex.withLock { value ?: load().also { value = it } }
}

/** The layer's bundled items, loaded once, in their state from each [progress] value. */
internal fun <T : Any, P> LoadOnce<List<T>>.withProgress(progress: Flow<P>, entry: (T, P) -> MapEntry): Flow<List<MapEntry>> =
    flow {
        val items = get()
        emitAll(progress.map { p -> items.map { entry(it, p) } })
    }
