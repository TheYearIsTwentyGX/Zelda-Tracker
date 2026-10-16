package gx.zelda.screencam

import gx.zelda.core.Games
import gx.zelda.screencam.live.ChromeTracker
import gx.zelda.screencam.live.GrayImage
import gx.zelda.shrines.scan.MapImage
import gx.zelda.shrines.scan.decodeMapImage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `files/botw/map_chrome.json` is the map frame of the golden screenshots, as [ScreenChrome.of]
 * computes it: the three normal shots, and the Hero's Path one for its own histograms. Run with
 * `REGENERATE_SCREEN_CHROME=1` to rewrite the file after changing how it's computed.
 */
class ScreenChromeReferenceTest {
    private fun sample(name: String): MapImage =
        decodeMapImage(javaClass.getResourceAsStream("/map-samples/$name.jpg")?.readBytes() ?: fail("missing $name")) ?: fail("can't decode $name")

    private val built by lazy {
        ScreenChrome.of(
            listOf("all-complete/1000002225", "all-complete/1000002224", "partial/1000002226").map(::sample),
            listOf(sample("partial/1000002228")),
        )
    }

    @Test
    fun bundledReferenceIsUpToDate() {
        val text = ScreenChromeRepository.encode(built)
        val file = File("src/commonMain/composeResources/${ScreenChromeRepository.path(Games.BOTW)}")
        if (System.getenv("REGENERATE_SCREEN_CHROME") == "1") file.writeText(text)
        assertEquals(text, file.readText(), "map_chrome.json is out of date: rerun this test with REGENERATE_SCREEN_CHROME=1")
    }

    /** The live scan's tracking reference, `map_chrome_track.bin`, from the three normal shots. */
    @Test
    fun trackingReferenceIsUpToDate() {
        val shots = listOf("all-complete/1000002225", "all-complete/1000002224", "partial/1000002226").map { GrayImage.fromMapImage(sample(it)) }
        val bytes = ChromeTracker.encodeReference(ChromeTracker.reference(shots))
        val file = File("src/commonMain/composeResources/${ChromeTracker.referencePath(Games.BOTW)}")
        if (System.getenv("REGENERATE_SCREEN_CHROME") == "1") file.writeBytes(bytes)
        assertTrue(bytes.contentEquals(file.readBytes()), "map_chrome_track.bin is out of date: rerun this test with REGENERATE_SCREEN_CHROME=1")
    }

    @Test
    fun loadsWhatWasBuilt() {
        val loaded = runBlocking { ScreenChromeRepository.load(Games.BOTW) }
        assertEquals(built.thumb.map { if (it.isNaN()) -1f else it }, loaded.thumb.map { if (it.isNaN()) -1f else it })
        assertEquals(built.histograms.map { it.toList() }, loaded.histograms.map { it.toList() })
        assertEquals(built.heroesPathHistograms.map { it.toList() }, loaded.heroesPathHistograms.map { it.toList() })
    }

    @Test
    fun everyScreenshotLooksLikeTheFrameAndAMapCropDoesNot() {
        for (name in listOf("all-complete/1000002225", "all-complete/1000002224", "partial/1000002226", "partial/1000002228")) {
            val score = built.frameScore(sample(name))
            assertTrue(score > 0.85, "$name: frame score $score")
        }
        // The map itself, stretched to fill the screen: no frame.
        val shot = sample("all-complete/1000002225")
        val px = IntArray(1920 * 1080) { i -> shot.argb[(300 + i / 1920 * 600 / 1080) * 1920 + 400 + i % 1920 * 1100 / 1920] }
        val score = built.frameScore(MapImage(1920, 1080, px))
        assertTrue(score < 0.4, "map crop: frame score $score")
    }
}
