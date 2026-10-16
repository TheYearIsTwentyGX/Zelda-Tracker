package gx.zelda.screencam.live

import gx.zelda.core.Games
import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import gx.zelda.screencam.ScreenChrome
import gx.zelda.shrines.scan.decodeMapImage
import kotlinx.coroutines.runBlocking
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveTrackingTest {
    private val screenshot by lazy {
        GrayImage.fromMapImage(decodeMapImage(javaClass.getResourceAsStream("/map-samples/all-complete/1000002225.jpg")!!.readBytes())!!)
    }

    /** A 960×540 "camera frame" of [screen] seen through [screenToFrame], dark around it. */
    private fun shoot(screen: GrayImage, screenToFrame: Homography): GrayImage {
        val inv = screenToFrame.inverse()
        return GrayImage(960, 540, FloatArray(960 * 540) { i ->
            val p = inv.map(i % 960 + 0.5, i / 960 + 0.5)
            screen.sample(p.x - 0.5, p.y - 0.5).let { if (it.isNaN()) 12f else it }
        })
    }

    private fun corners(h: Homography) = ScreenChrome.CORNERS.map(h::map)

    @Test
    fun trackerFindsTheScreenFromANearbyGuess() {
        val truth = Homography.fit(ScreenChrome.CORNERS, listOf(Point(120.0, 40.0), Point(860.0, 62.0), Point(842.0, 500.0), Point(104.0, 478.0)))!!
        val frame = ChromeTracker.Frame(shoot(screenshot, truth))
        val tracker = ChromeTracker(runBlocking { ChromeTracker.loadReference(Games.BOTW) })
        // The phone moved a little and came 4% closer since the last frame.
        val guess = Homography.scaleTranslate(1.04, -25.0, 4.0) * truth
        val r = assertNotNull(tracker.track(frame, guess))
        assertTrue(r.correlation > 0.8, "correlation ${r.correlation}")
        for ((a, b) in corners(truth).zip(corners(r.screenToFrame))) {
            assertTrue(hypot(a.x - b.x, a.y - b.y) < 2.0, "corner $a tracked to $b")
        }
    }

    @Test
    fun locatorFindsAViewOnTheTiles() = runBlocking {
        val mosaic = TileMosaic(BundledTiles)
        // Tanagar Canyon: a view cut straight from the level-2 tiles at a known place.
        val origin = Point(-4200.0, -300.0)
        val view = mosaic.region(2, MapLocator.pixelX(origin.x, 2), MapLocator.pixelZ(origin.y, 2), MapLocator.VIEW_W, MapLocator.VIEW_H)
        val locator = MapLocator(mosaic)
        val fix = locator.near(view, Point(origin.x + 46, origin.y - 30))
        assertTrue(fix.good, "peak ${fix.peak} ratio ${fix.ratio}")
        assertEquals(origin.x, fix.viewOrigin.x, 1.5)
        assertEquals(origin.y, fix.viewOrigin.y, 1.5)
        // Every block agrees with the whole.
        val blocks = locator.blocks(view, fix.viewOrigin)
        assertTrue(blocks.size >= LiveKorokScanner.MIN_BLOCKS, "${blocks.size} blocks")
        for (b in blocks) {
            val expected = Point(origin.x + (b.canonical.x - MapLocator.VIEW_X0) / MapLocator.MAX_ZOOM_SCALE, origin.y + (b.canonical.y - MapLocator.VIEW_Y0) / MapLocator.MAX_ZOOM_SCALE)
            assertTrue(hypot(expected.x - b.world.x, expected.y - b.world.y) < 2.5, "block at ${b.canonical}: ${b.world} vs $expected")
        }
    }

    @Test
    fun evidenceNeedsSeveralAgreeingFrames() {
        val e = KorokEvidence(stacked = setOf("X05"))
        repeat(3) { e.add("A", 0.8) }
        e.add("A", 0.5)
        repeat(2) { e.add("B", 0.8) }
        repeat(5) { e.add("C", 0.2) }
        repeat(4) { e.add("D", 0.2) }
        e.add("D", 0.7)
        repeat(5) { e.add("X05", 0.9) }
        assertEquals(KorokVerdict.FOUND, e.verdict("A"))
        assertEquals(KorokVerdict.UNSURE, e.verdict("B"), "two frames aren't enough")
        assertEquals(KorokVerdict.EMPTY, e.verdict("C"))
        assertEquals(KorokVerdict.UNSURE, e.verdict("D"), "one marker sighting rules out empty")
        assertEquals(KorokVerdict.UNSURE, e.verdict("X05"), "stacked pairs can't be told apart")
        assertEquals(KorokVerdict.UNSURE, e.verdict("unseen"))
    }

    @Test
    fun stackedKoroksAreTheKnownPairs() {
        val spots = runBlocking { gx.zelda.koroks.data.KorokRepository.load(Games.BOTW) }.koroks.map { KorokSpot(it.id, it.x, it.z) }
        val reader = KorokMarkerReader(GrayImage(28, 18), spots)
        assertTrue(setOf("X05", "X06", "L17", "L18").all { it in reader.stacked }, "${reader.stacked}")
        assertTrue(reader.stacked.size <= 8, "${reader.stacked}")
    }
}
