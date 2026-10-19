package gx.zelda.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The current game's ornament ([GameDecor.ornament]) as a rule across the width: decoration only,
 * drawn in code from simple shapes rather than taken from the games. Draws nothing for
 * [Ornament.NONE].
 */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier) {
    val ornament = LocalGameDecor.current.ornament
    if (ornament == Ornament.NONE) return
    val color = MaterialTheme.colorScheme.primary
    val accent = LocalGameDecor.current.star
    // A many-coloured ornament falls back to the primary colour if the theme gives no accents.
    val accents = LocalGameDecor.current.accents.ifEmpty { listOf(color) }
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        when (ornament) {
            Ornament.NONE -> Unit
            Ornament.PIXEL_RULE -> pixelRule(color)
            Ornament.WAVES -> waves(color)
            Ornament.VINE -> vine(color, accent)
            Ornament.GLYPHS -> glyphs(color)
            Ornament.BRICKS -> bricks(color)
            Ornament.TRIFORCES -> triforces(color)
            Ornament.SEASONS -> seasons(color, accents)
            Ornament.COLOUR_BANDS -> colourBands(accents)
            Ornament.CREST -> crest(color)
            Ornament.CLOCK -> clock(color, accent)
            Ornament.TWILI -> twili(color)
            Ornament.CLOUDS -> clouds(color)
            Ornament.SPIRAL -> spiral(color)
        }
    }
}

/** Square pixels with a gap, every third one in the second row: the stepped edge of an NES window. */
private fun DrawScope.pixelRule(color: Color) {
    val px = 3.dp.toPx()
    val top = (size.height - 2 * px) / 2
    var i = 0
    var x = 0f
    while (x + px <= size.width) {
        drawRect(color, Offset(x, top), Size(px, px))
        if (i % 3 == 1) drawRect(color.copy(alpha = 0.5f), Offset(x, top + px), Size(px, px))
        x += 2 * px
        i++
    }
}

/** A rolling wave, crests every 16dp. */
private fun DrawScope.waves(color: Color) {
    val period = 16.dp.toPx()
    val amplitude = size.height / 3
    val mid = size.height / 2
    val path = Path().apply {
        moveTo(0f, mid)
        var x = 0f
        while (x <= size.width) {
            lineTo(x, mid - amplitude * sin(2 * PI * x / period).toFloat())
            x += 1.dp.toPx()
        }
    }
    drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
}

/** A gently winding stem with a leaf every 24dp, alternating sides, and a gold bud at each end. */
private fun DrawScope.vine(color: Color, bud: Color) {
    val period = 48.dp.toPx()
    val mid = size.height / 2
    val stem = Path().apply {
        moveTo(0f, mid)
        var x = 0f
        while (x <= size.width) {
            lineTo(x, mid + size.height / 6 * sin(2 * PI * x / period).toFloat())
            x += 1.dp.toPx()
        }
    }
    drawPath(stem, color, style = Stroke(width = 1.2.dp.toPx()))
    val leaf = Size(7.dp.toPx(), 3.5.dp.toPx())
    var x = period / 4
    var up = true
    while (x + leaf.width <= size.width) {
        val y = if (up) 0f else size.height - leaf.height
        drawOval(color, Offset(x, y), leaf)
        x += period / 2
        up = !up
    }
    val r = 2.5.dp.toPx()
    drawCircle(bud, r, Offset(r, mid))
    drawCircle(bud, r, Offset(size.width - r, mid))
}

/** A hairline with a hollow diamond in the middle and short ticks at the ends. */
private fun DrawScope.glyphs(color: Color) {
    val mid = size.height / 2
    val stroke = 1.dp.toPx()
    val d = size.height / 2
    val cx = size.width / 2
    drawLine(color, Offset(0f, mid), Offset(cx - d * 1.6f, mid), stroke)
    drawLine(color, Offset(cx + d * 1.6f, mid), Offset(size.width, mid), stroke)
    val diamond = Path().apply {
        moveTo(cx, mid - d)
        lineTo(cx + d, mid)
        lineTo(cx, mid + d)
        lineTo(cx - d, mid)
        close()
    }
    drawPath(diamond, color, style = Stroke(width = stroke))
    drawLine(color, Offset(0f, mid - d), Offset(0f, mid + d), stroke * 2)
    drawLine(color, Offset(size.width, mid - d), Offset(size.width, mid + d), stroke * 2)
}

/** Two courses of bricks, outlined, each course offset by half a brick. */
private fun DrawScope.bricks(color: Color) {
    val stroke = 1.dp.toPx()
    val course = size.height / 2
    val brick = 4 * course
    drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
    drawLine(color, Offset(0f, course), Offset(size.width, course), stroke)
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
    for (row in 0..1) {
        var x = if (row == 0) 0f else brick / 2
        while (x <= size.width) {
            drawLine(color, Offset(x, row * course), Offset(x, (row + 1) * course), stroke)
            x += brick
        }
    }
}

/** Small filled Triforces, three triangles each, spaced along the line. */
private fun DrawScope.triforces(color: Color) {
    val h = size.height
    val w = h * 1.15f
    val gap = w * 1.6f
    val count = ((size.width + gap - w) / (w + gap)).toInt().coerceAtLeast(1)
    val start = (size.width - (count * w + (count - 1) * gap)) / 2
    for (i in 0 until count) {
        val left = start + i * (w + gap)
        fun triangle(x: Float, y: Float, tw: Float, th: Float) = Path().apply {
            moveTo(x + tw / 2, y)
            lineTo(x + tw, y + th)
            lineTo(x, y + th)
            close()
        }
        drawPath(triangle(left + w / 4, 0f, w / 2, h / 2), color)
        drawPath(triangle(left, h / 2, w / 2, h / 2), color)
        drawPath(triangle(left + w / 2, h / 2, w / 2, h / 2), color)
    }
}

/** A hairline with a dot every 24dp, the dots taking each of [colors] in turn. */
private fun DrawScope.seasons(line: Color, colors: List<Color>) {
    val mid = size.height / 2
    drawLine(line, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
    val step = 24.dp.toPx()
    val r = size.height / 2.4f
    var x = step / 2
    var i = 0
    while (x <= size.width) {
        drawCircle(colors[i % colors.size], r, Offset(x, mid))
        x += step
        i++
    }
}

/** [colors] as equal bands across the width, with a small gap between them. */
private fun DrawScope.colourBands(colors: List<Color>) {
    val gap = 3.dp.toPx()
    val height = size.height / 2
    val top = (size.height - height) / 2
    val width = (size.width - gap * (colors.size - 1)) / colors.size
    colors.forEachIndexed { i, c -> drawRect(c, Offset(i * (width + gap), top), Size(width, height)) }
}

/** A hairline either side of a hollow diamond holding a small filled Triforce. */
private fun DrawScope.crest(color: Color) {
    val stroke = 1.dp.toPx()
    val mid = size.height / 2
    val cx = size.width / 2
    val d = size.height / 2
    drawLine(color, Offset(0f, mid), Offset(cx - d * 1.8f, mid), stroke)
    drawLine(color, Offset(cx + d * 1.8f, mid), Offset(size.width, mid), stroke)
    val diamond = Path().apply {
        moveTo(cx, mid - d)
        lineTo(cx + d * 1.5f, mid)
        lineTo(cx, mid + d)
        lineTo(cx - d * 1.5f, mid)
        close()
    }
    drawPath(diamond, color, style = Stroke(width = stroke))
    val t = d * 0.9f
    fun triangle(x: Float, y: Float, w: Float) = Path().apply {
        moveTo(x + w / 2, y)
        lineTo(x + w, y + w * 0.87f)
        lineTo(x, y + w * 0.87f)
        close()
    }
    val top = mid - t * 0.87f
    drawPath(triangle(cx - t / 2, top, t), color)
    drawPath(triangle(cx - t, top + t * 0.87f, t), color)
    drawPath(triangle(cx, top + t * 0.87f, t), color)
}

/** An arc bowing downward across the width, with hour ticks and a moon-coloured dot at each end. */
private fun DrawScope.clock(color: Color, end: Color) {
    val stroke = 1.2.dp.toPx()
    val r = size.height / 4
    val sag = size.height / 2
    val arc = Path().apply {
        moveTo(r, r)
        quadraticTo(size.width / 2, r + sag * 2, size.width - r, r)
    }
    drawPath(arc, color, style = Stroke(width = stroke))
    // Ticks along the curve at 24 even steps, like the hours on the clock's face.
    for (i in 1 until 24) {
        val t = i / 24f
        val x = (1 - t) * (1 - t) * r + 2 * (1 - t) * t * (size.width / 2) + t * t * (size.width - r)
        val y = (1 - t) * (1 - t) * r + 2 * (1 - t) * t * (r + sag * 2) + t * t * r
        val len = if (i % 6 == 0) 3.dp.toPx() else 1.5.dp.toPx()
        drawLine(color, Offset(x, y - len), Offset(x, y), stroke)
    }
    drawCircle(end, r, Offset(r, r))
    drawCircle(end, r, Offset(size.width - r, r))
}

/** Squares stepping up and down along the line, filled and hollow in turn. */
private fun DrawScope.twili(color: Color) {
    val cell = size.height / 2
    val stroke = 1.dp.toPx()
    var x = 0f
    var i = 0
    while (x + cell <= size.width) {
        val y = if (i % 4 < 2) 0f else cell
        if (i % 2 == 0) drawRect(color, Offset(x, y), Size(cell, cell))
        else drawRect(color, Offset(x + stroke / 2, y + stroke / 2), Size(cell - stroke, cell - stroke), style = Stroke(width = stroke))
        x += cell * 1.5f
        i++
    }
}

/** Half-circle cloud tops sitting on a baseline, alternating large and small. */
private fun DrawScope.clouds(color: Color) {
    val stroke = 1.2.dp.toPx()
    val base = size.height - stroke
    drawLine(color, Offset(0f, base), Offset(size.width, base), stroke)
    var x = 0f
    var big = true
    while (x < size.width) {
        val r = if (big) size.height * 0.8f else size.height * 0.5f
        drawArc(color, 180f, 180f, false, Offset(x, base - r), Size(2 * r, 2 * r), style = Stroke(width = stroke))
        x += 2 * r
        big = !big
    }
}

/** A hairline threaded through rings every 32dp, each ring with a smaller ring and a dot inside. */
private fun DrawScope.spiral(color: Color) {
    val stroke = 1.dp.toPx()
    val mid = size.height / 2
    val r = size.height / 2 - stroke
    val step = 32.dp.toPx()
    var x = step / 2
    var last = 0f
    while (x + r <= size.width) {
        drawLine(color, Offset(last, mid), Offset(x - r, mid), stroke)
        drawCircle(color, r, Offset(x, mid), style = Stroke(width = stroke))
        drawCircle(color, r * 0.55f, Offset(x + r * 0.15f, mid), style = Stroke(width = stroke))
        drawCircle(color, r * 0.18f, Offset(x + r * 0.25f, mid))
        last = x + r
        x += step
    }
    drawLine(color, Offset(last, mid), Offset(size.width, mid), stroke)
}

