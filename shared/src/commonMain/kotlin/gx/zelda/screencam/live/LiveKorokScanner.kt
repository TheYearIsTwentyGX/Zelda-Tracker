package gx.zelda.screencam.live

import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import gx.zelda.screencam.ScreenChrome
import kotlin.time.TimeSource

/** Where the live scan is. */
enum class LivePhase {
    /** Looking for the whole screen in the camera's view. */
    FINDING_SCREEN,

    /** Screen found; working out where on the map it shows. */
    FINDING_PLACE,

    /** Following the screen and the map, reading markers. */
    TRACKING,

    /** The view doesn't match the map at max zoom: probably zoomed out. */
    NOT_MAX_ZOOM,
}

/** Milliseconds spent in each stage of one frame. */
data class StageTimes(
    val acquire: Double = 0.0,
    val track: Double = 0.0,
    val view: Double = 0.0,
    val locate: Double = 0.0,
    val markers: Double = 0.0,
) {
    val total: Double get() = acquire + track + view + locate + markers

    operator fun plus(o: StageTimes) = StageTimes(acquire + o.acquire, track + o.track, view + o.view, locate + o.locate, markers + o.markers)

    operator fun div(n: Int) = StageTimes(acquire / n, track / n, view / n, locate / n, markers / n)
}

/** What one frame gave. */
class LiveFrameResult(
    val phase: LivePhase,
    /** Canonical → frame pixels, when the screen is tracked. */
    val screenToFrame: Homography?,
    /** World position at canonical (0, 0), when the map is located. */
    val mapOrigin: Point?,
    /** Whether this frame counted as evidence: screen and map both held. */
    val tracked: Boolean,
    val markers: List<KorokMarkerReader.Reading>,
    val times: StageTimes,
    /** Diagnostics: the chrome correlation and the map match's peak and ratio. */
    val chromeCorrelation: Double = 0.0,
    val mapPeak: Double = 0.0,
    val mapRatio: Double = 0.0,
)

/**
 * The live Korok scan, frame by frame: find the screen, follow it with [ChromeTracker], find and
 * follow the map's position with [MapLocator], and read every Korok spot in view into [evidence].
 * Frames are grey (a camera's luma plane). Not thread-safe: feed it one frame at a time.
 */
class LiveKorokScanner(
    private val chrome: ScreenChrome,
    private val tracker: ChromeTracker,
    private val locator: MapLocator,
    private val markers: KorokMarkerReader,
) {
    val evidence = KorokEvidence(markers.stacked)

    private var screen: Homography? = null
    private var lostScreen = 0
    private var origin: Point? = null
    private var velocity = Point(0.0, 0.0)

    /** Frames since the map was last located. */
    private var sinceFix = 0

    /** Rolling statistics for the stats overlay and logcat. */
    val stats = LiveStats()

    suspend fun process(frame: GrayImage): LiveFrameResult {
        val clock = TimeSource.Monotonic
        var times = StageTimes()

        // 1. The screen.
        var guess = screen
        if (guess == null) {
            val t = clock.markNow()
            val found = chrome.locate(frame.toMapImage())
            times = times.copy(acquire = t.elapsedNow().inWholeMicroseconds / 1000.0)
            if (found == null || found.second < MIN_FRAME_SCORE) return done(LiveFrameResult(LivePhase.FINDING_SCREEN, null, null, false, emptyList(), times))
            guess = found.first
        }
        val t0 = clock.markNow()
        val pyramid = ChromeTracker.Frame(frame)
        val result = tracker.track(pyramid, guess)
        times = times.copy(track = t0.elapsedNow().inWholeMicroseconds / 1000.0)
        if (result == null || result.correlation < KEEP_CHROME) {
            if (++lostScreen >= LOST_FRAMES) {
                screen = null
                lostScreen = 0
            }
            return done(LiveFrameResult(LivePhase.TRACKING, guess, origin, false, emptyList(), times, result?.correlation ?: 0.0))
        }
        lostScreen = 0
        var h = result.screenToFrame
        var correlation = result.correlation
        screen = h

        // 2. The map: where the view is, then (from blocks of it) how to straighten the screen.
        var t = clock.markNow()
        var view = locator.viewImage(frame, h)
        times = times.copy(view = t.elapsedNow().inWholeMicroseconds / 1000.0)
        t = clock.markNow()
        val previous = origin
        val predicted = previous?.let {
            val ahead = sinceFix + 1
            Point(it.x + ahead * velocity.x + MapLocator.VIEW_X0 / S, it.y + ahead * velocity.y + MapLocator.VIEW_Y0 / S)
        }
        val radius = 100.0 + 30.0 * sinceFix
        // The map pans at a steady speed under the stick, so a fix far from both the prediction and
        // the last fix is a false lock. (Near the last fix allows for the pan changing direction.)
        val last = previous?.let { Point(it.x + MapLocator.VIEW_X0 / S, it.y + MapLocator.VIEW_Y0 / S) }
        fun plausible(f: MapLocator.Fix?): Boolean {
            if (f == null || !f.good) return false
            if (predicted == null || last == null) return true
            val fromPrediction = kotlin.math.hypot(f.viewOrigin.x - predicted.x, f.viewOrigin.y - predicted.y)
            val fromLast = kotlin.math.hypot(f.viewOrigin.x - last.x, f.viewOrigin.y - last.y)
            return fromPrediction <= MAX_SURPRISE * (sinceFix + 1) || fromLast <= MAX_PAN * (sinceFix + 1)
        }
        var straightened: Pair<Homography, Point>? = null
        var fix = if (predicted == null) locator.anywhere(view) else locator.near(view, predicted, radius)
        if (plausible(fix)) {
            straightened = straighten(locator.blocks(view, fix!!.viewOrigin), h)
        } else if (predicted != null) {
            // The chrome fit can drift or lock at the wrong size when the phone moves; the map fills
            // the screen, so match its blocks one by one in a wider window and rebuild the screen from them.
            val wide = straighten(locator.blocks(view, predicted, radius = WIDE_BLOCKS), h)
            if (wide != null) {
                val v = locator.viewImage(frame, wide.first)
                val f = locator.near(v, Point(wide.second.x + MapLocator.VIEW_X0 / S, wide.second.y + MapLocator.VIEW_Y0 / S), 30.0)
                if (plausible(f)) {
                    h = wide.first
                    screen = h
                    view = v
                    fix = f
                    straightened = straighten(locator.blocks(view, f.viewOrigin), h) ?: wide
                }
            }
        }
        if (fix == null || !plausible(fix)) {
            times = times.copy(locate = t.elapsedNow().inWholeMicroseconds / 1000.0)
            if (previous == null) {
                return done(LiveFrameResult(LivePhase.NOT_MAX_ZOOM, h, null, false, emptyList(), times, correlation, fix?.peak ?: 0.0, fix?.ratio ?: 0.0))
            }
            if (++sinceFix > LOST_MAP_FRAMES) {
                origin = null
                velocity = Point(0.0, 0.0)
                sinceFix = 0
            }
            return done(LiveFrameResult(LivePhase.TRACKING, h, previous, false, emptyList(), times, correlation, fix?.peak ?: 0.0, fix?.ratio ?: 0.0))
        }
        val located = checkNotNull(fix)
        var o = Point(located.viewOrigin.x - MapLocator.VIEW_X0 / S, located.viewOrigin.y - MapLocator.VIEW_Y0 / S)
        if (straightened != null) {
            h = straightened.first
            o = straightened.second
            screen = h
        }
        times = times.copy(locate = t.elapsedNow().inWholeMicroseconds / 1000.0)
        if (previous != null) {
            val steps = sinceFix + 1
            velocity = Point(0.6 * (o.x - previous.x) / steps + 0.4 * velocity.x, 0.6 * (o.y - previous.y) / steps + 0.4 * velocity.y)
        }
        sinceFix = 0
        origin = o

        // 3. The Koroks in view, counted when the screen's shape was checked against the map, or the
        //    frame's fit was strong anyway (a featureless stretch of map gives too few blocks).
        t = clock.markNow()
        val readings = markers.read(frame, h, o)
        val trusted = straightened != null || correlation >= STRONG_CHROME
        if (trusted) for (r in readings) evidence.add(r.id, r.score)
        times = times.copy(markers = t.elapsedNow().inWholeMicroseconds / 1000.0)
        return done(LiveFrameResult(LivePhase.TRACKING, h, o, trusted, readings, times, correlation, located.peak, located.ratio))
    }

    /**
     * A corrected screen homography and map origin from block [matches]: a robust canonical → world
     * fit M, so world → frame is h ∘ M⁻¹, and the screen is that after the max-zoom map's own
     * canonical → world. Null with too few matches or an implausible fit.
     */
    private fun straighten(matches: List<MapLocator.Correspondence>, h: Homography): Pair<Homography, Point>? {
        if (matches.size < MIN_BLOCKS) return null
        val (m, inliers) = Homography.ransac(matches.map { it.canonical }, matches.map { it.world }, threshold = 3.0) ?: return null
        if (inliers.size < MIN_BLOCKS) return null
        val centre = Point(ScreenChrome.WIDTH / 2.0, ScreenChrome.HEIGHT / 2.0)
        if (kotlin.math.abs(m.localScale(centre) * S - 1) > 0.1) return null
        val c = m.map(centre)
        val o = Point(c.x - centre.x / S, c.y - centre.y / S)
        val corrected = h * m.inverse() * Homography.scaleTranslate(1 / S, o.x, o.y)
        return corrected to o
    }

    private fun done(r: LiveFrameResult): LiveFrameResult {
        stats.add(r)
        return r
    }

    companion object {
        /** The frame likeness that counts as having found the screen (see [ScreenChrome.locate]). */
        const val MIN_FRAME_SCORE = 0.6

        /** Chrome correlation below which the screen counts as lost. */
        const val KEEP_CHROME = 0.3

        /** Chrome correlation that's trusted without the map's check. */
        const val STRONG_CHROME = 0.6

        /** Map blocks that must agree to straighten the screen. */
        const val MIN_BLOCKS = 6

        /** World units a fix may land from the predicted pan, per frame since the last fix. */
        const val MAX_SURPRISE = 40.0

        /** World units the map may pan per frame, whatever the direction (the stick pans it about 230 a second). */
        const val MAX_PAN = 55.0

        /** Frames without a map fix before searching the whole map again. */
        const val LOST_MAP_FRAMES = 15

        /** Search radius (level-2 pixels, two world units each) for rebuilding the screen from map blocks. */
        const val WIDE_BLOCKS = 14

        private const val S = MapLocator.MAX_ZOOM_SCALE

        /** Lost frames in a row before starting over. */
        const val LOST_FRAMES = 3
    }
}

/** Frames per second and mean stage times over the last [window] frames. */
class LiveStats(private val window: Int = 30) {
    private val recent = ArrayDeque<Pair<Long, StageTimes>>()
    private val start = TimeSource.Monotonic.markNow()
    var frames = 0
        private set
    var trackedFrames = 0
        private set

    fun add(r: LiveFrameResult) {
        frames++
        if (r.tracked) trackedFrames++
        recent.addLast(start.elapsedNow().inWholeMilliseconds to r.times)
        while (recent.size > window) recent.removeFirst()
    }

    /** Frames analysed per second over the window. */
    val fps: Double
        get() {
            if (recent.size < 2) return 0.0
            val span = recent.last().first - recent.first().first
            return if (span > 0) (recent.size - 1) * 1000.0 / span else 0.0
        }

    val meanTimes: StageTimes
        get() = if (recent.isEmpty()) StageTimes() else recent.fold(StageTimes()) { a, p -> a + p.second } / recent.size

    fun summary(): String {
        val t = meanTimes
        fun f(v: Double) = ((v * 10).toLong() / 10.0).toString()
        return "fps ${f(fps)} | ms total ${f(t.total)} acquire ${f(t.acquire)} track ${f(t.track)} view ${f(t.view)} " +
            "locate ${f(t.locate)} markers ${f(t.markers)} | tracked $trackedFrames/$frames"
    }
}
