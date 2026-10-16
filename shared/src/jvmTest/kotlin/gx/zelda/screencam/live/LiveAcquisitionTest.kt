package gx.zelda.screencam.live

import gx.zelda.core.Games
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.screencam.FakeCamera
import gx.zelda.screencam.ScreenChromeRepository
import gx.zelda.shrines.scan.MapImage
import gx.zelda.shrines.scan.decodeMapImage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Finding the screen in a camera frame: real clip frames, a TV across the room, and things that aren't screens. */
class LiveAcquisitionTest {
    private val chrome by lazy { runBlocking { ScreenChromeRepository.load(Games.BOTW) } }
    private val tracker by lazy { ChromeTracker(runBlocking { ChromeTracker.loadReference(Games.BOTW) }) }
    private val acquirer by lazy { ScreenAcquirer(chrome, tracker) }
    private val screenshot by lazy { decodeMapImage(javaClass.getResourceAsStream("/map-samples/all-complete/1000002225.jpg")!!.readBytes())!! }

    private fun clipFrame(i: Int): GrayImage {
        val dir = File(javaClass.getResource("/korok-clips/handheld-hebra-ridgeland/frames")!!.toURI())
        return decodeGrayImage(File(dir, "%04d.jpg".format(i)).readBytes())!!
    }

    private fun acquire(f: GrayImage) = acquirer.acquire(f, ChromeTracker.Frame(f))

    /** A docked TV across a dim room: 1280×720 like the phone's analysis frames. */
    private fun tv(share: Double, yaw: Double = 8.0, glare: Double = 40.0) =
        GrayImage.fromMapImage(FakeCamera.shoot(screenshot, FakeCamera.Shot(1280, 720, share, yawDeg = yaw, pitchDeg = 4.0, glare = glare, room = 25.0, samples = 4)))

    @Test
    fun findsTheHandheldScreenFromTheFirstFrame() {
        for (i in 0..3) assertNotNull(acquire(clipFrame(i)).screenToFrame, "clip frame $i: ${acquire(clipFrame(i)).reason}")
    }

    @Test
    fun findsADockedTvAcrossTheRoom() {
        for (share in listOf(0.3, 0.4, 0.5)) {
            val a = acquire(tv(share))
            assertNotNull(a.screenToFrame, "TV at $share of the frame: ${a.reason}")
            assertTrue(a.correlation >= ScreenAcquirer.MIN_CORRELATION)
        }
        // Turned on its side (the phone held upright), too.
        val upright = tv(0.4)
        val turned = GrayImage(upright.height, upright.width, FloatArray(upright.pixels.size) { i -> upright[i / upright.height, upright.height - 1 - i % upright.height] })
        assertNotNull(acquire(turned).screenToFrame, "TV on its side")
    }

    @Test
    fun saysWhenTheScreenIsTooSmall() {
        val a = acquire(tv(0.12, glare = 0.0))
        if (a.screenToFrame == null) assertTrue(a.reason!!.startsWith("Screen too small") || a.reason!!.startsWith("No screen found"), a.reason)
    }

    /** Frames with no screen in them: none may lock, however long the scan looks. */
    @Test
    fun doesntLockOntoThingsThatArentScreens() {
        val random = Random(3)
        val w = 1280
        val h = 720
        val room = GrayImage(w, h, FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            (60 + 40 * kotlin.math.sin(x / 90.0) * kotlin.math.cos(y / 70.0) + random.nextDouble(-6.0, 6.0)).toFloat()
        })
        val window = GrayImage(w, h, FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val inside = x in 380..900 && y in 160..560 && (x - 640) % 130 !in -4..4 // a bright window with glazing bars
            (if (inside) 240.0 else 45.0 + random.nextDouble(-5.0, 5.0)).toFloat()
        })
        val wall = GrayImage(w, h, FloatArray(w * h) { (150 + random.nextDouble(-8.0, 8.0)).toFloat() })
        val tvOff = GrayImage.fromMapImage(FakeCamera.shoot(MapImage(1920, 1080, IntArray(1920 * 1080) { 0xFF101010.toInt() }), FakeCamera.Shot(1280, 720, 0.5, samples = 2)))
        for ((name, frame) in listOf("room" to room, "window" to window, "wall" to wall, "TV off" to tvOff)) {
            val a = acquire(frame)
            assertNull(a.screenToFrame, "$name locked: score ${a.score} fit ${a.correlation} corners ${a.corners} refined ${a.screenToFrame?.let { h -> chrome.frameScore(frame.shrink(4), 0.25, h) }}")
            assertNotNull(a.reason, name)
        }
    }

    /** A real screen that isn't the max-zoom map can't be placed, so the scan goes back to looking for the screen. */
    @Test
    fun dropsBackWhenTheMapCantBePlaced() = runBlocking {
        val spots = KorokRepository.load(Games.BOTW).koroks.map { KorokSpot(it.id, it.x, it.z) }
        val scanner = LiveKorokScanner(chrome, tracker, MapLocator(TileMosaic(BundledTiles)), KorokMarkerReader(GrayImage(28, 18), spots))
        val frame = tv(0.5, glare = 0.0)
        val results = (1..LiveKorokScanner.PLACE_TRIES + 1).map { scanner.process(frame) }
        assertEquals(LivePhase.FINDING_PLACE, results.first().phase, results.first().reason)
        assertTrue(results.first().reason!!.startsWith("Can't place this on the map"), results.first().reason)
        // After the tries run out, the next frame is a fresh search for the screen.
        assertTrue(results.last().times.acquire > 0, "re-acquired: ${results.map { it.phase }}")
    }
}
