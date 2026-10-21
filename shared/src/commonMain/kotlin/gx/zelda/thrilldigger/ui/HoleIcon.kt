package gx.zelda.thrilldigger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import gx.zelda.resources.Res
import gx.zelda.resources.thrill_blue
import gx.zelda.resources.thrill_bomb
import gx.zelda.resources.thrill_gold
import gx.zelda.resources.thrill_green
import gx.zelda.resources.thrill_red
import gx.zelda.resources.thrill_rupoor
import gx.zelda.resources.thrill_silver
import gx.zelda.thrilldigger.model.Hole
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Skyward Sword's own render of what a hole can hold (scripts/fetch-thrill-digger-icons.py), or null
 * for an undug hole. A skipped hole borrows the Green Rupee's shape, greyed by [HoleIcon].
 */
fun holeDrawable(hole: Hole): DrawableResource? = when (hole) {
    Hole.UNKNOWN -> null
    Hole.GREEN -> Res.drawable.thrill_green
    Hole.BLUE -> Res.drawable.thrill_blue
    Hole.RED -> Res.drawable.thrill_red
    Hole.SILVER -> Res.drawable.thrill_silver
    Hole.GOLD -> Res.drawable.thrill_gold
    Hole.RUPOOR -> Res.drawable.thrill_rupoor
    Hole.BOMB -> Res.drawable.thrill_bomb
    Hole.SKIPPED -> Res.drawable.thrill_green
}

/** What a screen reader calls each kind of hole. */
fun holeName(hole: Hole): String = when (hole) {
    Hole.UNKNOWN -> "Not dug"
    Hole.RUPOOR -> "Rupoor"
    Hole.BOMB -> "Bomb"
    Hole.SKIPPED -> "Dug, colour not seen"
    else -> "${hole.label} Rupee"
}

/** A skipped hole's Rupee: a plain grey silhouette, its colour unknown. */
private val SkippedGrey = Color(0xFF9E9E9E)

/** The minus sign's colour: a dark purple, ringed in white so it shows on the Rupoor's own dark purple. */
private val MinusPurple = Color(0xFF3A0B5C)

/**
 * [hole]'s picture, fitted into [modifier]'s size. A Rupoor gets a dark purple minus sign over its
 * right-hand side, drawn here, so it can't be taken for a Silver Rupee at a glance. A skipped hole
 * is a grey Rupee with a "?" on it.
 */
@Composable
fun HoleIcon(hole: Hole, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val drawable = holeDrawable(hole) ?: return
    Box(modifier) {
        Image(
            painterResource(drawable),
            contentDescription = holeName(hole),
            contentScale = ContentScale.Fit,
            alpha = alpha,
            colorFilter = if (hole == Hole.SKIPPED) ColorFilter.tint(SkippedGrey) else null,
            modifier = Modifier.fillMaxSize(),
        )
        if (hole == Hole.SKIPPED) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val size = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.5f).toSp() }
                Text("?", color = Color.White.copy(alpha = alpha), fontSize = size, fontWeight = FontWeight.Black)
            }
        }
        if (hole == Hole.RUPOOR) {
            Canvas(Modifier.fillMaxSize()) {
                // The pictures are about 0.64 as wide as tall, so a square box leaves room on the right.
                val side = minOf(size.width, size.height)
                val barWidth = side * 0.42f
                val barHeight = side * 0.14f
                val left = size.width / 2 + side * 0.04f
                val top = size.height / 2 - barHeight / 2
                val radius = CornerRadius(barHeight / 2)
                drawRoundRect(Color.White.copy(alpha = alpha), Offset(left, top), Size(barWidth, barHeight), radius, style = Stroke(width = barHeight * 0.45f))
                drawRoundRect(MinusPurple.copy(alpha = alpha), Offset(left, top), Size(barWidth, barHeight), radius)
            }
        }
    }
}
