package gx.zelda.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** ★★☆☆ — filled stars up to [level], hollow up to [max]. */
@Composable
fun Stars(
    level: Int,
    max: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) = Stars(level.toFloat(), max, modifier, style)

/** Like the Int version, but a fractional [level] fills part of a star (1.33 = one and a third). */
@Composable
fun Stars(
    level: Float,
    max: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    if (max == 0) return
    val dim = MaterialTheme.colorScheme.outlineVariant
    Row(modifier) {
        for (i in 0 until max) {
            val fill = (level - i).coerceIn(0f, 1f)
            Box {
                Text("★", style = style, color = dim)
                if (fill > 0f) {
                    Text(
                        "★",
                        style = style,
                        color = StarGold,
                        modifier = Modifier.drawWithContent {
                            clipRect(right = size.width * fill) { this@drawWithContent.drawContent() }
                        },
                    )
                }
            }
        }
    }
}

/**
 * [Stars] inside an outlined pill that steps to the next level on each tap
 * (wrapping from [max] back to 0), with a haptic tick.
 */
@Composable
fun StarLevelButton(level: Int, max: Int, onLevelChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val next = (level + 1) % (max + 1)
    StarButton(
        value = level.toFloat(),
        max = max,
        description = "Upgrade level $level of $max",
        clickLabel = "Change to level $next",
        onClick = { onLevelChange(next) },
        modifier = modifier,
    )
}

/** The tappable star pill: outlined, rippled, with a haptic tick on each tap. */
@Composable
fun StarButton(
    value: Float,
    max: Int,
    description: String,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { contentDescription = description }
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClickLabel = clickLabel, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Stars(value, max, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
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
