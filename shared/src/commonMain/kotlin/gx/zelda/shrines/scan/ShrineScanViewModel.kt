package gx.zelda.shrines.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.PickedImage
import gx.zelda.core.SaveFile
import gx.zelda.screencam.ScreenChrome
import gx.zelda.screencam.ScreenChromeRepository
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/** One picked screenshot or photo of the screen, and what came of it. */
data class ScannedShot(val name: String, val scan: ShotScan, val millis: Long, val fromPhoto: Boolean = false)

data class ShrineScanUiState(
    val saveFile: SaveFile,
    val prefs: ShrinePrefs,
    /** [saveFile]'s shrines as tracked now. */
    val progress: ShrineProgress,
    val shots: List<ScannedShot> = emptyList(),
    /** (done, total) while screenshots are being read. */
    val scanning: Pair<Int, Int>? = null,
    /** Null until at least one screenshot has been read. */
    val review: ScanReview? = null,
    val selections: Map<String, ScanChoice> = emptyMap(),
    /** Undiscovered shrines the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
    /** Shrines changed by Apply; set once it has been written. */
    val applied: Int? = null,
) {
    /** True when [item]'s name stays hidden: spoilers are off and it's undiscovered here. */
    fun isHidden(item: ReviewItem): Boolean =
        prefs.hideUndiscovered &&
            item.current == ShrineState.UNDISCOVERED &&
            (item.section == ReviewSection.UNSURE || item.section == ReviewSection.OFF_SCREEN) &&
            item.shrine.id !in revealed

    val readShots: List<ShotScan.Read> get() = shots.mapNotNull { it.scan as? ShotScan.Read }

    /** What Apply would write now. */
    val changes: Map<String, ShrineState> get() = review?.changes(progress, selections).orEmpty()
}

/**
 * Reads map screenshots and photos of the screen, merges them, and turns the result into suggestions
 * for the save file that was active when the screen opened. Nothing is written until [apply].
 */
class ShrineScanViewModel(
    private val storage: ShrineStorage,
    private val loadCatalog: suspend () -> ShrineCatalog = { ShrineRepository.load(storage.game) },
    private val loadMap: suspend () -> ShrineMapData = { ShrineMapRepository.load(storage.game) },
    private val loadChrome: suspend () -> ScreenChrome = { ScreenChromeRepository.load(storage.game) },
    private val decode: (ByteArray) -> MapImage? = ::decodeMapImage,
    /** Decoding and scanning are CPU-bound, so they run here rather than on the main thread. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<ShrineScanUiState?>(null)
    val state: StateFlow<ShrineScanUiState?> = _state

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private lateinit var catalog: ShrineCatalog
    private lateinit var scanner: ShrineMapScanner
    private lateinit var photos: PhotoShotReader

    /** Choices the user made by hand, kept when more screenshots are added. */
    private var edits = mapOf<String, ScanChoice>()

    init {
        viewModelScope.launch {
            runCatching {
                val c = async { loadCatalog() }
                val m = async { loadMap() }
                val f = async { loadChrome() }
                catalog = c.await()
                val map = m.await()
                scanner = ShrineMapScanner(map)
                photos = PhotoShotReader(scanner, map, f.await())
                val file = storage.game.saveFile(storage.activeSaveFile.first())
                ShrineScanUiState(file, storage.prefs.first(), storage.progress(file.id).first())
            }.onSuccess { initial ->
                _state.value = initial
                storage.progress(initial.saveFile.id).collect { p -> _state.update { it?.copy(progress = p) } }
            }.onFailure { _loadFailed.value = true }
        }
    }

    fun scan(images: List<PickedImage>) {
        val start = _state.value ?: return
        if (images.isEmpty() || start.scanning != null) return
        viewModelScope.launch {
            _state.update { it?.copy(scanning = 0 to images.size, applied = null) }
            val results = mutableListOf<ScannedShot>()
            for ((i, image) in images.withIndex()) {
                results += withContext(work) { scanOne(image) }
                _state.update { it?.copy(scanning = i + 1 to images.size) }
            }
            val shots = start.shots + results
            val read = shots.mapNotNull { it.scan as? ShotScan.Read }
            val progress = storage.progress(start.saveFile.id).first()
            val prefs = storage.prefs.first()
            val review = if (read.isEmpty()) {
                null
            } else {
                ScanReview.build(catalog, mergeShots(read, catalog.shrines.map { it.id }), progress, includeDlc = prefs.showDlc)
            }
            _state.update { s ->
                s?.copy(
                    prefs = prefs,
                    progress = progress,
                    shots = shots,
                    scanning = null,
                    review = review,
                    selections = review?.let(::selectionsFor).orEmpty(),
                )
            }
        }
    }

    private suspend fun scanOne(image: PickedImage): ScannedShot {
        val mark = TimeSource.Monotonic.markNow()
        val bytes = runCatching { image.read() }.getOrNull()
        val decoded = bytes?.let(decode)
        // Anything but a straight-from-the-album screenshot is taken to be a photo of the screen.
        val fromPhoto = decoded != null && (decoded.width != ShrineMapScanner.WIDTH || decoded.height != ShrineMapScanner.HEIGHT)
        val result = when {
            decoded == null -> ShotScan.Rejected(RejectReason.UNREADABLE, "Couldn't open this file as an image.")
            fromPhoto -> photos.read(decoded)
            else -> scanner.scan(decoded)
        }
        return ScannedShot(image.name, result, mark.elapsedNow().inWholeMilliseconds, fromPhoto)
    }

    /** The review's suggestions, overridden by any hand edits that still apply. */
    private fun selectionsFor(review: ScanReview): Map<String, ScanChoice> {
        val options = review.items.associate { it.shrine.id to it.options }
        return review.initialSelections + edits.filter { (id, choice) -> options[id]?.contains(choice) == true }
    }

    fun setChoice(shrineId: String, choice: ScanChoice) {
        edits = edits + (shrineId to choice)
        _state.update { it?.copy(selections = it.selections + (shrineId to choice)) }
    }

    fun reveal(shrineId: String) = _state.update { it?.copy(revealed = it.revealed + shrineId) }

    /** Forgets every screenshot and choice. */
    fun startOver() {
        edits = emptyMap()
        _state.update { s -> s?.let { ShrineScanUiState(it.saveFile, it.prefs, it.progress, revealed = it.revealed) } }
    }

    /** Writes the selected choices to the save file the review was made for, in one update. */
    fun apply() {
        val s = _state.value ?: return
        val review = s.review ?: return
        viewModelScope.launch {
            var changed = 0
            storage.updateProgress(s.saveFile.id) { progress ->
                changed = review.changes(progress, s.selections).size
                review.applyTo(progress, s.selections)
            }
            _state.update { it?.copy(applied = changed) }
        }
    }
}
