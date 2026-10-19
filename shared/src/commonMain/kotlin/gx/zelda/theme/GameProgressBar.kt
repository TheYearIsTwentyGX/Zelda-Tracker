package gx.zelda.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * A determinate progress bar, full width, drawn in the current game's style ([GameDecor.progress]).
 * [progress] is clamped to 0..1.
 */
@Composable
fun GameProgressBar(progress: () -> Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    when (LocalGameDecor.current.progress) {
        ProgressStyle.SOLID -> LinearProgressIndicator(progress = progress, modifier = modifier.fillMaxWidth())
        ProgressStyle.ROUNDED -> LinearProgressIndicator(
            progress = progress,
            modifier = modifier.fillMaxWidth().height(10.dp),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        ProgressStyle.THIN -> LinearProgressIndicator(
            progress = progress,
            modifier = modifier.fillMaxWidth().height(3.dp),
            trackColor = colors.outlineVariant,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        ProgressStyle.SEGMENTED -> {
            val value = progress().coerceIn(0f, 1f)
            Canvas(
                modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
            ) {
                val gap = 2.dp.toPx()
                val segments = SEGMENTS
                val width = (size.width - gap * (segments - 1)) / segments
                val filled = value * segments
                for (i in 0 until segments) {
                    val x = i * (width + gap)
                    drawRect(colors.outlineVariant, Offset(x, 0f), Size(width, size.height))
                    // The block being filled shows its part, so small changes still move the bar.
                    val part = (filled - i).coerceIn(0f, 1f)
                    if (part > 0f) drawRect(colors.primary, Offset(x, 0f), Size(floor(width * part), size.height))
                }
            }
        }
    }
}

private const val SEGMENTS = 20
