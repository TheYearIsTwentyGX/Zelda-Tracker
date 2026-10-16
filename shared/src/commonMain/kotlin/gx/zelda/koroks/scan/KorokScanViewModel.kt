package gx.zelda.koroks.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.DiscoveryState
import gx.zelda.core.SaveFile
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
import gx.zelda.screencam.Point
import gx.zelda.screencam.ScreenChrome
import gx.zelda.screencam.ScreenChromeRepository
import gx.zelda.screencam.live.BundledTiles
import gx.zelda.screencam.live.ChromeTracker
import gx.zelda.screencam.live.GrayImage
import gx.zelda.screencam.live.KorokMarkerReader
import gx.zelda.screencam.live.KorokMarkerTemplate
import gx.zelda.screencam.live.KorokSpot
import gx.zelda.screencam.live.KorokVerdict
import gx.zelda.screencam.live.LiveKorokScanner
import gx.zelda.screencam.live.LivePhase
import gx.zelda.screencam.live.MapLocator
import gx.zelda.screencam.live.TileMosaic
import gx.zelda.screencam.live.logLiveStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** A Korok spot in the latest frame, in frame pixels, for the overlay. */
data class SpotMark(val id: String, val x: Double, val y: Double, val verdict: KorokVerdict)

/** What the camera sees right now. */
data class LiveView(
    val phase: LivePhase = LivePhase.FINDING_SCREEN,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** The screen's corners in frame pixels, when tracked. */
    val screen: List<Point>? = null,
    val spots: List<SpotMark> = emptyList(),
    /** fps and per-stage milliseconds, as logged. */
    val stats: String = "",
)

/** The scan's findings, ready to apply. */
data class KorokScanReview(
    /** Markers seen on Koroks this save doesn't have collected: suggested, pre-ticked. */
    val toCollect: List<Korok>,
    /** Markers on Koroks already collected here. */
    val alreadyCollected: Int,
    /** Spots the scan couldn't decide: listed for the user. */
    val unsure: List<Korok>,
    /** Spots seen clearly empty; never written. */
    val empty: Int,
)

data class KorokScanUiState(
    val saveFile: SaveFile,
    val prefs: KorokPrefs,
    val progress: KorokProgress,
    val live: LiveView = LiveView(),
    val found: Int = 0,
    val empty: Int = 0,
    val unsure: Int = 0,
    /** Set when the user is reviewing instead of scanning. */
    val review: KorokScanReview? = null,
    /** Koroks ticked to be marked collected. */
    val selected: Set<String> = emptySet(),
    /** Undiscovered Koroks the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
    /** Koroks changed by Apply; set once it's written. */
    val applied: Int? = null,
) {
    /** True when [korok]'s name stays hidden: spoilers off and it's undiscovered on this save. */
    fun isHidden(korok: Korok): Boolean =
        prefs.hideUndiscovered && korok.id !in progress.found && korok.id !in progress.completed && korok.id !in revealed
}

/**
 * The live Korok scan for the save file that was active when the screen opened: frames in from the
 * camera, an overlay and counts out, then a review whose Apply marks the seen markers' Koroks
 * collected. Found Koroks are only ever added; nothing is downgraded.
 */
class KorokScanViewModel(
    private val storage: KorokStorage,
    private val loadCatalog: suspend () -> KorokCatalog = { KorokRepository.load(storage.game) },
    private val makeScanner: suspend (KorokCatalog) -> LiveKorokScanner = { catalog -> defaultScanner(storage, catalog) },
) : ViewModel() {
    private val _state = MutableStateFlow<KorokScanUiState?>(null)
    val state: StateFlow<KorokScanUiState?> = _state

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private lateinit var catalog: KorokCatalog
    private var scanner: LiveKorokScanner? = null
    private val busy = Mutex()
    private var lastLog = TimeSource.Monotonic.markNow()

    init {
        viewModelScope.launch {
            runCatching {
                catalog = loadCatalog()
                scanner = makeScanner(catalog)
                val file = storage.game.saveFile(storage.activeSaveFile.first())
                KorokScanUiState(file, storage.prefs.first(), storage.progress(file.id).first())
            }.onSuccess { initial ->
                _state.value = initial
                storage.progress(initial.saveFile.id).collect { p -> _state.update { it?.copy(progress = p) } }
            }.onFailure { _loadFailed.value = true }
        }
    }

    /**
     * One camera frame. Runs on the camera's thread; a frame arriving while another is being read
     * is skipped (the camera drops it anyway).
     */
    suspend fun analyse(frame: GrayImage) {
        val s = scanner ?: return
        if (_state.value?.review != null) return
        if (!busy.tryLock()) return
        try {
            val r = s.process(frame)
            val verdicts = s.evidence.verdicts()
            val h = r.screenToFrame
            val spots = if (h == null) emptyList() else r.markers.map { m ->
                val p = h.map(m.canonical)
                SpotMark(m.id, p.x, p.y, verdicts[m.id] ?: KorokVerdict.UNSURE)
            }
            val live = LiveView(
                phase = r.phase,
                frameWidth = frame.width,
                frameHeight = frame.height,
                screen = h?.let { hh -> ScreenChrome.CORNERS.map(hh::map) },
                spots = spots,
                stats = s.stats.summary(),
            )
            _state.update { st ->
                st?.copy(
                    live = live,
                    found = verdicts.count { it.value == KorokVerdict.FOUND },
                    empty = verdicts.count { it.value == KorokVerdict.EMPTY },
                    unsure = verdicts.count { it.value == KorokVerdict.UNSURE },
                )
            }
            if (lastLog.elapsedNow().inWholeMilliseconds >= 1000) {
                lastLog = TimeSource.Monotonic.markNow()
                logLiveStats("${r.phase} ${s.stats.summary()} | Koroks in view ${live.spots.size}")
            }
        } finally {
            busy.unlock()
        }
    }

    /** Stops reading frames and lists what was found. */
    fun finish() {
        val s = scanner ?: return
        val st = _state.value ?: return
        val verdicts = s.evidence.verdicts()
        val byId = catalog.koroks.associateBy { it.id }
        val collected = st.progress.completed
        val found = verdicts.filterValues { it == KorokVerdict.FOUND }.keys.mapNotNull(byId::get)
        val review = KorokScanReview(
            toCollect = found.filter { it.id !in collected },
            alreadyCollected = found.count { it.id in collected },
            unsure = verdicts.filterValues { it == KorokVerdict.UNSURE }.keys.mapNotNull(byId::get).filter { it.id !in collected },
            empty = verdicts.count { it.value == KorokVerdict.EMPTY },
        )
        _state.update { it?.copy(review = review, selected = review.toCollect.map { k -> k.id }.toSet(), applied = null) }
    }

    /** Back to the camera; the evidence so far is kept. */
    fun keepScanning() = _state.update { it?.copy(review = null) }

    fun setSelected(korokId: String, selected: Boolean) = _state.update {
        it?.copy(selected = if (selected) it.selected + korokId else it.selected - korokId)
    }

    fun reveal(korokId: String) = _state.update { it?.copy(revealed = it.revealed + korokId) }

    /** Marks the ticked Koroks collected on the review's save file, in one update. */
    fun apply(): Job? {
        val st = _state.value ?: return null
        if (st.review == null) return null
        return viewModelScope.launch {
            var changed = 0
            storage.updateProgress(st.saveFile.id) { progress ->
                val todo = st.selected.filter { it !in progress.completed }
                changed = todo.size
                todo.fold(progress) { p, id -> p.withState(id, DiscoveryState.COMPLETED) }
            }
            _state.update { it?.copy(applied = changed) }
        }
    }

    companion object {
        suspend fun defaultScanner(storage: KorokStorage, catalog: KorokCatalog): LiveKorokScanner {
            val game = storage.game
            return LiveKorokScanner(
                ScreenChromeRepository.load(game),
                ChromeTracker(ChromeTracker.loadReference(game)),
                MapLocator(TileMosaic(BundledTiles)),
                KorokMarkerReader(KorokMarkerTemplate.load(game), catalog.koroks.map { KorokSpot(it.id, it.x, it.z) }),
            )
        }
    }
}
