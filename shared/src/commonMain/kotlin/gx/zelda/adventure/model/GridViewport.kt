package gx.zelda.adventure.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the map sits on screen. The map is [cols]×[rows] squares of [squareWidth]×[squareHeight]
 * map pixels; [scale] is screen pixels per map pixel, and ([offsetX], [offsetY]) is where the map's
 * top-left corner is on screen. Every change goes through [clamped], so the map never zooms out
 * past fitting the view or drifts off it.
 *
 * [insetRight] is how much of the view's right side a panel covers. The map keeps its size under
 * it, but is kept on (and can be panned into) the part left showing, [visibleWidth].
 */
data class GridViewport(
    val viewWidth: Float,
    val viewHeight: Float,
    val cols: Int,
    val rows: Int,
    val squareWidth: Float,
    val squareHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val insetRight: Float = 0f,
) {
    val mapWidth: Float get() = cols * squareWidth
    val mapHeight: Float get() = rows * squareHeight

    /** The scale that shows the whole map. */
    val fitScale: Float get() = min(viewWidth / mapWidth, viewHeight / mapHeight)
    val minScale: Float get() = fitScale
    val maxScale: Float get() = max(fitScale * MAX_ZOOM, MIN_MAX_SCALE)

    /** How far in from fitting, 1 at fit. */
    val zoom: Float get() = scale / fitScale

    /** The width of the view the panel ([insetRight]) leaves showing. */
    val visibleWidth: Float get() = viewWidth - insetRight

    /** Zoomed in by [factor] around the screen point ([focusX], [focusY]), which stays where it is. */
    fun zoomBy(factor: Float, focusX: Float = visibleWidth / 2, focusY: Float = viewHeight / 2): GridViewport {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val k = newScale / scale
        return copy(scale = newScale, offsetX = focusX - (focusX - offsetX) * k, offsetY = focusY - (focusY - offsetY) * k).clamped()
    }

    fun panBy(dx: Float, dy: Float): GridViewport = copy(offsetX = offsetX + dx, offsetY = offsetY + dy).clamped()

    /**
     * The scale kept in range, and the map centred along any side it doesn't fill, else kept
     * covering the view. Across, that's the part [visibleWidth] leaves.
     */
    fun clamped(): GridViewport {
        val s = scale.coerceIn(minScale, maxScale)
        val w = mapWidth * s
        val h = mapHeight * s
        val vw = visibleWidth
        val x = if (w <= vw) (vw - w) / 2 else offsetX.coerceIn(vw - w, 0f)
        val y = if (h <= viewHeight) (viewHeight - h) / 2 else offsetY.coerceIn(viewHeight - h, 0f)
        return copy(scale = s, offsetX = x, offsetY = y)
    }

    /** The same part of the map in a view of a new size (a rotation, say), at the same zoom. */
    fun resized(width: Float, height: Float): GridViewport {
        val (cx, cy) = toMap(visibleWidth / 2, viewHeight / 2)
        val z = zoom
        val next = copy(viewWidth = width, viewHeight = height, insetRight = insetRight.coerceAtMost(width))
        val s = next.fitScale * z
        return next.copy(scale = s, offsetX = next.visibleWidth / 2 - cx * s, offsetY = height / 2 - cy * s).clamped()
    }

    /**
     * The viewport for a view that's now [width]×[height]: the same view at the same zoom
     * ([resized]), unless the view turned between portrait and landscape, where it opens afresh for
     * the new shape ([initial], around [focus]) since the old zoom would make little sense there.
     */
    fun forNewSize(width: Float, height: Float, focus: Pair<Int, Int>? = null): GridViewport =
        if ((viewWidth > viewHeight) != (width > height)) initial(width, height, cols, rows, squareWidth, squareHeight, focus) else resized(width, height)

    /** Fit, centred. */
    fun fitted(): GridViewport = copy(scale = fitScale).clamped()

    /** With [px] of the right side covered by a panel (0 when it closes), at the same scale. */
    fun withInset(px: Float): GridViewport = if (px == insetRight) this else copy(insetRight = px.coerceIn(0f, viewWidth)).clamped()

    /**
     * Panned as little as it takes to show the square at ([row], [col]) whole, at least [margin]
     * in from the edges of the part of the view the panel leaves; centred there if it's too big to.
     */
    fun reveal(row: Int, col: Int, margin: Float = 0f): GridViewport {
        val (x0, y0) = toScreen((col - 1) * squareWidth, (row - 1) * squareHeight)
        fun shift(start: Float, size: Float, view: Float): Float = when {
            size > view - 2 * margin -> view / 2 - (start + size / 2)
            start < margin -> margin - start
            start + size > view - margin -> view - margin - (start + size)
            else -> 0f
        }
        val dx = shift(x0, squareWidth * scale, visibleWidth)
        val dy = shift(y0, squareHeight * scale, viewHeight)
        return if (dx == 0f && dy == 0f) this else panBy(dx, dy)
    }

    fun toMap(x: Float, y: Float): Pair<Float, Float> = (x - offsetX) / scale to (y - offsetY) / scale

    fun toScreen(mapX: Float, mapY: Float): Pair<Float, Float> = offsetX + mapX * scale to offsetY + mapY * scale

    /** The square under a screen point, as (row, col) from 1; null off the map. */
    fun squareAt(x: Float, y: Float): Pair<Int, Int>? {
        val (mx, my) = toMap(x, y)
        if (mx < 0 || my < 0 || mx >= mapWidth || my >= mapHeight) return null
        return floor(my / squareHeight).toInt() + 1 to floor(mx / squareWidth).toInt() + 1
    }

    /**
     * The search-screen cell under a point given in one square's own pixels (0 until [squareWidth]
     * and [squareHeight]), as (row 0–10, col 0–15); null outside the square.
     */
    fun cellAt(squareX: Float, squareY: Float): Pair<Int, Int>? = cellAt(squareX, squareY, squareWidth, squareHeight)

    /**
     * Screen x of the left edge of column [col] (from 1; `cols + 1` is the right edge), rounded to a
     * whole pixel. Neighbouring squares share these edges, so with pixel-sharp drawing they meet with
     * no seam or overlap.
     */
    fun edgeX(col: Int): Int = (offsetX + (col - 1) * squareWidth * scale).roundToInt()

    /** Screen y of the top edge of row [row] (from 1; `rows + 1` is the bottom), rounded the same way. */
    fun edgeY(row: Int): Int = (offsetY + (row - 1) * squareHeight * scale).roundToInt()

    /** The columns and rows at least partly on screen, from 1. */
    val visibleCols: IntRange get() = visible(offsetX, squareWidth * scale, viewWidth, cols)
    val visibleRows: IntRange get() = visible(offsetY, squareHeight * scale, viewHeight, rows)

    companion object {
        /** How far in the map zooms from fitting the view. */
        const val MAX_ZOOM = 8f

        /** At least this many screen pixels per map pixel at full zoom, however small the fit. */
        const val MIN_MAX_SCALE = 2f

        /**
         * How the map first opens: fitted, unless that leaves most of a tall view empty (a phone held
         * upright, with a map three times wider than tall); then zoomed to fill the view's height,
         * centred on the square at [focus] (row, col) if given.
         */
        fun initial(width: Float, height: Float, cols: Int, rows: Int, squareWidth: Float, squareHeight: Float, focus: Pair<Int, Int>? = null): GridViewport {
            val fit = fit(width, height, cols, rows, squareWidth, squareHeight)
            if (fit.mapHeight * fit.scale >= height * 0.6f) return fit
            val zoomed = fit.zoomBy(height / (fit.mapHeight * fit.scale))
            val (row, col) = focus ?: return zoomed
            val (cx, cy) = zoomed.toScreen((col - 0.5f) * squareWidth, (row - 0.5f) * squareHeight)
            return zoomed.panBy(width / 2 - cx, height / 2 - cy)
        }

        /** The map fitted into a view of [width]×[height]. */
        fun fit(width: Float, height: Float, cols: Int, rows: Int, squareWidth: Float, squareHeight: Float): GridViewport =
            GridViewport(width, height, cols, rows, squareWidth, squareHeight, 1f, 0f, 0f).fitted()

        /** The search-screen cell under ([x], [y]) in a square [width]×[height] big, or null outside it. */
        fun cellAt(x: Float, y: Float, width: Float, height: Float): Pair<Int, Int>? {
            if (x < 0 || y < 0 || x >= width || y >= height) return null
            val row = floor(y / (height / AdventureMap.SEARCH_ROWS)).toInt().coerceAtMost(AdventureMap.SEARCH_ROWS - 1)
            val col = floor(x / (width / AdventureMap.SEARCH_COLS)).toInt().coerceAtMost(AdventureMap.SEARCH_COLS - 1)
            return row to col
        }

        private fun visible(offset: Float, size: Float, view: Float, count: Int): IntRange {
            val first = floor(-offset / size).toInt() + 1
            val last = floor((view - offset) / size).toInt() + 1
            return first.coerceAtLeast(1)..last.coerceAtMost(count)
        }
    }
}
