package gx.zelda.zeldanotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.DiscoveryState
import gx.zelda.core.PickedImage
import gx.zelda.core.PickedVideo
import gx.zelda.core.SaveFile
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
import gx.zelda.screencam.live.KorokSpot
import gx.zelda.shrines.scan.MapImage
import gx.zelda.shrines.scan.decodeMapImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** One picked screenshot or recording, and what came of it, for the list under the review. */
data class NotesInput(
    val name: String,
    /** "Placed · 34 Koroks judged", a rejection's reason, or why a placed view judged nothing. */
    val summary: String,
    val ok: Boolean,
)

/** The import's findings for [NotesImportUiState.target], ready to apply. */
data class NotesReview(
    /** Missing from Zelda Notes and not yet collected here: suggested, pre-ticked. */
    val toCollect: List<Korok>,
    /** Missing from Zelda Notes and already collected here. */
    val alreadyCollected: Int,
    /** Couldn't be told (covered or crowded); listed so the user can tick any they know. */
    val unsure: List<Korok>,
    /** Drawn in Zelda Notes, so still to collect in the synced save, but marked collected here. Never unmarked. */
    val shownButCollectedHere: List<Korok>,
    /** Koroks judged at all, and the views that judged them, for the coverage map. */
    val judged: Int,
    val views: List<WorldRect>,
    val verdicts: Map<String, NotesVerdict>,
    /** World (x, z) of each judged Korok, for the coverage map. */
    val points: Map<String, Pair<Double, Double>>,
    /** Koroks in the game: 900. */
    val total: Int,
)

data class NotesImportUiState(
    val saveFiles: List<SaveFile>,
    /** The save file Apply writes to: picked explicitly on the review, the active one to start with. */
    val target: SaveFile,
    val prefs: KorokPrefs,
    /** [target]'s Koroks as tracked now. */
    val progress: KorokProgress,
    val inputs: List<NotesInput> = emptyList(),
    /** (done, total) while screenshots or a recording are being read. */
    val reading: Pair<Int, Int>? = null,
    /** Null until at least one view has judged a Korok. */
    val review: NotesReview? = null,
    /** Koroks ticked to be marked collected. */
    val selected: Set<String> = emptySet(),
    /** Undiscovered Koroks the user tapped to see while spoilers are hidden. */
    val revealed: Set<String> = emptySet(),
    /** Koroks changed by Apply, and the file they went to; set once it's written. */
    val applied: Pair<Int, SaveFile>? = null,
) {
    fun isHidden(korok: Korok): Boolean =
        prefs.hideUndiscovered && korok.id !in progress.found && korok.id !in progress.completed && korok.id !in revealed

    /** Seeds [target] would have collected after Apply, out of [total]. */
    fun collectedAfterApply(total: Int): Int = (progress.completed + selected).size.coerceAtMost(total)

    /** As Zelda Notes' Categories sheet shows it: a whole percentage. */
    fun percentAfterApply(total: Int): Int = if (total == 0) 0 else (collectedAfterApply(total) * 100.0 / total).roundToInt()
}

/**
 * Korok Seeds → ⋮ → Import from Zelda Notes: reads Navigation screenshots or a screen recording
 * (with Hide Discovered Destinations on), merges what every view says, and suggests marking the
 * Koroks Zelda Notes no longer draws as collected, on a save file the user picks. Nothing is written
 * until [apply], and nothing is ever unmarked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesImportViewModel(
    private val storage: KorokStorage,
    private val loadCatalog: suspend () -> KorokCatalog = { KorokRepository.load(storage.game) },
    private val makeReader: suspend (KorokCatalog) -> NotesReader = { c -> NotesReader.load(storage.game, c.koroks.map { KorokSpot(it.id, it.x, it.z) }) },
    private val decode: (ByteArray) -> MapImage? = ::decodeMapImage,
    /** Decoding and reading are CPU-bound, so they run here rather than on the main thread. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<NotesImportUiState?>(null)
    val state: StateFlow<NotesImportUiState?> = _state

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private lateinit var catalog: KorokCatalog
    private lateinit var reader: NotesReader
    private var evidence = NotesEvidence()
    private val targetId = MutableStateFlow<String?>(null)

    /** Ticks the user changed by hand, kept when more views come in or the target changes. */
    private var edits = mapOf<String, Boolean>()

    init {
        viewModelScope.launch {
            runCatching {
                catalog = loadCatalog()
                reader = makeReader(catalog)
                val file = storage.game.saveFile(storage.activeSaveFile.first())
                NotesImportUiState(storage.game.saveFiles, file, storage.prefs.first(), storage.progress(file.id).first())
            }.onSuccess { initial ->
                _state.value = initial
                targetId.value = initial.target.id
                targetId.flatMapLatest { id -> storage.progress(id ?: initial.target.id) }.collect { p ->
                    // While reading, the evidence is being added to off the main thread; the review is
                    // rebuilt once reading ends.
                    _state.update { s -> s?.copy(progress = p)?.let { if (it.reading == null) withReview(it) else it } }
                }
            }.onFailure { _loadFailed.value = true }
        }
    }

    /** Points Apply at another save file; the review is rebuilt against its progress. */
    fun setTarget(saveFileId: String) {
        val file = storage.game.saveFile(saveFileId)
        _state.update { it?.copy(target = file, applied = null) }
        targetId.value = file.id
    }

    fun readImages(images: List<PickedImage>): Job? {
        val start = _state.value ?: return null
        if (images.isEmpty() || start.reading != null) return null
        return viewModelScope.launch {
            _state.update { it?.copy(reading = 0 to images.size, applied = null) }
            val inputs = mutableListOf<NotesInput>()
            for ((i, image) in images.withIndex()) {
                inputs += withContext(work) {
                    val decoded = runCatching { image.read() }.getOrNull()?.let(decode)
                    if (decoded == null) {
                        NotesInput(image.name, "Couldn't open this file as an image.", ok = false)
                    } else {
                        val read = reader.read(decoded)
                        evidence.add(read)
                        describe(image.name, read)
                    }
                }
                _state.update { it?.copy(reading = i + 1 to images.size) }
            }
            _state.update { s -> s?.copy(inputs = s.inputs + inputs, reading = null)?.let(::withReview) }
        }
    }

    /**
     * Reads a recording a few frames a second, each frame's search starting from the last placed
     * pose so frames with only a few Koroks in view still place.
     */
    fun readVideo(video: PickedVideo): Job? {
        val start = _state.value ?: return null
        if (start.reading != null) return null
        return viewModelScope.launch {
            _state.update { it?.copy(reading = 0 to 1, applied = null) }
            var pose: NotesPose? = null
            var misses = 0
            var frames = 0
            var placed = 0
            val notes = mutableMapOf<String, Int>()
            val before = evidence.verdicts().size
            val result = runCatching {
                withContext(work) {
                    video.frames(FRAME_MS, MAX_FRAME_WIDTH) { i, total, frame ->
                        val read = reader.read(frame, pose)
                        frames++
                        if (read is NotesRead.Placed) {
                            placed++
                            pose = read.pose
                            misses = 0
                            read.note?.let { notes[it] = (notes[it] ?: 0) + 1 }
                        } else if (++misses > MAX_MISSES) {
                            // Lost for a second: search the whole map again.
                            pose = null
                        }
                        evidence.add(read)
                        _state.update { it?.copy(reading = i + 1 to total) }
                    }
                }
            }
            val summary = when {
                result.isFailure -> "Couldn't read this video."
                frames == 0 -> "No frames could be read."
                else -> buildString {
                    append("$placed of $frames frames placed · ${evidence.verdicts().size - before} more Koroks judged")
                    notes.maxByOrNull { it.value }?.let { (note, n) -> if (n * 2 >= placed) append(" · $note") }
                }
            }
            val input = NotesInput(video.name, summary, ok = result.isSuccess && placed > 0)
            _state.update { s -> s?.copy(inputs = s.inputs + input, reading = null)?.let(::withReview) }
        }
    }

    private fun describe(name: String, read: NotesRead): NotesInput = when (read) {
        is NotesRead.Rejected -> NotesInput(name, read.reason, ok = false)
        is NotesRead.Placed -> when {
            read.note != null -> NotesInput(name, read.note, ok = false)
            else -> NotesInput(name, "Placed · ${read.verdicts.size} Koroks judged", ok = true)
        }
    }

    /** [s] with its review rebuilt from the evidence so far, against [s]'s target progress. */
    private fun withReview(s: NotesImportUiState): NotesImportUiState {
        val verdicts = evidence.verdicts()
        if (verdicts.isEmpty()) return s.copy(review = null, selected = emptySet())
        val collected = s.progress.completed
        val ids = { v: NotesVerdict -> catalog.koroks.filter { verdicts[it.id] == v } }
        val absent = ids(NotesVerdict.ABSENT)
        val review = NotesReview(
            toCollect = absent.filter { it.id !in collected },
            alreadyCollected = absent.count { it.id in collected },
            unsure = ids(NotesVerdict.UNSURE).filter { it.id !in collected },
            shownButCollectedHere = ids(NotesVerdict.PRESENT).filter { it.id in collected },
            judged = verdicts.size,
            views = evidence.views.toList(),
            verdicts = verdicts.toMap(),
            points = catalog.koroks.filter { it.id in verdicts }.associate { it.id to (it.x to it.z) },
            total = catalog.koroks.size,
        )
        val offered = (review.toCollect + review.unsure).map { it.id }.toSet()
        val suggested = review.toCollect.map { it.id }.toSet()
        val selected = offered.filter { id -> edits[id] ?: (id in suggested) }.toSet()
        return s.copy(review = review, selected = selected)
    }

    fun setSelected(korokId: String, selected: Boolean) {
        edits = edits + (korokId to selected)
        _state.update { it?.copy(selected = if (selected) it.selected + korokId else it.selected - korokId) }
    }

    fun reveal(korokId: String) = _state.update { it?.copy(revealed = it.revealed + korokId) }

    /** Forgets every view and tick. */
    fun startOver() {
        evidence = NotesEvidence()
        edits = emptyMap()
        _state.update { s -> s?.copy(inputs = emptyList(), review = null, selected = emptySet(), applied = null) }
    }

    /** Marks the ticked Koroks collected on the target save file, in one update. */
    fun apply(): Job? {
        val st = _state.value ?: return null
        if (st.review == null) return null
        val target = st.target
        return viewModelScope.launch {
            var changed = 0
            storage.updateProgress(target.id) { progress ->
                val todo = st.selected.filter { it !in progress.completed }
                changed = todo.size
                todo.fold(progress) { p, id -> p.withState(id, DiscoveryState.COMPLETED) }
            }
            _state.update { it?.copy(applied = changed to target) }
        }
    }

    companion object {
        /** A recording is read three frames a second: enough overlap to carry the pose while panning. */
        const val FRAME_MS = 333L

        /** A pose is carried past this many frames that can't be placed (a moment with almost no Koroks in view). */
        const val MAX_MISSES = 3

        /** Frames are scaled down to this before reading; the reader works smaller still. */
        const val MAX_FRAME_WIDTH = 720
    }
}
