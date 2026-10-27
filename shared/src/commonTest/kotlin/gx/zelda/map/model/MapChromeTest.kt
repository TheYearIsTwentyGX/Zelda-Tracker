package gx.zelda.map.model

import gx.zelda.adventure.model.AdventureChrome
import gx.zelda.adventure.model.AdventureTab
import gx.zelda.adventure.model.SquareContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the map screen hides its bars and where a marker opens: as on the HW:DE Adventure Maps' Map tab. */
class MapChromeTest {
    /** A Razr on its side is about 1005×411 dp; upright, 411×1005. */
    @Test
    fun aShortLandscapeWindowHidesTheBars() {
        assertFalse(MapChrome.barsVisible(1005f, 411f), "a phone on its side")
        assertTrue(MapChrome.barsVisible(411f, 1005f), "upright keeps them")
        assertTrue(MapChrome.barsVisible(1280f, 800f), "a tablet on its side has room for them")
        assertFalse(MapChrome.barsVisible(600f, 400f), "a short split-screen window")
        assertTrue(MapChrome.barsVisible(400f, 400f), "square isn't landscape")
    }

    @Test
    fun aShortLandscapeWindowOpensMarkersInASidePanel() {
        assertEquals(MarkerContainer.PANEL, MapChrome.markerContainer(1005f, 411f), "a phone on its side")
        assertEquals(MarkerContainer.SHEET, MapChrome.markerContainer(411f, 1005f), "upright")
        assertEquals(MarkerContainer.SHEET, MapChrome.markerContainer(1280f, 800f), "a tablet on its side has room for a sheet")
        assertEquals(MarkerContainer.PANEL, MapChrome.markerContainer(600f, 400f), "a short split-screen window")
        // About 42% of the width, within bounds.
        assertEquals(422f, MapChrome.panelWidthDp(1005f), 1f)
        assertEquals(AdventureChrome.PANEL_MIN_DP, MapChrome.panelWidthDp(600f))
        assertEquals(AdventureChrome.PANEL_MAX_DP, MapChrome.panelWidthDp(1400f))
    }

    @Test
    fun theWorldMapAndTheAdventureMapsAgree() {
        for (w in listOf(320f, 411f, 600f, 915f, 1005f, 1280f, 1600f)) {
            for (h in listOf(360f, 400f, 411f, 479f, 480f, 800f, 1005f)) {
                assertEquals(AdventureChrome.visible(w, h, AdventureTab.MAP), MapChrome.barsVisible(w, h), "$w×$h")
                val panel = AdventureChrome.squareContainer(w, h) == SquareContainer.PANEL
                assertEquals(panel, MapChrome.markerContainer(w, h) == MarkerContainer.PANEL, "$w×$h")
            }
        }
    }

    /** The panel covers the right 1000 px of a 2400 px map, so 1400 px are showing. */
    @Test
    fun aMarkerUnderThePanelOrAtTheEdgeIsPannedIntoView() {
        assertFalse(MapChrome.needsReveal(700f, visibleWidth = 1400f, margin = 100f), "in the open")
        assertTrue(MapChrome.needsReveal(1800f, visibleWidth = 1400f, margin = 100f), "under the panel")
        assertTrue(MapChrome.needsReveal(1350f, visibleWidth = 1400f, margin = 100f), "against the panel's edge")
        assertTrue(MapChrome.needsReveal(-40f, visibleWidth = 1400f, margin = 100f), "off the left edge")
    }
}
