package gx.zelda.shrines.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gx.zelda.core.rememberImagesPicker
import gx.zelda.core.ui.Tag
import gx.zelda.shrines.model.ShrineState

/** Shrines → ⋮ → Scan map screenshots or photos: instructions, the photo picker, and the review before Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrineScanScreen(state: ShrineScanUiState?, loadFailed: Boolean, viewModel: ShrineScanViewModel, onBack: () -> Unit) {
    val pick = rememberImagesPicker(viewModel::scan)
    var confirming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scan map screenshots or photos")
                        if (state != null) {
                            Text(
                                state.saveFile.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        bottomBar = {
            if (state?.review != null && state.applied == null && state.scanning == null) {
                ApplyBar(state.changes.size, onAddMore = pick, onApply = { confirming = true })
            }
        },
    ) { padding ->
        when {
            state == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                if (loadFailed) {
                    Text("The map data couldn't be loaded.", color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator()
                }
            }
            state.applied != null -> Applied(state, padding, onBack)
            else -> ScanContent(state, viewModel, padding, onPick = pick)
        }
    }
    if (confirming && state != null) {
        ConfirmApply(
            state,
            onConfirm = {
                confirming = false
                viewModel.apply()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun ScanContent(state: ShrineScanUiState, viewModel: ShrineScanViewModel, padding: PaddingValues, onPick: () -> Unit) {
    val review = state.review
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        if (state.shots.isEmpty() && state.scanning == null) {
            item(key = "_how") { Instructions(onPick) }
            return@LazyColumn
        }
        state.scanning?.let { (done, total) ->
            item(key = "_scanning") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reading image ${(done + 1).coerceAtMost(total)} of $total…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        items(state.shots.withIndex().toList(), key = { "shot_${it.index}" }) { (i, shot) -> ShotCard(i, shot) }
        if (review == null) {
            if (state.scanning == null) {
                item(key = "_retry") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("None of these images could be used.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { viewModel.startOver(); onPick() }) { Text("Choose images") }
                    }
                }
            }
            return@LazyColumn
        }
        item(key = "_counts") { Counts(review) }
        reviewSections(state, review, viewModel)
    }
}

@Composable
private fun Instructions(onPick: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Read which shrines you've found and finished from screenshots of the in-game map, or photos of the " +
                "screen showing it. You'll see what it found before anything changes.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Taking the screenshots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Step(1, "Open the 2D map and zoom all the way out.")
                Step(2, "Take one screenshot of the north half, then pan down and take one of the south half.")
                Step(3, "On the Switch, open the Album, pick the screenshots and use Send to Smartphone. Scan the QR code with this phone and save the images.")
                Step(4, "Choose them here. Use the original screenshots, not crops or edited copies.")
                Text(
                    "Hero's Path mode works, but its trail can hide a few spots. Pins and stamps on a shrine hide it too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Or photograph the screen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Step(1, "With the map zoomed all the way out, take a photo of the TV or the Switch: one of the north half, one of the south.")
                Step(2, "Fill most of the picture with the screen, but keep all of it in the shot. From the couch, use the camera's 2× zoom.")
                Step(3, "Hold steady, keep lamps and windows from reflecting in the screen, then choose the photos here.")
                Text(
                    "Photos leave a few more shrines unsure than screenshots do.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choose screenshots or photos") }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$n.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ShotCard(index: Int, shot: ScannedShot) {
    val rejected = shot.scan as? ShotScan.Rejected
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (rejected != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${if (shot.fromPhoto) "Photo" else "Screenshot"} ${index + 1} · ${shot.name}", style = MaterialTheme.typography.labelLarge)
            when (val scan = shot.scan) {
                is ShotScan.Rejected -> Text(scan.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                is ShotScan.Read -> Text(
                    buildString {
                        append("Lined up with the map using ${scan.registration.anchors} icons · ${shot.millis} ms")
                        if (scan.heroesPath) append(" · Hero's Path trail read around")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Counts(review: ScanReview) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        for (verdict in ScanVerdict.entries) {
            val n = review.counts[verdict] ?: 0
            if (verdict == ScanVerdict.COMPLETED || verdict == ScanVerdict.NOT_DONE) {
                Tag("${verdict.label} $n", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Tag("${verdict.label} $n")
            }
        }
    }
}

private fun LazyListScope.reviewSections(state: ShrineScanUiState, review: ScanReview, viewModel: ShrineScanViewModel) {
    val completed = review.section(ReviewSection.COMPLETED)
    val notDone = review.section(ReviewSection.NOT_DONE)
    val unsure = review.section(ReviewSection.UNSURE)
    val offScreen = review.section(ReviewSection.OFF_SCREEN)

    if (completed.isNotEmpty()) {
        item(key = "h_done") { SectionHeader("Completed on the map", "Ticked to mark completed.") }
        items(completed, key = { it.shrine.id }) { TickRow(it, state, ScanChoice.COMPLETED, viewModel) }
    }
    if (notDone.isNotEmpty()) {
        item(key = "h_found") { SectionHeader("Found, not finished", "Orange icon on the map. Ticked to mark found.") }
        items(notDone, key = { it.shrine.id }) { TickRow(it, state, ScanChoice.FOUND, viewModel) }
    }
    if (unsure.isNotEmpty()) {
        item(key = "h_unsure") { SectionHeader("Check these yourself", "Something covers the spot, so the scan can't tell.") }
        items(unsure, key = { it.shrine.id }) { ChoiceRow(it, state, viewModel) }
    }
    if (offScreen.isNotEmpty()) {
        item(key = "h_off") {
            Collapsible("Not in any screenshot (${offScreen.size})") { open ->
                if (open) {
                    Column { offScreen.forEach { ChoiceRow(it, state, viewModel) } }
                }
            }
        }
    }
    item(key = "_left") { LeftAlone(state, review) }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A suggestion the user can untick: ticked means [choice], unticked means leave it. */
@Composable
private fun TickRow(item: ReviewItem, state: ShrineScanUiState, choice: ScanChoice, viewModel: ShrineScanViewModel) {
    val ticked = state.selections[item.shrine.id] == choice
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = ticked, role = Role.Checkbox) { viewModel.setChoice(item.shrine.id, if (it) choice else ScanChoice.LEAVE) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Checkbox(checked = ticked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${item.shrine.name} Shrine", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${item.shrine.region} · now ${item.current.label.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An unsure shrine: why, and Leave / Found / Done. Hidden behind a tap while spoilers are off. */
@Composable
private fun ChoiceRow(item: ReviewItem, state: ShrineScanUiState, viewModel: ShrineScanViewModel) {
    val hidden = state.isHidden(item)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (hidden) Modifier.clickable(onClickLabel = "Reveal") { viewModel.reveal(item.shrine.id) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hidden) Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(
                    if (hidden) "Undiscovered shrine" else "${item.shrine.name} Shrine",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${item.shrine.region} · ${item.reading.note}" + if (hidden) " · Tap to reveal" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!hidden) {
            val selected = state.selections[item.shrine.id] ?: ScanChoice.LEAVE
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                item.options.forEachIndexed { i, choice ->
                    SegmentedButton(
                        selected = selected == choice,
                        onClick = { viewModel.setChoice(item.shrine.id, choice) },
                        shape = SegmentedButtonDefaults.itemShape(i, item.options.size),
                    ) { Text(choice.label) }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Collapsible(title: String, content: @Composable (open: Boolean) -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Collapse" else "Expand",
            )
        }
        content(open)
    }
}

/** What the review won't touch: empty spots, shrines already up to date, and completed ones it won't downgrade. */
@Composable
private fun LeftAlone(state: ShrineScanUiState, review: ScanReview) {
    val notPresent = review.notPresent
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (notPresent.isNotEmpty()) {
            if (state.prefs.hideUndiscovered) {
                Text(
                    "${shrineCount(notPresent.size)} weren't on the map. They're left as they are.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Collapsible("Not on the map (${notPresent.size})") { open ->
                    Column(Modifier.padding(horizontal = 0.dp)) {
                        Text(
                            "Their spots are clearly empty, so they're undiscovered. They're left as they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (open) {
                            Text(
                                notPresent.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (review.upToDate > 0) {
            Text("${shrineCount(review.upToDate)} already match the map.", style = MaterialTheme.typography.bodyMedium)
        }
        if (review.keptCompleted.isNotEmpty()) {
            Text(
                "Kept as completed, although the map shows them unfinished or missing: " +
                    review.keptCompleted.joinToString(", ") { it.name } + ".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ApplyBar(changes: Int, onAddMore: () -> Unit, onApply: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            OutlinedButton(onClick = onAddMore) { Text("Add more") }
            Button(onClick = onApply, enabled = changes > 0, modifier = Modifier.weight(1f)) {
                Text(if (changes == 0) "Nothing to apply" else "Apply ${changeCount(changes)}")
            }
        }
    }
}

@Composable
private fun ConfirmApply(state: ShrineScanUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val changes = state.changes
    val completed = changes.count { it.value == ShrineState.COMPLETED }
    val found = changes.count { it.value == ShrineState.FOUND }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply to ${state.saveFile.label}?") },
        text = {
            Text(
                listOfNotNull(
                    "${shrineCount(completed)} marked completed".takeIf { completed > 0 },
                    "${shrineCount(found)} marked found".takeIf { found > 0 },
                ).joinToString(" and ") + ". Nothing else changes.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Applied(state: ShrineScanUiState, padding: PaddingValues, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Updated ${shrineCount(state.applied ?: 0)} on ${state.saveFile.label}.", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack) { Text("Back to shrines") }
    }
}

private fun shrineCount(n: Int) = if (n == 1) "1 shrine" else "$n shrines"

private fun changeCount(n: Int) = if (n == 1) "1 change" else "$n changes"
