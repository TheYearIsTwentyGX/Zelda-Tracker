package gx.zelda.art

import gx.zelda.resources.Res
import gx.zelda.resources.allDrawableResources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.compose.resources.DrawableResource

/** Where an icon's picture comes from. */
sealed interface ArtRef {
    /** A drawable bundled with the app. */
    data class Bundled(val resource: DrawableResource) : ArtRef

    /** A file from an installed art pack; [vector] for an Android vector drawable. */
    data class File(val path: Path) : ArtRef {
        val vector: Boolean get() = path.name.endsWith(".xml")
    }
}

/**
 * The one place the app asks for art. Every icon, map tile and Adventure Map square is looked up
 * by asset id ([ArtIds]): an installed art pack's file wins, then the art bundled with the app
 * (while [bundled] is on), and otherwise the caller draws its own fallback.
 *
 * A process-wide singleton, like the DataStore: the platform [attach]es the pack store at start-up,
 * and screens follow [index] so an import or removal shows at once.
 */
object GameArt {
    private val _index = MutableStateFlow(ArtIndex.EMPTY)
    val index: StateFlow<ArtIndex> = _index.asStateFlow()

    private val _packs = MutableStateFlow<List<InstalledPack>>(emptyList())
    val packs: StateFlow<List<InstalledPack>> = _packs.asStateFlow()

    /** Whether the drawables and files bundled with the app back up the packs. */
    var bundled: Boolean = true

    var store: ArtPackStore? = null
        private set

    private val lock = Mutex()
    private var ready = CompletableDeferred(Unit)
    private val fs = FileSystem.SYSTEM

    /** Reads [store]'s packs; tiles asked for meanwhile wait, so a map never caches a missing tile. */
    suspend fun attach(store: ArtPackStore) {
        val loading = CompletableDeferred<Unit>()
        ready = loading
        this.store = store
        lock.withLock { withContext(Dispatchers.Default) { refresh(store) } }
        loading.complete(Unit)
    }

    /** Forgets the store (for tests). */
    fun detach() {
        store = null
        _index.value = ArtIndex.EMPTY
        _packs.value = emptyList()
        ready = CompletableDeferred(Unit)
        bundled = true
    }

    suspend fun import(zip: Path, onProgress: (Float) -> Unit = {}): ImportResult = withStore { store ->
        store.import(zip, onProgress).also { refresh(store) }
    }

    suspend fun remove(pack: InstalledPack) = withStore { store ->
        store.remove(pack.dir)
        refresh(store)
    }

    private suspend fun <T> withStore(block: (ArtPackStore) -> T): T {
        val store = store ?: throw ArtPackException("Art packs aren't available.")
        return lock.withLock { withContext(Dispatchers.Default) { block(store) } }
    }

    private fun refresh(store: ArtPackStore) {
        _packs.value = store.installed()
        _index.value = store.index()
    }

    /** The picture for [id], or null if neither a pack nor the app has one. */
    fun icon(id: String, index: ArtIndex = this.index.value): ArtRef? {
        index.icons[id]?.let { return ArtRef.File(it) }
        if (!bundled) return null
        return ArtIds.bundledName(id)?.let { Res.allDrawableResources[it] }?.let(ArtRef::Bundled)
    }

    /** One tile of the pyramid [id]; [bundledPath] is the tile's path in the app's files, if it has one. */
    suspend fun tile(id: String, level: Int, row: Int, col: Int, bundledPath: String?): ByteArray? {
        ready.await()
        val set = index.value.tilesets[id]
        return if (set != null) read(set.dir / set.value.file("$level/$row/$col")) else bundled(bundledPath)
    }

    /** One square of the Adventure Map [id], or its preview when [square] is null. */
    suspend fun square(id: String, square: String?, bundledPath: String?): ByteArray? {
        ready.await()
        val set = index.value.squaresets[id]
        if (set != null) {
            val file = if (square == null) set.value.preview else set.value.file(square).takeIf { square in set.value.squares }
            return file?.let { read(set.dir / it) }
        }
        return bundled(bundledPath)
    }

    /** Whether a pack supplies the Adventure Map [id]'s squares. */
    fun hasSquares(id: String): Boolean = id in index.value.squaresets

    private suspend fun bundled(path: String?): ByteArray? =
        if (bundled && path != null) runCatching { Res.readBytes(path) }.getOrNull() else null

    suspend fun read(path: Path): ByteArray? = withContext(Dispatchers.Default) {
        runCatching { fs.read(path) { readByteArray() } }.getOrNull()
    }
}
