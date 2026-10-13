package gx.zelda.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** ★★☆☆ — filled stars up to [level], hollow up to [max]. */
@Composable
fun Stars(
    level: Int,
    max: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    if (max == 0) return
    val dim = MaterialTheme.colorScheme.outlineVariant
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = StarGold)) { append("★".repeat(level)) }
            withStyle(SpanStyle(color = dim)) { append("★".repeat(max - level)) }
        },
        style = style,
        modifier = modifier,
    )
}

/**
 * [Stars] inside an outlined pill that steps to the next level on each tap
 * (wrapping from [max] back to 0), with a haptic tick.
 */
@Composable
fun StarLevelButton(level: Int, max: Int, onLevelChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val next = (level + 1) % (max + 1)
    val cycle = {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onLevelChange(next)
    }
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { contentDescription = "Upgrade level $level of $max" }
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClickLabel = "Change to level $next", role = Role.Button, onClick = cycle)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Stars(level, max, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Small rounded label, e.g. a source tag or a status badge. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
