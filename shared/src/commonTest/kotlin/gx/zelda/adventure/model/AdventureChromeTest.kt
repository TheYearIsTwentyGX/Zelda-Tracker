package gx.zelda.adventure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the map screen hides its bars, and how the map refits when the window turns. */
class AdventureChromeTest {
    @Test
    fun onlyAShortLandscapeMapTabHidesTheBars() {
        assertFalse(AdventureChrome.visible(915f, 412f, AdventureTab.MAP), "a phone on its side, on the map")
        assertTrue(AdventureChrome.visible(915f, 412f, AdventureTab.LIST), "the list keeps them")
        assertTrue(AdventureChrome.visible(412f, 915f, AdventureTab.MAP), "upright keeps them")
        assertTrue(AdventureChrome.visible(1280f, 800f, AdventureTab.MAP), "a tablet on its side has room for them")
        assertFalse(AdventureChrome.visible(600f, 400f, AdventureTab.MAP), "a short split-screen window")
        assertTrue(AdventureChrome.visible(400f, 400f, AdventureTab.MAP), "square isn't landscape")
    }

    @Test
    fun turningTheWindowOpensTheMapAfreshForItsShape() {
        val portrait = GridViewport.initial(1080f, 2000f, 16, 8, 256f, 176f, focus = 8 to 8).zoomBy(1.5f)
        val turned = portrait.forNewSize(2400f, 1080f, focus = 8 to 8)
        assertEquals(GridViewport.initial(2400f, 1080f, 16, 8, 256f, 176f, focus = 8 to 8), turned)
        // The hidden bars' height goes to the map: a taller landscape view fits a bigger map.
        val tall = GridViewport.initial(2400f, 1080f, 16, 8, 256f, 176f)
        val short = GridViewport.initial(2400f, 700f, 16, 8, 256f, 176f)
        assertTrue(tall.scale > short.scale)
        // A resize that keeps the shape (the bars hiding, say) keeps the zoom.
        val zoomed = tall.zoomBy(2f)
        assertEquals(zoomed.zoom, zoomed.forNewSize(2400f, 1000f).zoom, 0.001f)
    }
}
