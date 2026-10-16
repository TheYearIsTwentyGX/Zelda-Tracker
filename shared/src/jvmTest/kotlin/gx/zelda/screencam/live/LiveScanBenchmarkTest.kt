package gx.zelda.screencam.live

import gx.zelda.core.Games
import gx.zelda.screencam.FakeCamera
import gx.zelda.screencam.ScreenChromeRepository
import gx.zelda.shrines.scan.decodeMapImage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveScanBenchmarkTest {
    private fun clipFrame(i: Int): GrayImage {
        val dir = File(javaClass.getResource("/korok-clips/handheld-hebra-ridgeland/frames")!!.toURI())
        return decodeGrayImage(File(dir, "%04d.jpg".format(i)).readBytes())!!
    }

    private fun resize(img: GrayImage, w: Int): GrayImage {
        val h = img.height * w / img.width
        val s = img.width.toDouble() / w
        return GrayImage(w, h, FloatArray(w * h) { i -> img.sample((i % w + 0.5) * s - 0.5, (i / w + 0.5) * s - 0.5).let { if (it.isNaN()) 0f else it } })
    }

    @Test
    fun acquisition() {
        val chrome = runBlocking { ScreenChromeRepository.load(Games.BOTW) }
        val tracker = ChromeTracker(runBlocking { ChromeTracker.loadReference(Games.BOTW) })
        val acquirer = ScreenAcquirer(chrome, tracker)
        val shot = decodeMapImage(javaClass.getResourceAsStream("/map-samples/all-complete/1000002225.jpg")!!.readBytes())!!
        val tv = GrayImage.fromMapImage(FakeCamera.shoot(shot, FakeCamera.Shot(1280, 720, 0.45, yawDeg = 8.0, glare = 40.0, room = 25.0, samples = 4)))
        val frames = listOf(tv) + (0..4).map { resize(clipFrame(it), 1280) }
        repeat(3) { acquirer.acquire(frames[0], ChromeTracker.Frame(frames[0])) }
        val times = ArrayList<Double>()
        for ((k, f) in frames.withIndex()) {
            val t = System.nanoTime()
            val a = acquirer.acquire(f, ChromeTracker.Frame(f))
            val ms = (System.nanoTime() - t) / 1e6
            println("frame $k ${f.width}x${f.height}: acquire $ms ms score ${a.score} fit ${a.correlation} ${a.reason ?: "accepted"}")
            times += ms
        }
        times.sort()
        assertTrue(times[times.size / 2] < 100.0, "median acquisition ${times[times.size / 2]} ms")
    }

    @Test
    fun globalSearch() = runBlocking {
        val chrome = ScreenChromeRepository.load(Games.BOTW)
        val tracker = ChromeTracker(ChromeTracker.loadReference(Games.BOTW))
        val f = clipFrame(2)
        val a = ScreenAcquirer(chrome, tracker).acquire(f, ChromeTracker.Frame(f))
        val h = tracker.track(ChromeTracker.Frame(f), a.screenToFrame!!)!!.screenToFrame
        for (run in 0..2) {
            val loc = MapLocator(TileMosaic(BundledTiles))
            val view = loc.viewImage(f, h)
            val t = System.nanoTime()
            val fix = loc.anywhere(view)
            val first = (System.nanoTime() - t) / 1e6
            val t2 = System.nanoTime()
            loc.anywhere(view)
            println("anywhere run $run: first ${first} ms (tiles cold), again ${(System.nanoTime() - t2) / 1e6} ms; peak ${fix?.peak} at ${fix?.viewOrigin}")
        }
    }
}
