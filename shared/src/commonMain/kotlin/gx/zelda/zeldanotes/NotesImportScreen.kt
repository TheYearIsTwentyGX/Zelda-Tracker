package gx.zelda.zeldanotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gx.zelda.core.rememberImagesPicker
import gx.zelda.core.rememberVideoPicker
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.koroks.model.Korok

/** Korok Seeds → ⋮ → Import from Zelda Notes: pick screenshots or a recording, review, Apply to a chosen save file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesImportScreen(state: NotesImportUiState?, loadFailed: Boolean, viewModel: NotesImportViewModel, onBack: () -> Unit) {
    val pickImages = rememberImagesPicker { viewModel.readImages(it) }
    val pickVideo = rememberVideoPicker { viewModel.readVideo(it) }
    var confirming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Import from Zelda Notes")
                        if (state != null) {
                            Text("Into ${state.target.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state != null && state.inputs.isNotEmpty() && state.reading == null && state.applied == null) {
                        TextButton(onClick = viewModel::startOver) { Text("Start over") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state == null -> LoadingOrError(loadFailed, "The Zelda Notes import couldn't be loaded.", padding)
            state.applied != null -> Applied(state.applied.first, state.applied.second.label, padding, onBack)
            state.reading != null -> Reading(state.reading, padding)
            state.review == null -> Intro(state, padding, pickImages, pickVideo)
            else -> Review(state, state.review, viewModel, padding, pickImages, pickVideo, onApply = { confirming = true })
        }
    }
    if (confirming && state?.review != null) {
        val n = state.selected.count { it !in state.progress.completed }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Apply to ${state.target.label}?") },
            text = {
                Text(
                    "Mark ${seeds(n)} collected on ${state.target.label}. Zelda Notes only shows the save your Switch last " +
                        "synced; make sure that's this one. Nothing is unmarked.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.apply()
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

private fun seeds(n: Int) = if (n == 1) "1 Korok seed" else "$n Korok seeds"

@Composable
private fun Intro(state: NotesImportUiState, padding: PaddingValues, pickImages: () -> Unit, pickVideo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Read your Koroks from the Nintendo Switch App", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. In the Nintendo Switch App, open Zelda Notes → Navigation.\n" +
                        "2. Turn on Hide Discovered Destinations, so collected Koroks disappear.\n" +
                        "3. Optional: in Categories, show only Hidden Koroks.\n" +
                        "4. Zoom in until the Korok droplets stand apart.\n" +
                        "5. Screen-record while you pan slowly across the map, or take screenshots that cover it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Koroks missing from the map are suggested as collected. Everything is read on the phone; nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SyncWarning()
        Button(onClick = pickVideo, modifier = Modifier.fillMaxWidth()) { Text("Pick a screen recording") }
        OutlinedButton(onClick = pickImages, modifier = Modifier.fillMaxWidth()) { Text("Pick screenshots") }
        if (state.inputs.isNotEmpty()) {
            Text("Nothing judged yet:", style = MaterialTheme.typography.titleSmall)
            state.inputs.forEach { InputRow(it) }
        }
    }
}

@Composable
private fun SyncWarning() {
    Text(
        "Zelda Notes shows one save: the one your Switch last synced. Import it into the matching save file here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun Reading(progress: Pair<Int, Int>, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reading ${progress.first} of ${progress.second}…", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Review(
    state: NotesImportUiState,
    review: NotesReview,
    viewModel: NotesImportViewModel,
    padding: PaddingValues,
    pickImages: () -> Unit,
    pickVideo: () -> Unit,
    onApply: () -> Unit,
) {
    val total = review.total
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.weight(1f)) {
            item(key = "_target") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import into", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (file in state.saveFiles) {
                            FilterChip(selected = file.id == state.target.id, onClick = { viewModel.setTarget(file.id) }, label = { Text(file.label) })
                        }
                    }
                    SyncWarning()
                }
            }
            item(key = "_totals") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "After Apply, ${state.target.label} has ${state.collectedAfterApply(total)} of $total seeds: " +
                            "${state.percentAfterApply(total)}%. Zelda Notes → Categories → Hidden Koroks should show the same " +
                            "once every part of the map has been read.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Judged ${review.judged} of $total Koroks · missing in Zelda Notes ${review.toCollect.size + review.alreadyCollected}" +
                            (if (review.alreadyCollected > 0) " (${review.alreadyCollected} already marked here)" else "") +
                            " · unsure ${review.unsure.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (review.shownButCollectedHere.isNotEmpty()) {
                        Text(
                            "${review.shownButCollectedHere.size} marked collected here are still drawn in Zelda Notes; they're left as they are. " +
                                "If that's many, this may be the wrong save file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item(key = "_map") { CoverageMap(review, Modifier.padding(16.dp).fillMaxWidth()) }
            if (review.toCollect.isNotEmpty()) {
                item(key = "_h_found") { Header("Collected in Zelda Notes", "Missing from its map. Ticked to mark collected.") }
                // Missing from Zelda Notes means collected in the game, so naming it spoils nothing.
                items(review.toCollect, key = { it.id }) { KorokRow(it, state, viewModel, mayHide = false) }
            }
            if (review.unsure.isNotEmpty()) {
                item(key = "_h_unsure") { Header("Unsure", "Covered by other icons or too close to another Korok. Tick any you know you've collected.") }
                items(review.unsure, key = { "u_" + it.id }) { KorokRow(it, state, viewModel, mayHide = true) }
            }
            item(key = "_h_inputs") { Header("Read", "Each screenshot or recording, and what came of it.") }
            items(state.inputs.withIndex().toList(), key = { "i_${it.index}" }) { InputRow(it.value, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = pickVideo) { Text("+ Recording") }
            TextButton(onClick = pickImages) { Text("+ Screenshots") }
            val n = state.selected.count { it !in state.progress.completed }
            Button(onClick = onApply, enabled = n > 0, modifier = Modifier.weight(1f)) { Text("Apply $n") }
        }
    }
}

/** The whole map (x −5000…5000, z −4000…4000): views read, and each judged Korok. */
@Composable
private fun CoverageMap(review: NotesReview, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val viewFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(10f / 8f).background(MaterialTheme.colorScheme.surfaceContainer)) {
            val k = size.width / 10000f
            fun at(x: Double, z: Double) = Offset(((x + 5000) * k).toFloat(), ((z + 4000) * k).toFloat())
            for (v in review.views) {
                val a = at(v.x0, v.z0)
                val b = at(v.x1, v.z1)
                drawRect(viewFill, topLeft = a, size = Size(b.x - a.x, b.y - a.y))
            }
            drawRect(outline, style = Stroke(width = 1.dp.toPx()))
            for ((id, verdict) in review.verdicts) {
                val p = review.points[id] ?: continue
                val c = at(p.first, p.second)
                when (verdict) {
                    NotesVerdict.ABSENT -> drawCircle(Color(0xFF66BB6A), radius = 3.dp.toPx(), center = c)
                    NotesVerdict.UNSURE -> drawCircle(Color(0xFFFFCA28), radius = 2.5.dp.toPx(), center = c)
                    NotesVerdict.PRESENT -> drawCircle(Color(0xFF90A4AE), radius = 1.5.dp.toPx(), center = c)
                }
            }
        }
        Text(
            "Shaded: views read. Green: collected · amber: unsure · grey: still to collect.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InputRow(input: NotesInput, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(input.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            input.summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (input.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun KorokRow(korok: Korok, state: NotesImportUiState, viewModel: NotesImportViewModel, mayHide: Boolean) {
    val hidden = mayHide && state.isHidden(korok)
    val checked = korok.id in state.selected
    Row(
        Modifier.fillMaxWidth()
            .let { if (hidden) it.clickable { viewModel.reveal(korok.id) } else it.toggleable(checked, role = Role.Checkbox) { on -> viewModel.setSelected(korok.id, on) } }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (hidden) "Undiscovered Korok · tap to show" else "${korok.id} · ${korok.nearestPlace}", style = MaterialTheme.typography.bodyLarge)
            Text(korok.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!hidden) Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Applied(n: Int, file: String, padding: PaddingValues, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Marked ${seeds(n)} collected on $file.", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack) { Text("Back to Korok Seeds") }
    }
}
