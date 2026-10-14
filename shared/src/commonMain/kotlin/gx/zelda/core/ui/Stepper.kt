package gx.zelda.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** "−  n  +" with a label; the buttons stop at 0 and [max]. */
@Composable
fun Stepper(label: String, detail: String, value: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onValueChange(value - 1) }, enabled = value > 0) {
            Text("−", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "Fewer" })
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp).semantics { contentDescription = "$label: $value of $max" },
        )
        FilledTonalIconButton(onClick = { onValueChange(value + 1) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "More" })
        }
    }
}
