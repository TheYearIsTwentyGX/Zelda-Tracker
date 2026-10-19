package gx.zelda.adventure.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The map's pan and zoom maths and its hit-testing, on the Adventure Map's 16×8 squares of 256×176. */
class GridViewportTest {
    private val portrait = GridViewport.fit(1080f, 1800f, 16, 8, 256f, 176f)
    /** Wide enough that the map's height limits it, with bands either side. */
    private val landscape = GridViewport.fit(3000f, 800f, 16, 8, 256f, 176f)

    private fun close(a: Float, b: Float, eps: Float = 0.01f) = assertTrue(abs(a - b) <= eps, "$a vs $b")

    @Test
    fun fitShowsTheWholeMapCentred() {
        close(1080f / 4096f, portrait.scale)
        close(0f, portrait.offsetX)
        close((1800f - 1408f * portrait.scale) / 2, portrait.offsetY)
        close(800f / 1408f, landscape.scale, 0.0001f)
        close((3000f - 4096f * landscape.scale) / 2, landscape.offsetX)
        close(0f, landscape.offsetY)
        close(1f, portrait.zoom)
    }

    @Test
    fun zoomStaysInRangeAndKeepsTheFocusStill() {
        assertEquals(portrait.minScale, portrait.zoomBy(0.01f).scale, "never smaller than fitting")
        assertEquals(portrait.maxScale, portrait.zoomBy(1000f).scale)
        close(portrait.fitScale * GridViewport.MAX_ZOOM, portrait.maxScale)
        assertEquals(GridViewport.MIN_MAX_SCALE, GridViewport.fit(400f, 800f, 16, 8, 256f, 176f).maxScale, "a small fit still zooms to 2 px per pixel")
        val focus = 540f to 900f
        val before = portrait.toMap(focus.first, focus.second)
        val zoomed = portrait.zoomBy(3f, focus.first, focus.second)
        val after = zoomed.toMap(focus.first, focus.second)
        close(before.first, after.first, 0.5f)
        close(before.second, after.second, 0.5f)
    }

    @Test
    fun panningNeverLeavesTheMapOffScreen() {
        val zoomed = portrait.zoomBy(8f) // taller than the view now, as well as wider
        val far = zoomed.panBy(1e6f, 1e6f)
        close(0f, far.offsetX)
        close(0f, far.offsetY)
        val other = zoomed.panBy(-1e6f, -1e6f)
        close(1080f - 4096f * other.scale, other.offsetX)
        close(1800f - 1408f * other.scale, other.offsetY)
        // At fit, the map can't be dragged sideways at all.
        assertEquals(portrait, portrait.panBy(300f, 0f).copy(offsetY = portrait.offsetY))
    }

    @Test
    fun theRoundTripReturnsThePoint() {
        val v = landscape.zoomBy(2.5f, 700f, 300f).panBy(-120f, 40f)
        for ((x, y) in listOf(0f to 0f, 123.4f to 567.8f, 2999f to 799f)) {
            val (mx, my) = v.toMap(x, y)
            val (sx, sy) = v.toScreen(mx, my)
            close(x, sx)
            close(y, sy)
        }
    }

    @Test
    fun everySquareIsHitAtItsCornersAndCentre() {
        for (v in listOf(portrait, landscape, portrait.zoomBy(5f, 333f, 777f))) {
            val w = v.squareWidth * v.scale
            val h = v.squareHeight * v.scale
            for (row in 1..8) for (col in 1..16) {
                val left = v.offsetX + (col - 1) * w
                val top = v.offsetY + (row - 1) * h
                for ((x, y) in listOf(left + 0.01f to top + 0.01f, left + w / 2 to top + h / 2, left + w - 0.01f to top + h - 0.01f)) {
                    if (x < 0 || y < 0 || x >= v.viewWidth || y >= v.viewHeight) continue
                    assertEquals(row to col, v.squareAt(x, y), "($x, $y) at ${v.scale}")
                }
            }
        }
    }

    @Test
    fun offTheMapIsNoSquare() {
        assertNull(portrait.squareAt(10f, 10f), "the band above a fitted map")
        assertNull(portrait.squareAt(540f, 1790f), "and below it")
        assertNull(landscape.squareAt(5f, 500f), "the band beside it")
    }

    @Test
    fun everySearchCellIsHit() {
        for (row in 0 until 11) for (col in 0 until 16) {
            assertEquals(row to col, GridViewport.cellAt(col * 16f + 8f, row * 16f + 8f, 256f, 176f))
            assertEquals(row to col, GridViewport.cellAt(col * 16f, row * 16f, 256f, 176f), "a cell's top-left pixel is its own")
        }
        assertEquals(10 to 15, GridViewport.cellAt(255.9f, 175.9f, 256f, 176f))
        assertNull(GridViewport.cellAt(256f, 0f, 256f, 176f))
        assertNull(GridViewport.cellAt(-1f, 0f, 256f, 176f))
        // The same grid over a square drawn at another size.
        assertEquals(5 to 8, GridViewport.cellAt(8.5f * 40f, 5.5f * 40f, 640f, 440f))
    }

    @Test
    fun squaresShareWholePixelEdges() {
        for (v in listOf(portrait, landscape.zoomBy(1.37f, 913f, 411f))) {
            val w = v.squareWidth * v.scale
            for (col in 1..16) {
                val width = v.edgeX(col + 1) - v.edgeX(col)
                assertTrue(width >= floor(w).toInt() && width <= ceil(w).toInt(), "column $col is $width wide for $w")
            }
            val h = v.squareHeight * v.scale
            for (row in 1..8) {
                val height = v.edgeY(row + 1) - v.edgeY(row)
                assertTrue(height >= floor(h).toInt() && height <= ceil(h).toInt(), "row $row is $height tall for $h")
            }
        }
    }

    @Test
    fun aResizeKeepsTheZoomAndTheMiddle() {
        val zoomed = portrait.zoomBy(3f, 540f, 900f).panBy(-200f, 0f)
        val middle = zoomed.toMap(540f, 900f)
        val turned = zoomed.resized(1800f, 1080f)
        close(zoomed.zoom, turned.zoom, 0.001f)
        val after = turned.toMap(900f, 540f)
        close(middle.first, after.first, 1f)
        close(middle.second, after.second, 1f)
    }

    /** Upright, the map opens filling the view's height around the start square; wide, it opens fitted. */
    @Test
    fun theMapOpensAroundTheStartWhenUpright() {
        val upright = GridViewport.initial(1080f, 1800f, 16, 8, 256f, 176f, focus = 8 to 8)
        close(1800f, upright.mapHeight * upright.scale, 1f)
        assertEquals(8, upright.squareAt(540f, 900f)?.second, "the start square's column is in the middle")
        assertEquals(8 to 8, upright.squareAt(540f, 1700f), "and its row on screen: the map can't scroll further down")
        assertEquals(landscape, GridViewport.initial(3000f, 800f, 16, 8, 256f, 176f, focus = 8 to 8))
        assertEquals(portrait.zoomBy(1800f / (1408f * portrait.scale)), GridViewport.initial(1080f, 1800f, 16, 8, 256f, 176f))
    }

    @Test
    fun visibleRangesCoverWhatsOnScreen() {
        assertEquals(1..16, portrait.visibleCols)
        assertEquals(1..8, portrait.visibleRows)
        val corner = portrait.zoomBy(8f, 0f, portrait.offsetY)
        assertEquals(1, corner.visibleCols.first)
        assertTrue(corner.visibleCols.last < 16)
        assertTrue(corner.visibleRows.last < 8)
    }
}
