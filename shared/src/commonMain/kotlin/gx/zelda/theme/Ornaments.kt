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
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        when (ornament) {
            Ornament.NONE -> Unit
            Ornament.PIXEL_RULE -> pixelRule(color)
            Ornament.WAVES -> waves(color)
            Ornament.VINE -> vine(color, accent)
            Ornament.GLYPHS -> glyphs(color)
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
