package gx.zelda.art.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.art.InstalledPack
import gx.zelda.core.rememberDocumentPicker
import gx.zelda.core.ui.MenuOrBackButton

/** Zip files, as different file managers label them. */
private val ZIP_TYPES = listOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * Settings → Art packs: import a pack from a file, see what each installed one covers, remove one.
 * It explains what a pack does and nothing about where one comes from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtPacksScreen(viewModel: ArtPacksViewModel, onBack: () -> Unit) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()
    val pick = rememberDocumentPicker(ZIP_TYPES) { viewModel.import(it) }
    var removing by remember { mutableStateOf<InstalledPack?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Art packs") }, navigationIcon = { MenuOrBackButton(onBack) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Art packs replace the built-in icons and maps with art you supply. A pack can cover one game " +
                            "or several; anything it leaves out keeps the app's own look. The newest pack wins where two " +
                            "cover the same thing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val running = import as? ImportState.Running
                    Button(onClick = pick, enabled = running == null && viewModel.available, modifier = Modifier.fillMaxWidth()) {
                        Text(if (running == null) "Import pack" else "Importing…")
                    }
                    if (running != null) LinearProgressIndicator(progress = { running.progress }, modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
            val list = packs
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No art packs installed.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.pack.dir }) { row -> PackItem(row, onRemove = { removing = row.pack }) }
        }
    }

    removing?.let { pack ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${pack.name}?") },
            text = { Text("Its files are deleted from this device. Anything it covered goes back to the app's own art.") },
            confirmButton = { TextButton(onClick = { viewModel.remove(pack); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    when (val state = import) {
        is ImportState.Done -> ImportedDialog(state, onDismiss = viewModel::dismissImport)
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text("Couldn't import the pack") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissImport) { Text("OK") } },
        )
        else -> Unit
    }
}

@Composable
private fun PackItem(row: PackRow, onRemove: () -> Unit) {
    val pack = row.pack
    ListItem(
        headlineContent = { Text(if (pack.version.isBlank()) pack.name else "${pack.name} · ${pack.version}") },
        supportingContent = {
            Column {
                row.coverage.forEach { Text(coverageLine(it)) }
                if (row.coverage.isEmpty()) Text("Covers nothing this app draws")
                Text(megabytes(pack.bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${pack.name}") }
        },
    )
    HorizontalDivider()
}

@Composable
private fun ImportedDialog(state: ImportState.Done, onDismiss: () -> Unit) {
    val result = state.result
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imported ${result.pack.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.coverage.forEach { Text(coverageLine(it)) }
                if (result.ignored.isNotEmpty()) {
                    Text("${result.ignored.size} ${if (result.ignored.size == 1) "entry isn't" else "entries aren't"} used by this app.")
                }
                if (result.rejected.isNotEmpty()) {
                    Text("${result.rejected.size} left out:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                    result.rejected.take(MAX_REASONS).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (result.rejected.size > MAX_REASONS) Text("and ${result.rejected.size - MAX_REASONS} more", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private const val MAX_REASONS = 8

private fun coverageLine(c: Coverage) = "${c.game.title}: ${c.have}/${c.of}"

private fun megabytes(bytes: Long): String {
    val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
    return "${tenths / 10}.${tenths % 10} MB"
}
