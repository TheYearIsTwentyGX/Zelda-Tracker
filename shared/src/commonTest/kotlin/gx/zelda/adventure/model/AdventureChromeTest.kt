package gx.zelda.adventure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** When the map screen hides its bars, where a square opens, and how the map refits and moves for it. */
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

    /** A Razr on its side is about 1005×411 dp; upright, 411×1005. */
    @Test
    fun aShortLandscapeWindowOpensSquaresInASidePanel() {
        assertEquals(SquareContainer.PANEL, AdventureChrome.squareContainer(1005f, 411f), "a phone on its side")
        assertEquals(SquareContainer.SHEET, AdventureChrome.squareContainer(411f, 1005f), "upright")
        assertEquals(SquareContainer.SHEET, AdventureChrome.squareContainer(1280f, 800f), "a tablet on its side has room for a sheet")
        assertEquals(SquareContainer.PANEL, AdventureChrome.squareContainer(600f, 400f), "a short split-screen window")
        assertEquals(SquareContainer.SHEET, AdventureChrome.squareContainer(400f, 400f), "square isn't landscape")
        // The panel takes about 42% of the width, within bounds.
        assertEquals(422f, AdventureChrome.panelWidthDp(1005f), 1f)
        assertEquals(AdventureChrome.PANEL_MIN_DP, AdventureChrome.panelWidthDp(600f))
        assertEquals(AdventureChrome.PANEL_MAX_DP, AdventureChrome.panelWidthDp(1400f))
    }

    @Test
    fun tickBoxesSitTwoToARowInThePanelAndTheSheet() {
        assertEquals(2, AdventureChrome.checkColumns(AdventureChrome.panelWidthDp(1005f) - 32f), "the panel, less its padding")
        assertEquals(2, AdventureChrome.checkColumns(411f - 32f), "the sheet, upright")
        assertEquals(1, AdventureChrome.checkColumns(AdventureChrome.PANEL_MIN_DP - 32f), "the narrowest panel")
    }

    /** A 2400×1080 px landscape view of a 16×8 map, fitted; the panel covers its right 1000 px. */
    @Test
    fun thePanelKeepsTheMapsScaleAndTheOpenSquareInSight() {
        val fit = GridViewport.initial(2400f, 1080f, 16, 8, 256f, 176f)
        val covered = fit.withInset(1000f)
        assertEquals(fit.scale, covered.scale, "the map doesn't shrink when the panel opens")
        assertEquals(1400f, covered.visibleWidth)

        // P-16's square, under the panel, is panned into the part left showing.
        val shown = covered.reveal(4, 16, margin = 30f)
        val (left, _) = shown.toScreen(15 * 256f, 3 * 176f)
        val right = left + 256f * shown.scale
        assertTrue(left >= 0f && right <= shown.visibleWidth + 0.5f, "on screen: $left..$right")
        assertTrue(shown.offsetX < 0f)

        // A square already in sight doesn't move the map.
        assertSame(shown, shown.reveal(4, 10, margin = 30f))

        // Zoomed in until a square is wider than what's showing, it's centred across, and brought
        // fully into sight down.
        val close = GridViewport.initial(2400f, 1080f, 16, 8, 256f, 176f).zoomBy(8f).withInset(1400f).reveal(4, 8)
        val (x0, y0) = close.toScreen(7 * 256f, 3 * 176f)
        assertEquals(close.visibleWidth / 2, x0 + 256f * close.scale / 2, 1f)
        assertTrue(y0 >= 0f && y0 + 176f * close.scale <= close.viewHeight + 0.5f, "down: $y0")

        // Closing the panel: the same scale, and the fitted map back in place.
        val closed = shown.withInset(0f)
        assertEquals(fit.scale, closed.scale)
        assertEquals(fit.offsetX, closed.offsetX)
        // The zoom buttons zoom about the middle of what's showing, and a resize keeps the panel's inset.
        val (cx, cy) = covered.toMap(700f, 540f)
        val zoomed = covered.zoomBy(1.6f)
        val (zx, zy) = zoomed.toScreen(cx, cy)
        assertEquals(700f, zx, 1f)
        assertEquals(540f, zy, 1f)
        assertEquals(1000f, covered.resized(2300f, 1080f).insetRight)
    }
}
