package gx.zelda.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** ★★☆☆ — filled stars up to [level], hollow up to [max]. */
@Composable
fun Stars(level: Int, max: Int, modifier: Modifier = Modifier) {
    if (max == 0) return
    val dim = MaterialTheme.colorScheme.outlineVariant
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = StarGold)) { append("★".repeat(level)) }
            withStyle(SpanStyle(color = dim)) { append("★".repeat(max - level)) }
        },
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
    )
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
