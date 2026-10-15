package gx.zelda.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressSummary
import gx.zelda.core.TrackerKind
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.Tag

/** Every tracker's progress on the active save file; tapping a row opens that tracker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: OverviewUiState?,
    viewModel: OverviewViewModel,
    onOpenTracker: (TrackerKind) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Progress Overview", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it, "") },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // A tracker can have several rows (Shrines and Hearts & Stamina), so the key is the position.
            itemsIndexed(state.rows, key = { i, row -> "$i:${row.title}" }) { _, row ->
                RowCard(row, onClick = { onOpenTracker(row.kind) })
            }
            state.mapItems?.let { items -> item(key = "map") { MapItemsCard(items) } }
        }
    }
}

@Composable
private fun RowCard(row: OverviewRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val summary = (row.status as? RowStatus.Ready)?.summary
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary?.title ?: row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (summary != null) Count(summary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            when (row.status) {
                RowStatus.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                RowStatus.Unavailable -> Text(
                    "Unavailable: this tracker's data couldn't be loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is RowStatus.Ready -> Details(row.status.summary)
            }
        }
    }
}

@Composable
private fun Count(summary: ProgressSummary) {
    val text = "${grouped(summary.done)} / ${grouped(summary.total)}"
    if (summary.total > 0 && summary.done >= summary.total) {
        Tag(text, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
    } else {
        Tag(text)
    }
}

@Composable
private fun Details(summary: ProgressSummary) {
    LinearProgressIndicator(progress = { summary.fraction }, modifier = Modifier.fillMaxWidth())
    summary.secondary?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    summary.next?.let {
        Text("Next: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Not a percentage: named locations count toward it too, and nothing tracks them. */
@Composable
private fun MapItemsCard(items: List<ItemCount>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val done = items.sumOf { it.done }
            val total = items.sumOf { it.total }
            Text("Map completion", style = MaterialTheme.typography.titleMedium)
            Text(
                "${joined(items.map { it.label }).replaceFirstChar { it.uppercase() }}: " +
                    "${grouped(done)} of ${grouped(total)} items toward 100%",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text(
                "The map's percentage also counts the named locations you discover. They aren't tracked here, " +
                    "so this isn't the percentage itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "a", "a and b", "a, b and c". */
private fun joined(parts: List<String>): String =
    if (parts.size <= 1) parts.joinToString() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()

/** 1024 → "1,024". */
private fun grouped(n: Int): String =
    if (n < 0) "-" + grouped(-n) else n.toString().reversed().chunked(3).joinToString(",").reversed()
