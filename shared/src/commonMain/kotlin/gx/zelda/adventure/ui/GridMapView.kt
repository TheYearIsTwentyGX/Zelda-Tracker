package gx.zelda.adventure.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.GridViewport
import gx.zelda.adventure.model.MarkerKind
import gx.zelda.adventure.model.Side
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.SquareLook
import gx.zelda.adventure.model.SquareStatus
import gx.zelda.core.ui.StarGold
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** Screen pixels per map pixel from which the full-size squares are drawn over the preview. */
private const val DETAIL_SCALE = 0.45f

/**
 * The map: every square's picture with its state drawn over it, pinch to zoom, drag to pan, tap a
 * square to open it. [viewport] lives in the view model (null until the view has a size); every
 * gesture hands a new one to [onViewport].
 */
@Composable
fun GridMapView(
    state: AdventureUiState,
    images: SquareImages,
    viewport: GridViewport?,
    selected: String?,
    onViewport: (GridViewport) -> Unit,
    onTapSquare: (Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    val map = state.map
    // The gesture handlers are set up once; they read the latest of these.
    val current by rememberUpdatedState(viewport)
    val latestState by rememberUpdatedState(state)
    val setViewport by rememberUpdatedState(onViewport)
    val tap by rememberUpdatedState(onTapSquare)
    val text = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme) {
        MapPalette(
            background = scheme.surfaceContainerLowest,
            scrim = scheme.scrim,
            label = scheme.inverseSurface.copy(alpha = 0.8f),
            onLabel = scheme.inverseOnSurface,
            selection = scheme.primary,
            onSelection = scheme.onPrimary,
        )
    }

    LaunchedEffect(images) { images.loadPreview() }
    LaunchedEffect(images) {
        snapshotFlow { current?.takeIf { it.scale >= DETAIL_SCALE }?.let { visibleSquares(map, it) }.orEmpty() }
            .distinctUntilChanged()
            .collectLatest { images.load(it) }
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size ->
                if (size.width <= 0 || size.height <= 0) return@onSizeChanged
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val v = current
                setViewport(
                    when {
                        v == null -> GridViewport.initial(
                            w, h, map.cols, map.rows, map.art.squareWidth.toFloat(), map.art.squareHeight.toFloat(),
                            focus = map.start?.let(map::square)?.let { it.row to it.col },
                        )
                        v.viewWidth != w || v.viewHeight != h -> v.resized(w, h)
                        else -> v
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    current?.let { setViewport(it.zoomBy(zoom, centroid.x, centroid.y).panBy(pan.x, pan.y)) }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    val (row, col) = current?.squareAt(at.x, at.y) ?: return@detectTapGestures
                    latestState.map.squareAt(row, col)?.let(tap)
                }
            }
            .semantics { contentDescription = "Map of the ${map.name}. The List tab lists every square." },
    ) {
        images.version // Read so a picture that's just been decoded is drawn.
        val v = current ?: return@Canvas
        drawRect(palette.background)
        drawMap(state, v, images, selected, text, palette)
    }
}

/** The squares on screen, for decoding. */
private fun visibleSquares(map: AdventureMap, v: GridViewport): List<Square> =
    v.visibleRows.flatMap { row -> v.visibleCols.mapNotNull { col -> map.squareAt(row, col) } }

/** The theme's colours the map draws with: behind it, over hidden squares, the labels and the selection. */
private data class MapPalette(
    val background: Color,
    val scrim: Color,
    val label: Color,
    val onLabel: Color,
    val selection: Color,
    val onSelection: Color,
)

/**
 * Colours that mean something in the game, so they stay the same in any theme: the medals' bronze
 * and gold, the game's wall colour, and a colour per kind of thing left on a square.
 */
private object MapColors {
    val bronze = Color(0xFFCD7F32)
    val gold = StarGold
    val wall = Color(0xFF2B2B2B)
    val start = Color(0xFF43A047)
    val boss = Color(0xFFD32F2F)
    fun marker(kind: MarkerKind): Color = when (kind) {
        MarkerKind.HEART_CONTAINER -> Color(0xFFE53935)
        MarkerKind.HEART_PIECE -> Color(0xFFF48FB1)
        MarkerKind.SKULLTULA -> Color(0xFFFFB300)
        MarkerKind.SEARCH -> Color(0xFF42A5F5)
    }
}

private fun DrawScope.drawMap(state: AdventureUiState, v: GridViewport, images: SquareImages, selected: String?, text: TextMeasurer, palette: MapPalette) {
    val map = state.map
    val left = v.edgeX(1)
    val top = v.edgeY(1)
    images.preview?.let {
        drawImage(
            it,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(v.edgeX(map.cols + 1) - left, v.edgeY(map.rows + 1) - top),
            filterQuality = FilterQuality.Low,
        )
    }
    val detailed = v.scale >= DETAIL_SCALE
    val sharp = if (v.scale >= 1f) FilterQuality.None else FilterQuality.Low
    for (row in v.visibleRows) for (col in v.visibleCols) {
        val square = map.squareAt(row, col) ?: continue
        val rect = Rect(v.edgeX(col).toFloat(), v.edgeY(row).toFloat(), v.edgeX(col + 1).toFloat(), v.edgeY(row + 1).toFloat())
        if (detailed) {
            images.square(square)?.let {
                drawImage(
                    it,
                    dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                    dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                    filterQuality = sharp,
                )
            }
        }
        drawLook(state.look(square, square.id == selected), rect, text, palette)
    }
    for (row in v.visibleRows) for (col in v.visibleCols) {
        val square = map.squareAt(row, col) ?: continue
        val rect = Rect(v.edgeX(col).toFloat(), v.edgeY(row).toFloat(), v.edgeX(col + 1).toFloat(), v.edgeY(row + 1).toFloat())
        square.blockades.forEach { drawWall(rect, it) }
    }
    selected?.let(map::square)?.let { s ->
        val rect = Rect(v.edgeX(s.col).toFloat(), v.edgeY(s.row).toFloat(), v.edgeX(s.col + 1).toFloat(), v.edgeY(s.row + 1).toFloat())
        drawRect(palette.onSelection, rect.topLeft, rect.size, style = Stroke(5.dp.toPx()))
        drawRect(palette.selection, rect.topLeft, rect.size, style = Stroke(2.5.dp.toPx()))
    }
    drawAxes(v, text, palette)
}

/** One square's state over its picture. Detail comes in as the square gets bigger on screen. */
private fun DrawScope.drawLook(look: SquareLook, rect: Rect, text: TextMeasurer, palette: MapPalette) {
    val w = rect.width
    if (look.hidden) {
        drawRect(palette.scrim.copy(alpha = 0.72f), rect.topLeft, rect.size)
        if (w >= 28.dp.toPx()) centred(text, "?", rect.center, TextStyle(color = palette.onLabel.copy(alpha = 0.8f), fontSize = (w / 5).toSp(), fontWeight = FontWeight.Bold))
        return
    }
    if (look.dimmed) drawRect(palette.scrim.copy(alpha = 0.55f), rect.topLeft, rect.size)
    val stroke = maxOf(2.dp.toPx(), w * 0.025f)
    val edge = when {
        look.complete || look.status == SquareStatus.A_RANK -> MapColors.gold
        look.status == SquareStatus.CLEARED -> MapColors.bronze
        else -> null
    }
    edge?.let {
        val inset = stroke / 2
        drawRect(it, rect.topLeft + Offset(inset, inset), Size(rect.width - stroke, rect.height - stroke), style = Stroke(stroke))
    }
    val badge = maxOf(10.dp.toPx(), w * 0.14f)
    if (w >= 36.dp.toPx()) {
        var x = rect.left + stroke + 2.dp.toPx()
        val y = rect.top + stroke + 2.dp.toPx()
        if (look.status == SquareStatus.A_RANK) {
            chip(text, "A", Offset(x, y), badge, MapColors.gold, Color.Black)
            x += badge + 2.dp.toPx()
        }
        if (look.complete) chip(text, "★", Offset(x, y), badge, MapColors.gold, Color.Black)
        val corner = Offset(rect.right - stroke - 2.dp.toPx() - badge, y)
        when {
            look.finalBoss -> chip(text, "☠", corner, badge, MapColors.boss, Color.White)
            look.start -> chip(text, "▶", corner, badge, MapColors.start, Color.White)
        }
    } else if (look.finalBoss || look.start) {
        drawCircle(if (look.finalBoss) MapColors.boss else MapColors.start, w * 0.08f, Offset(rect.right - w * 0.14f, rect.top + w * 0.14f))
    }
    if (look.markers.isEmpty() || look.dimmed) return
    if (w < 32.dp.toPx()) {
        // Too small for a row of them: one dot says something's left.
        val r = 2.5.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.6f), r * 1.4f, Offset(rect.left + r * 2, rect.bottom - r * 2))
        drawCircle(MapColors.marker(look.markers.first().kind), r, Offset(rect.left + r * 2, rect.bottom - r * 2))
        return
    }
    // What's left: a dot per kind along the bottom, with its count once there's room.
    val r = maxOf(2.5.dp.toPx(), w * 0.045f)
    val withCounts = w >= 72.dp.toPx()
    var x = rect.left + stroke + r + 2.dp.toPx()
    val y = rect.bottom - stroke - r - 2.dp.toPx()
    for (m in look.markers) {
        drawCircle(Color.Black.copy(alpha = 0.6f), r * 1.35f, Offset(x, y))
        drawCircle(MapColors.marker(m.kind), r, Offset(x, y))
        x += r * 2.9f
        if (withCounts) {
            val layout = text.measure("${m.count}", TextStyle(color = Color.White, fontSize = (r * 1.7f).toSp(), fontWeight = FontWeight.Bold))
            drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(x - r * 0.9f, y - layout.size.height / 2f), Size(layout.size.width + r * 0.6f, layout.size.height.toFloat()), CornerRadius(r))
            drawText(layout, topLeft = Offset(x - r * 0.6f, y - layout.size.height / 2f))
            x += layout.size.width + r * 1.6f
        }
    }
}

/** The game's wavy wall along [side] of [rect]: this square doesn't open its neighbour that way. */
private fun DrawScope.drawWall(rect: Rect, side: Side) {
    val (from, to) = when (side) {
        Side.NORTH -> rect.topLeft to rect.topRight
        Side.SOUTH -> rect.bottomLeft to rect.bottomRight
        Side.WEST -> rect.topLeft to rect.bottomLeft
        Side.EAST -> rect.topRight to rect.bottomRight
    }
    val length = (to - from).getDistance()
    val waves = 6
    val amp = maxOf(1.5.dp.toPx(), rect.height * 0.035f)
    val dir = (to - from) / length
    val normal = Offset(-dir.y, dir.x)
    val path = Path().apply {
        moveTo(from.x, from.y)
        for (i in 1..waves * 2) {
            val p = from + dir * (length * i / (waves * 2))
            val n = normal * (if (i % 2 == 1) amp else -amp)
            lineTo(p.x + n.x, p.y + n.y)
        }
        lineTo(to.x, to.y)
    }
    drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(maxOf(3.dp.toPx(), amp * 1.4f)))
    drawPath(path, MapColors.wall, style = Stroke(maxOf(1.5.dp.toPx(), amp * 0.7f)))
}

/** Row letters down the left and column numbers along the top, once squares are big enough to need them. */
private fun DrawScope.drawAxes(v: GridViewport, text: TextMeasurer, palette: MapPalette) {
    val squareH = v.squareHeight * v.scale
    if (squareH < 16.dp.toPx()) return
    val style = TextStyle(color = palette.onLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val pad = 3.dp.toPx()
    for (row in v.visibleRows) {
        val y = (v.edgeY(row) + v.edgeY(row + 1)) / 2f
        val layout = text.measure(AdventureLabels.rowLetter(row), style)
        val x = maxOf(v.edgeX(1).toFloat(), 0f) + pad
        drawRoundRect(palette.label, Offset(x - pad, y - layout.size.height / 2f - 1), Size(layout.size.width + pad * 2, layout.size.height + 2f), CornerRadius(pad))
        drawText(layout, topLeft = Offset(x, y - layout.size.height / 2f))
    }
    for (col in v.visibleCols) {
        val x = (v.edgeX(col) + v.edgeX(col + 1)) / 2f
        val layout = text.measure("$col", style)
        val y = maxOf(v.edgeY(1).toFloat(), 0f) + pad
        drawRoundRect(palette.label, Offset(x - layout.size.width / 2f - pad, y - 1), Size(layout.size.width + pad * 2, layout.size.height + 2f), CornerRadius(pad))
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
    }
}

private fun DrawScope.chip(text: TextMeasurer, label: String, topLeft: Offset, size: Float, fill: Color, ink: Color) {
    drawRoundRect(fill, topLeft, Size(size, size), CornerRadius(size * 0.25f))
    drawRoundRect(Color.Black.copy(alpha = 0.5f), topLeft, Size(size, size), CornerRadius(size * 0.25f), style = Stroke(1.dp.toPx()))
    centred(text, label, topLeft + Offset(size / 2, size / 2), TextStyle(color = ink, fontSize = (size * 0.62f).toSp(), fontWeight = FontWeight.Bold))
}

private fun DrawScope.centred(text: TextMeasurer, label: String, center: Offset, style: TextStyle) {
    val layout = text.measure(label, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

/** The labels the map and its sheet use for rows and columns. */
object AdventureLabels {
    /** Prima's and Zelda Wiki's row letters, A at the top. */
    fun rowLetter(row: Int): String = ('A' + (row - 1)).toString()
}
